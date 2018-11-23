package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json.{Format, Json}

case class UploadSettings(
                           callbackUrl: String,
                           minimumFileSize: Option[Int],
                           maximumFileSize: Option[Int],
                           expectedContentType: Option[String])

object UploadSettings {
  implicit val settingsFormat: Format[UploadSettings] = Json.format
}
