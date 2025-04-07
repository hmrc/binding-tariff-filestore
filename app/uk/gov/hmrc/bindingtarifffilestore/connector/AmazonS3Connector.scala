/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bindingtarifffilestore.connector

import java.net.{URI, URL}
import java.util
import com.google.inject.Inject

import java.nio.file.Path
import play.twirl.api.TwirlHelperImports.twirlJavaCollectionToScala
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentials, AwsCredentialsProvider, DefaultCredentialsProvider}
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region

import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ChecksumAlgorithm, Delete, DeleteObjectRequest, DeleteObjectsRequest, GetObjectRequest, ListObjectsV2Request, ObjectCannedACL, ObjectIdentifier, PutObjectRequest, S3Exception}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

import java.io.UncheckedIOException
import java.time.Duration
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class AmazonS3Connector @Inject() (config: AppConfig) extends Logging {

  private lazy val s3Config = config.s3Configuration

  private lazy val s3client: S3Client = {
    log.info(s"${s3Config.bucket}:${s3Config.region}")
    val builder = S3Client
      .builder()
      .region(Region.of(s3Config.region))
      .forcePathStyle(true)
      .credentialsProvider(new LocalDevelopmentS3CredentialsProvider)

    s3Config.endpoint match {
      case Some(endpoint) => builder.endpointOverride(new URI(endpoint)).build()
      case _              => builder.build()
    }
  }

  private lazy val preSigner: S3Presigner = S3Presigner
    .builder()
    .region(Region.of(s3Config.region))
    .credentialsProvider(new LocalDevelopmentS3CredentialsProvider)
    .build()

  def getAll: Seq[String] = {
    val request = ListObjectsV2Request.builder().bucket(s3Config.bucket).build()

    sequenceOf(
      s3client.listObjectsV2(request).contents()
    ).map(_.key())
  }

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val metadata = new util.HashMap[String, String]()
    metadata.put("content-type", fileMetaData.mimeType.getOrElse(""))
    metadata.put("content-length", contentLengthOf(url).toString)

    val request = PutObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(fileMetaData.id)
      .metadata(metadata)
      .checksumAlgorithm(ChecksumAlgorithm.CRC32)
      .acl(ObjectCannedACL.PRIVATE)
      .build()

    val fileUrl = s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"

    Try(s3client.putObject(request, Path.of(url.toURI))) match {
      case Success(_)                       =>
        fileMetaData.copy(url = Some(fileUrl))
      case Failure(e: UncheckedIOException) =>
        throw S3Exception
          .builder()
          .message("Failed to read the file: " + e.getMessage)
          .cause(e)
          .awsErrorDetails(
            AwsErrorDetails
              .builder()
              .errorCode("ClientSideException:FailedToReadFile")
              .errorMessage(e.getMessage)
              .build()
          )
          .build()
      case Failure(e: S3Exception)          =>
        log.error(s"Failed to put object: {${e.getMessage}", e)
        throw e
      case Failure(e: Throwable)            =>
        log.error("Failed to upload to the S3 bucket.", e)
        throw e
    }
  }

  def delete(id: String): Unit = {
    val request = DeleteObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(id)
      .build()

    s3client.deleteObject(request)
  }

  def deleteAll(): Unit = {
    val keys = getAll.map(ObjectIdentifier.builder().key(_).build()).toList.asJava
    if (keys.nonEmpty) {
      log.info(s"Removing [${keys.size()}] files from S3")
      log.info(s"bucket is: ${s3Config.bucket}")
      val del = Delete
        .builder()
        .objects(keys)
        .build()

      val request = DeleteObjectsRequest
        .builder()
        .bucket(s3Config.bucket)
        .delete(del)
        .build()

      s3client.deleteObjects(request)
    } else {
      log.info(s"No files to remove from S3")
    }
  }

  def sign(fileMetaData: FileMetadata): FileMetadata =
    if (fileMetaData.url.isDefined) {
      val objectRequest  = GetObjectRequest.builder().bucket(s3Config.bucket).key(fileMetaData.id).build()
      val preSignRequest = GetObjectPresignRequest
        .builder()
        .signatureDuration(Duration.ofMinutes(15)) //v1 default value used to be 15 minutes
        .getObjectRequest(objectRequest)
        .build()

      val preSignedRequestUrl = preSigner.presignGetObject(preSignRequest).url().toString

      preSigner.close()

      fileMetaData.copy(url = Option(preSignedRequestUrl))
    } else {
      fileMetaData
    }

  private def contentLengthOf(url: URL): Long =
    url.openConnection.getContentLengthLong

  private def sequenceOf[T](list: util.List[T]): Seq[T] =
    list.iterator.asScala.toSeq
}

class LocalDevelopmentS3CredentialsProvider extends AwsCredentialsProvider with Logging {

  override def resolveCredentials(): AwsCredentials =
    Try {
      DefaultCredentialsProvider.builder().build().resolveCredentials()
    }.recover { case _: SdkException =>
      log.warn(s"Failed to load credentials, using dummy ones now")

      AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")
    }.get
}
