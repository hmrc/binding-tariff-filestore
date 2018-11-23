package uk.gov.hmrc.bindingtarifffilestore.model.upscan

import play.api.libs.json.{Format, Json}

case class UploadSettings
(
  callbackUrl: String,
  minimumFileSize: Option[Int] = None,
  maximumFileSize: Option[Int] = None
)

object UploadSettings {
  implicit val format: Format[UploadSettings] = Json.format
}
