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

package uk.gov.hmrc.bindingtarifffilestore.model

import java.time.Instant
import java.util.UUID

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus._

case class FileMetadata
(
  id: String = UUID.randomUUID().toString,
  fileName: String,
  mimeType: String,
  url: Option[String] = None,
  scanStatus: Option[ScanStatus] = None,
  lastUpdated: Instant = Instant.now()
)

object FileMetadata {

  implicit val instantFormat: OFormat[Instant] = new OFormat[Instant] {
    override def writes(instant: Instant): JsObject = {
      Json.obj("$date" -> instant.toEpochMilli)
    }

    override def reads(json: JsValue): JsResult[Instant] = {
      json match {
        case JsObject(map) if map.contains("$date") =>
          map("$date") match {
            case JsNumber(v) => JsSuccess(Instant.ofEpochMilli(v.toLong))
            case _ => JsError("Unexpected Instant Format")
          }
        case _ => JsError("Unexpected Instant Format")
      }
    }
  }

  implicit val format: OFormat[FileMetadata] = Json.format[FileMetadata]
}
