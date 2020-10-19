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

case class FileStoreInitiateRequest(
  id: Option[String] = None,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String] = None,
  minimumFileSize: Option[Long] = None,
  maximumFileSize: Option[Long] = None,
  expectedContentType: Option[String] = None,
  publishable: Boolean = false
)

object FileStoreInitiateRequest {
  implicit val format: OFormat[FileStoreInitiateRequest] = Json.format[FileStoreInitiateRequest]
}