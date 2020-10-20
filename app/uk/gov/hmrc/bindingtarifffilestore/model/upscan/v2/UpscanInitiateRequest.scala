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

package uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2

import play.api.libs.json.{ OFormat, Json }
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig

case class UpscanInitiateRequest(
  callbackUrl: String,
  successRedirect: Option[String],
  errorRedirect: Option[String],
  minimumFileSize: Option[Long],
  maximumFileSize: Option[Long],
  expectedContentType: Option[String]
)

object UpscanInitiateRequest {
  implicit val format: OFormat[UpscanInitiateRequest] = Json.format[UpscanInitiateRequest]

  def fromFileStoreRequest(callbackUrl: String, appConfig: AppConfig, request: FileStoreInitiateRequest) =
    UpscanInitiateRequest(
      callbackUrl = callbackUrl,
      successRedirect = request.successRedirect,
      errorRedirect = request.errorRedirect,
      minimumFileSize = Some(appConfig.fileStoreSizeConfiguration.minFileSize),
      maximumFileSize = Some(appConfig.fileStoreSizeConfiguration.maxFileSize),
      expectedContentType = request.expectedContentType
    )
}