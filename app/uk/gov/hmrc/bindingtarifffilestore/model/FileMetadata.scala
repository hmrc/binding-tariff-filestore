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

package uk.gov.hmrc.bindingtarifffilestore.model

import java.time.{Instant, LocalDateTime, ZoneOffset}

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus._

case class FileMetadata
(
  id: String,
  fileName: String,
  mimeType: String,
  url: Option[String] = None,
  scanStatus: Option[ScanStatus] = None,
  publishable: Boolean = false,
  published: Boolean = false,
  lastUpdated: Instant = Instant.now()
) {
  private lazy val date = "X-Amz-Date=(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})".r.unanchored
  private lazy val expires = "X-Amz-Expires=(\\d+)".r.unanchored

  //date time groups -> based on date regex above
  private val yearGroup = 1
  private val monthGroup = 2
  private val dayGroup = 3
  private val hourGroup = 4
  private val minuteGroup = 5
  private val secondGroup = 6

  def isLive: Boolean = {
    this.url.forall { url =>
      (date.findFirstMatchIn(url), expires.findFirstMatchIn(url)) match {
        case (Some(dateMatch), Some(expiresMatch)) =>
          LocalDateTime.of(
            dateMatch.group(yearGroup).toInt,
            dateMatch.group(monthGroup).toInt,
            dateMatch.group(dayGroup).toInt,
            dateMatch.group(hourGroup).toInt,
            dateMatch.group(minuteGroup).toInt,
            dateMatch.group(secondGroup).toInt
          )
            .plusSeconds(expiresMatch.group(1).toLong)
            .toInstant(ZoneOffset.UTC)
            .isAfter(Instant.now())
        case _ => true
      }
    }
  }
}

object FileMetadataREST {
  val writes: OWrites[FileMetadata] = new OWrites[FileMetadata] {
    override def writes(o: FileMetadata): JsObject = {
      JsObject(
        Map[String, JsValue](
          "id" -> JsString(o.id),
          "fileName" -> JsString(o.fileName),
          "mimeType" -> JsString(o.mimeType),
          "publishable" -> JsBoolean(o.publishable),
          "published" -> JsBoolean(o.published),
          "lastUpdated" -> JsString(o.lastUpdated.toString)
        )
          ++ o.scanStatus.map("scanStatus" -> Json.toJson(_))
          ++ o.url.filter(_ => o.scanStatus.contains(READY)).map("url" -> JsString(_))
      )
    }
  }
  implicit val format: OFormat[FileMetadata] = OFormat(Json.reads[FileMetadata], writes)
}

object FileMetadataMongo {
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

  private val underlying = Json.format[FileMetadata]
  implicit val format: OFormat[FileMetadata] = OFormat(
    r = underlying,
    w = OWrites(fm => underlying.writes(fm).as[JsObject])
  )
}
