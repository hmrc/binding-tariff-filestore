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

import java.util

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.google.inject.Inject
import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.TemporaryAttachment

import scala.collection.JavaConverters

@Singleton
class AmazonS3Connector @Inject()(config: AppConfig) {

  private val bucket = config.s3Configuration.bucket

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

  def getAll: Seq[TemporaryAttachment] = {
    sequenceOf(s3client
      .listObjects(bucket)
      .getObjectSummaries)
      .map(obj => TemporaryAttachment(fileName = obj.getKey, url = "", mimeType = ""))
  }


  private def sequenceOf[T](list: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(list.iterator).asScala.toSeq
  }

}
