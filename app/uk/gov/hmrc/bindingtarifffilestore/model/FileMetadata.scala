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

package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.{util => ju}

case class FileMetadata(
  id: String,
  fileName: Option[String],
  mimeType: Option[String],
  url: Option[String] = None,
  scanStatus: Option[ScanStatus] = None,
  publishable: Boolean = false,
  published: Boolean = false,
  lastUpdated: Instant = Instant.now()
) {
  private lazy val date    = "X-Amz-Date=(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})".r.unanchored
  private lazy val expires = "X-Amz-Expires=(\\d+)".r.unanchored

  //date time groups -> based on date regex above
  private val yearGroup   = 1
  private val monthGroup  = 2
  private val dayGroup    = 3
  private val hourGroup   = 4
  private val minuteGroup = 5
  private val secondGroup = 6

  def isLive: Boolean =
    this.url.forall { url =>
      (date.findFirstMatchIn(url), expires.findFirstMatchIn(url)) match {
        case (Some(dateMatch), Some(expiresMatch)) =>
          LocalDateTime
            .of(
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
        case _                                     => true
      }
    }

  def withScanResult(scanResult: ScanResult): FileMetadata = scanResult match {
    case SuccessfulScanResult(_, _, downloadUrl, uploadDetails) =>
      copy(
        fileName = Some(uploadDetails.fileName),
        mimeType = Some(uploadDetails.fileMimeType),
        url = Some(downloadUrl),
        scanStatus = Some(ScanStatus.READY)
      )
    case FailedScanResult(_, _, _)                              =>
      copy(scanStatus = Some(ScanStatus.FAILED))
  }

}

object FileMetadata {
  def fromUploadRequest(uploadRequest: UploadRequest): FileMetadata =
    FileMetadata(
      id = uploadRequest.id.getOrElse(ju.UUID.randomUUID().toString),
      fileName = Some(uploadRequest.fileName),
      mimeType = Some(uploadRequest.mimeType),
      publishable = uploadRequest.publishable
    )

  def fromInitiateRequestV2(id: String, request: v2.FileStoreInitiateRequest): FileMetadata =
    FileMetadata(
      id = id,
      fileName = None,
      mimeType = None,
      publishable = request.publishable
    )

  implicit val format: OFormat[FileMetadata] = Json.format[FileMetadata]
}

object FileMetadataREST {
  val writes: OWrites[FileMetadata]          = (o: FileMetadata) =>
    JsObject(
      Map[String, JsValue](
        "id"                         -> JsString(o.id),
        "publishable"                -> JsBoolean(o.publishable),
        "published"                  -> JsBoolean(o.published),
        "lastUpdated"                -> JsString(o.lastUpdated.toString)
      )
        ++ o.fileName.map("fileName" -> Json.toJson(_))
        ++ o.mimeType.map("mimeType" -> Json.toJson(_))
        ++ o.scanStatus.map("scanStatus" -> Json.toJson(_))
        ++ o.url.filter(_ => o.scanStatus.contains(READY)).map("url" -> JsString(_))
    )
  implicit val format: OFormat[FileMetadata] = OFormat(Json.reads[FileMetadata], writes)
}

object FileMetadataMongo {
  implicit val instantFormat: OFormat[Instant] = new OFormat[Instant] {
    override def writes(instant: Instant): JsObject      =
      Json.obj("$date" -> instant.toEpochMilli)

    override def reads(json: JsValue): JsResult[Instant] =
      json match {
        case JsObject(map) if map.contains("$date") =>
          map("$date") match {
            case JsNumber(v)            => JsSuccess(Instant.ofEpochMilli(v.toLong))
            case JsObject(stringObject) =>
              if (stringObject.contains("$numberLong")) {
                JsSuccess(Instant.ofEpochMilli(BigDecimal(stringObject("$numberLong").as[JsString].value).toLong))
              } else {
                JsError("Unexpected Instant Format")
              }
            case _                      => JsError("Unexpected Instant Format")
          }
        case _                                      => JsError("Unexpected Instant Format")
      }
  }

  private val underlying                     = Json.using[Json.WithDefaultValues].format[FileMetadata]
  implicit val format: OFormat[FileMetadata] = OFormat(
    r = underlying,
    w = OWrites(fm => underlying.writes(fm).as[JsObject])
  )
}
