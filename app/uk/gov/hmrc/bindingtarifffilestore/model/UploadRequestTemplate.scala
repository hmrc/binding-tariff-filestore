package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json.{Json, OFormat}

case class UploadRequestTemplate(href: String, fields: Map[String, String])

object UploadFormTemplate {
  implicit val format: OFormat[UploadRequestTemplate] = Json.format
}
