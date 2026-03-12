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
import software.amazon.awssdk.services.s3.{S3Configuration => AwsS3Configuration}

import java.time.Duration
import java.net.URI

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
      .credentialsProvider(LocalDevelopmentCredentialsProvider())
      .serviceConfiguration(
        AwsS3Configuration.builder().pathStyleAccessEnabled(true).build()
      )

    s3Config.endpoint match {
      case Some(endpoint) => builder.endpointOverride(URI.create(endpoint))
      case _              => ()
    }
    builder.build()
  }

  private lazy val presigner: S3Presigner = {
    val builder = S3Presigner
      .builder()
      .region(Region.of(s3Config.region))
      .credentialsProvider(LocalDevelopmentCredentialsProvider())
      .serviceConfiguration(
        AwsS3Configuration.builder().pathStyleAccessEnabled(true).build()
      )

    s3Config.endpoint match {
      case Some(endpoint) => builder.endpointOverride(URI.create(endpoint))
      case _              => ()
    }
    builder.build()
  }

  def getAll: Seq[String] =
    s3client
      .listObjects(
        ListObjectsRequest.builder().bucket(s3Config.bucket).build()
      )
      .contents()
      .asScala
      .toSeq
      .map(_.key())

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val request = PutObjectRequest
      .builder()
      .bucket(s3Config.bucket)
      .key(fileMetaData.id)
      .contentType(fileMetaData.mimeType.get)
      .contentLength(contentLengthOf(url))
      .build()

    Try(
      s3client.putObject(
        request,
        RequestBody.fromInputStream(new BufferedInputStream(url.openStream()), contentLengthOf(url))
      )
    ) match {
      case Success(_)            =>
        fileMetaData.copy(url = Some(s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"))
      case Failure(e: Throwable) =>
        log.error("Failed to upload to the S3 bucket.", e)
        throw e
    }
  }

  def delete(id: String): Unit =
    s3client.deleteObject(
      DeleteObjectRequest.builder().bucket(s3Config.bucket).key(id).build()
    )

  def deleteAll(): Unit = {
    val identifiers = getAll.map(key => ObjectIdentifier.builder().key(key).build()).asJava

    if (!identifiers.isEmpty) {
      val request = DeleteObjectsRequest
        .builder()
        .bucket(s3Config.bucket)
        .delete(Delete.builder().objects(identifiers).quiet(false).build())
        .build()
      s3client.deleteObjects(request)
    } else {
      log.info(s"No files to remove from S3")
    }
  }

  def sign(fileMetaData: FileMetadata): FileMetadata =
    if (fileMetaData.url.isDefined) {
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
    } else {
      fileMetaData
    }

  private def contentLengthOf(url: URL): Long =
    url.openConnection.getContentLengthLong

}

class LocalDevelopmentCredentialsProvider extends AwsCredentialsProvider {
  override def resolveCredentials(): AwsCredentials =
    Try(DefaultCredentialsProvider.builder().build().resolveCredentials())
      .getOrElse(AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key"))
}
