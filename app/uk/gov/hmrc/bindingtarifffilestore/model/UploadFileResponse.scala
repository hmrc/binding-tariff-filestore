package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json.{Json, OFormat}

case class UploadFileResponse(reference: UploadReference, uploadRequest: UploadRequestTemplate)

object UploadFileResponse {
  implicit val uploadFormat: OFormat[UploadFileResponse] = Json.format
}
