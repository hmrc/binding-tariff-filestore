/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config._

@Singleton
class AppConfig @Inject() (
  config: Configuration,
  servicesConfig: ServicesConfig
) {

  lazy val authorization: String = config.get[String]("auth.api-token")

  lazy val s3Configuration: S3Configuration = S3Configuration(
    config.get[String]("s3.region"),
    config.get[String]("s3.bucket"),
    Option(config.get[String]("s3.endpoint")).filter(_.nonEmpty)
  )

  lazy val upscanInitiateUrl: String                              = servicesConfig.baseUrl("upscan-initiate")
  lazy val fileStoreSizeConfiguration: FileStoreSizeConfiguration = FileStoreSizeConfiguration(
    maxFileSize = config.get[Int]("upscan.maxFileSize"),
    minFileSize = config.get[Int]("upscan.minFileSize")
  )

  lazy val filestoreUrl: String  = config.get[String]("filestore.url")
  lazy val filestoreSSL: Boolean = config.get[Boolean]("filestore.ssl")

  lazy val isTestMode: Boolean = config.getOptional[Boolean]("testMode").getOrElse(false)
}

case class S3Configuration(
  region: String,
  bucket: String,
  endpoint: Option[String]
) {

  def baseUrl: String = endpoint.getOrElse(s"https://s3-$region.amazonaws.com")
}

case class FileStoreSizeConfiguration(
  minFileSize: Int,
  maxFileSize: Int
)
