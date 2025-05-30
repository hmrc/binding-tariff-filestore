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

package uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2

import play.api.libs.json.{Json, OFormat}

case class FileStoreInitiateResponse(id: String, upscanReference: String, uploadRequest: UpscanFormTemplate)

object FileStoreInitiateResponse {

  implicit val format: OFormat[FileStoreInitiateResponse] = Json.format[FileStoreInitiateResponse]

  def fromUpscanResponse(id: String, response: UpscanInitiateResponse): FileStoreInitiateResponse =
    FileStoreInitiateResponse(
      id = id,
      upscanReference = response.reference,
      uploadRequest = response.uploadRequest
    )
}
