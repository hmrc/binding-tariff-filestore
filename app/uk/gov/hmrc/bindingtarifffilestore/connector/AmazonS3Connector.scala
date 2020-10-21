/*
 * Copyright 2020 HM Revenue & Customs
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
import java.util

import com.amazonaws.HttpMethod
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.Logger
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata

import scala.collection.{JavaConversions, JavaConverters}
import scala.util.{Failure, Success, Try}

@Singleton
class AmazonS3Connector @Inject()(config: AppConfig) {

  private lazy val s3Config = config.s3Configuration

  private lazy val credentials = new BasicAWSCredentials(s3Config.key, s3Config.secret)
  private lazy val provider = new AWSStaticCredentialsProvider(credentials)

  private lazy val s3client: AmazonS3 = {
    Logger.info(s"${s3Config.bucket}:${s3Config.region}:${s3Config.key}:${s3Config.secret.substring(0,3)}")
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(provider)
      .withPathStyleAccessEnabled(true)

    s3Config.endpoint match {
      case Some(endpoint) => builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, s3Config.region))
      case _ => builder.withRegion(s3Config.region)
    }

    builder.build()
  }

  def getAll: Seq[String] = {
    sequenceOf(
      s3client.listObjects(s3Config.bucket).getObjectSummaries
    ).map(_.getKey)
  }

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val metadata = new ObjectMetadata
    metadata.setContentType(fileMetaData.mimeType)
    metadata.setContentLength(contentLengthOf(url))

    val request = new PutObjectRequest(
      s3Config.bucket, fileMetaData.id, new BufferedInputStream(url.openStream()), metadata
    ).withCannedAcl(CannedAccessControlList.Private)

    Try(s3client.putObject(request)) match {
      case Success(_) =>
        fileMetaData.copy(url = Some(s"${s3Config.baseUrl}/${s3Config.bucket}/${fileMetaData.id}"))
      case Failure(e: Throwable) =>
        Logger.error("Failed to upload to the S3 bucket.", e)
        throw e
    }
  }

  def delete(id: String): Unit = {
    s3client.deleteObject(s3Config.bucket, id)
  }

  def deleteAll(): Unit = {
    val keys: Seq[KeyVersion] = getAll.map(new KeyVersion(_))
    if(keys.nonEmpty) {
      Logger.info(s"Removing [${keys.length}] files from S3")
      val request = new DeleteObjectsRequest(s3Config.bucket)
        .withKeys(JavaConversions.seqAsJavaList(keys))
        .withQuiet(false)
      s3client.deleteObjects(request)
    } else {
      Logger.info(s"No files to remove from S3")
    }
  }

  def sign(fileMetaData: FileMetadata): FileMetadata = {
    if (fileMetaData.url.isDefined) {
      val authenticatedURLRequest = new GeneratePresignedUrlRequest(config.s3Configuration.bucket, fileMetaData.id)
        .withMethod(HttpMethod.GET)
      val authenticatedURL: URL = s3client.generatePresignedUrl(authenticatedURLRequest)
      fileMetaData.copy(url = Some(authenticatedURL.toString))
    } else {
      fileMetaData
    }
  }

  private def contentLengthOf(url: URL): Long = {
    url.openConnection.getContentLengthLong
  }

  private def sequenceOf[T](list: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(list.iterator).asScala.toSeq
  }

}
