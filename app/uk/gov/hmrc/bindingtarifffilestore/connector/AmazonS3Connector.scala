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

import java.io.BufferedInputStream
import java.net.URL
import com.google.inject.Inject

import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.{Delete, DeleteObjectsRequest, ObjectIdentifier}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.services.s3.S3Configuration as AwsS3Configuration

import java.time.Duration
import java.net.URI
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import scala.util.Using
import play.api.inject.ApplicationLifecycle

import java.util
import scala.concurrent.Future

@Singleton
class AmazonS3Connector @Inject() (config: AppConfig, lifecycle: ApplicationLifecycle) extends Logging {

  private lazy val s3Config = config.s3Configuration

  protected def openStream(url: URL): BufferedInputStream =
    new BufferedInputStream(url.openStream())

  private val s3client: S3Client =
    Try {
      log.info(s"Initializing S3 client for bucket: ${s3Config.bucket}, region: ${s3Config.region}")
      val builder = S3Client
        .builder()
        .region(Region.of(s3Config.region))
        .credentialsProvider(new LocalDevelopmentCredentialsProvider())
        .serviceConfiguration(
          AwsS3Configuration.builder().pathStyleAccessEnabled(true).build()
        )

      s3Config.endpoint match {
        case Some(endpoint) =>
          log.info(s"Using S3 endpoint override: $endpoint")
          builder.endpointOverride(URI.create(endpoint))
        case _              => ()
      }
      builder.build()
    } match {
      case Success(client) => client
      case Failure(e)      =>
        log.error(s"Failed to initialize S3 client for bucket ${s3Config.bucket}", e)
        throw e
    }

  private val presigner: S3Presigner = {
    val builder = S3Presigner
      .builder()
      .region(Region.of(s3Config.region))
      .credentialsProvider(new LocalDevelopmentCredentialsProvider())
      .serviceConfiguration(
        AwsS3Configuration.builder().pathStyleAccessEnabled(true).build()
      )

    s3Config.endpoint match {
      case Some(endpoint) => builder.endpointOverride(URI.create(endpoint))
      case _              => ()
    }
    builder.build()
  }

  lifecycle.addStopHook { () =>
    Future.successful {
      s3client.close()
      presigner.close()
      ()
    }
  }

  def getAll: Seq[String] =
    Try {
      s3client
        .listObjects(
          ListObjectsRequest.builder().bucket(s3Config.bucket).build()
        )
        .contents()
        .asScala
        .toSeq
        .map(_.key())
    } match {
      case Success(keys) =>
        log.debug(s"Listed ${keys.size} objects from bucket: ${s3Config.bucket}")
        keys
      case Failure(e)    =>
        log.error(s"Failed to list objects from bucket: ${s3Config.bucket}", e)
        throw e
    }

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val contentLength = contentLengthOf(url)

    val request = PutObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(fileMetaData.id)
      .contentType(fileMetaData.mimeType.get)
      .contentLength(contentLength)
      .build()

    Try(
      Using.resource(openStream(url)) { stream =>
        s3client.putObject(request, RequestBody.fromInputStream(stream, contentLength))
      }
    ) match {
      case Success(_)            =>
        fileMetaData.copy(url = Some(s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"))
      case Failure(e: Throwable) =>
        log.error("Failed to upload to the S3 bucket.", e)
        throw e
    }
  }

  private def contentLengthOf(url: URL): Long =
    url.openConnection.getContentLengthLong

  def delete(id: String): Unit =
    Try {
      s3client.deleteObject(
        DeleteObjectRequest.builder().bucket(s3Config.bucket).key(id).build()
      )
      log.info(s"Successfully deleted object: $id from bucket: ${s3Config.bucket}")
    } match {
      case Success(_) => ()
      case Failure(e) =>
        log.error(s"Failed to delete object: $id from bucket: ${s3Config.bucket}", e)
        throw e
    }

  def deleteAll(): Unit =
    Try {
      val identifiers: util.List[ObjectIdentifier] =
        getAll.map(key => ObjectIdentifier.builder().key(key).build()).asJava

      if (!identifiers.isEmpty) {
        log.info(s"Removing [${identifiers.size()}] files from S3 bucket: ${s3Config.bucket}")
        val request = DeleteObjectsRequest
          .builder()
          .bucket(s3Config.bucket)
          .delete(Delete.builder().objects(identifiers).quiet(false).build())
          .build()
        s3client.deleteObjects(request)
        log.info(s"Successfully deleted all ${identifiers.size()} files")
      } else {
        log.info(s"No files to remove from S3")
      }
    } match {
      case Success(_) => ()
      case Failure(e) =>
        log.error(s"Failed to delete all objects from bucket: ${s3Config.bucket}", e)
        throw e
    }

  def sign(fileMetaData: FileMetadata): FileMetadata =
    if (fileMetaData.url.isDefined) {
      Try {
        val presignedUrl = presigner
          .presignGetObject(
            GetObjectPresignRequest
              .builder()
              .signatureDuration(Duration.ofHours(1))
              .getObjectRequest(
                GetObjectRequest.builder().bucket(s3Config.bucket).key(fileMetaData.id).build()
              )
              .build()
          )
          .url()
        fileMetaData.copy(url = Some(presignedUrl.toString))
      } match {
        case Success(metadata) => metadata
        case Failure(e)        =>
          log.error(s"Failed to generate presigned URL for file: ${fileMetaData.id}", e)
          throw e
      }
    } else {
      fileMetaData
    }

}

class LocalDevelopmentCredentialsProvider extends AwsCredentialsProvider with Logging {
  private val delegate = DefaultCredentialsProvider.builder().build()

  override def resolveCredentials(): AwsCredentials =
    Try(delegate.resolveCredentials()) match {
      case Success(creds) => creds
      case Failure(e)     =>
        log.warn(
          "Failed to resolve AWS credentials from default provider chain, falling back to dummy credentials for local development",
          e
        )
        AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")
    }
}
