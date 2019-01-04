/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject._
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  lazy val s3Configuration = S3Configuration(
    getString("s3.accessKeyId"),
    base64Decode(getString("s3.secretKeyId")),
    getString("s3.region"),
    getString("s3.bucket"),
    Option(getString("s3.endpoint")).filter(_.nonEmpty)
  )

  lazy val upscanInitiateUrl: String = baseUrl("upscan-initiate")

  lazy val filestoreUrl: String = getString("filestore.url")
  lazy val filestoreSSL: Boolean = getBoolean("filestore.ssl")

  private def base64Decode(text: String) = new String(java.util.Base64.getDecoder.decode(text))

  lazy val mongoTTL: Int = getInt("mongodb.timeToLiveInSeconds")
}

case class S3Configuration
(
  key: String,
  secret: String,
  region: String,
  bucket: String,
  endpoint: Option[String]
) {

  def baseUrl: String = endpoint.getOrElse(s"https://s3-$region.amazonaws.com")
}

