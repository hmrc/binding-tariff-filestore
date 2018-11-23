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
import uk.gov.hmrc.bindingtarifffilestore.model.Attachment

import scala.collection.JavaConverters

@Singleton
class AmazonS3Connector @Inject()(appConfig: AppConfig) {

  private val credentials = new BasicAWSCredentials(appConfig.awsKey, appConfig.awsSecret)

  private val bucket = appConfig.awsBucket

  def getAll: Seq[Attachment] = {
    sequenceOf(client().listObjects(bucket).getObjectSummaries).map(obj => Attachment(obj.getKey))
  }

  private def client(): AmazonS3 = {
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withPathStyleAccessEnabled(true)

    appConfig.awsEndpoint match {
      case Some(endpoint) =>
        builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, appConfig.awsRegion))
      case _ =>
        builder.withRegion(appConfig.awsRegion)
    }
    builder.build()
  }

  private def sequenceOf[T](list: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(list.iterator).asScala.toSeq
  }

}
