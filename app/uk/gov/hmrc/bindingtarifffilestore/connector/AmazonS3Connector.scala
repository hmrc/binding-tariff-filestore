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

import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}

import java.io.BufferedInputStream
import java.net.URL
import java.util
import com.amazonaws.{AmazonClientException, HttpMethod}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.google.inject.Inject

import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class AmazonS3Connector @Inject() (config: AppConfig) extends Logging {

  private lazy val s3Config = config.s3Configuration

  private lazy val s3client: AmazonS3 = {
    log.info(s"${s3Config.bucket}:${s3Config.region}")
    val builder = AmazonS3ClientBuilder
      .standard()
      .withPathStyleAccessEnabled(true)
      .withCredentials(new LocalDevelopmentS3CredentialsProviderChain())

    s3Config.endpoint match {
      case Some(endpoint) => builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, s3Config.region))
      case _              => builder.withRegion(s3Config.region)
    }

    builder.build()
  }

  def getAll: Seq[String] =
    sequenceOf(
      s3client.listObjects(s3Config.bucket).getObjectSummaries
    ).map(_.getKey)

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val metadata = new ObjectMetadata
    // This .get is scary but our file must have received a positive scan
    // result and received metadata from Upscan if it is being published
    metadata.setContentType(fileMetaData.mimeType.get)
    metadata.setContentLength(contentLengthOf(url))

    val request = new PutObjectRequest(
      s3Config.bucket,
      fileMetaData.id,
      new BufferedInputStream(url.openStream()),
      metadata
    ).withCannedAcl(CannedAccessControlList.Private)

    Try(s3client.putObject(request)) match {
      case Success(_)            =>
        fileMetaData.copy(url = Some(s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"))
      case Failure(e: Throwable) =>
        log.error("Failed to upload to the S3 bucket.", e)
        throw e
    }
  }

  def delete(id: String): Unit =
    s3client.deleteObject(s3Config.bucket, id)

  def deleteAll(): Unit = {
    val keys: Seq[KeyVersion] = getAll.map(new KeyVersion(_))
    if (keys.nonEmpty) {
      log.info(s"Removing [${keys.length}] files from S3")
      log.info(s"bucket is: ${s3Config.bucket}")
      val request = new DeleteObjectsRequest(s3Config.bucket)
        .withKeys(keys.toList.asJava)
        .withQuiet(false)
      s3client.deleteObjects(request)
    } else {
      log.info(s"No files to remove from S3")
    }
  }

  def sign(fileMetaData: FileMetadata): FileMetadata =
    if (fileMetaData.url.isDefined) {
      val authenticatedURLRequest = new GeneratePresignedUrlRequest(config.s3Configuration.bucket, fileMetaData.id)
        .withMethod(HttpMethod.GET)
      val authenticatedURL: URL   = s3client.generatePresignedUrl(authenticatedURLRequest)
      fileMetaData.copy(url = Some(authenticatedURL.toString))
    } else {
      fileMetaData
    }

  private def contentLengthOf(url: URL): Long =
    url.openConnection.getContentLengthLong

  private def sequenceOf[T](list: util.List[T]): Seq[T] =
    list.iterator.asScala.toSeq

}

class LocalDevelopmentS3CredentialsProviderChain() extends DefaultAWSCredentialsProviderChain {

  override def getCredentials(): AWSCredentials =
    Try {
      super.getCredentials()
    }.recover { case _: AmazonClientException =>
      new BasicAWSCredentials("dummy-access-key", "dummy-secret-key")
    }.get
}
