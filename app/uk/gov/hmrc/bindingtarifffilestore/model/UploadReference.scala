package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json._

case class UploadReference(value: String) extends AnyVal

object UploadReference {
  implicit val referenceFormat: Format[UploadReference] =
    Format(Reads.of[String].map(UploadReference.apply), Writes(ref => JsString(ref.value)))
}
