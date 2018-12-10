/*
 * Copyright 2018 HM Revenue & Customs
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
import com.amazonaws.services.s3.model.{CannedAccessControlList, GeneratePresignedUrlRequest, ObjectMetadata, PutObjectRequest}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.google.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata

import scala.collection.JavaConverters
import scala.util.{Failure, Success, Try}

@Singleton
class AmazonS3Connector @Inject()(config: AppConfig) {

  private lazy val bucket = config.s3Configuration.bucket

  private lazy val credentials = new BasicAWSCredentials(config.s3Configuration.key, config.s3Configuration.secret)
  private lazy val provider = new AWSStaticCredentialsProvider(credentials)

  private lazy val s3client: AmazonS3 = {
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(provider)
      .withPathStyleAccessEnabled(true)

    config.s3Configuration.endpoint match {
      case Some(endpoint) => builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, config.s3Configuration.region))
      case _ => builder.withRegion(config.s3Configuration.region)
    }

    builder.build()
  }

  def getAll: Seq[FileMetadata] = {
    sequenceOf(s3client
      .listObjects(bucket)
      .getObjectSummaries)
      .map(obj => FileMetadata(fileName = obj.getKey, mimeType = ""))
  }

  def upload(fileMetaData: FileMetadata): FileMetadata = {
    val url: URL = new URL(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL")))

    val metadata = new ObjectMetadata
    metadata.setContentType(fileMetaData.mimeType)

    val request = new PutObjectRequest(bucket, fileMetaData.id, new BufferedInputStream(url.openStream()), metadata)
    request.withCannedAcl(CannedAccessControlList.Private)

    Try ( s3client.putObject(request) ) match {
      case Success(_) =>
        val authenticatedURLRequest = new GeneratePresignedUrlRequest(config.s3Configuration.bucket, fileMetaData.id)
          .withMethod(HttpMethod.GET)
        val authenticatedURL: URL = s3client.generatePresignedUrl(authenticatedURLRequest)
        fileMetaData.copy(url = Some(authenticatedURL.toString))
      case Failure(e: Throwable) => throw e
    }

  }

  private def sequenceOf[T](list: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(list.iterator).asScala.toSeq
  }

}
