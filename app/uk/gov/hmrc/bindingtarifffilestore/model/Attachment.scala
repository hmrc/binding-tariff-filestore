package uk.gov.hmrc.bindingtarifffilestore.model

import java.util.UUID

import play.api.libs.json.Json
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.ScanStatus

case class Attachment
(
  id: String = UUID.randomUUID().toString,
  name: String,
  scanStatus: Option[ScanStatus] = None
)

object Attachment {
  implicit val attachmentFormat = Json.format[Attachment]
}

object ScanStatus extends Enumeration {
  type ScanStatus = Value
  implicit val scanStatusFormat = Json.format[ScanStatus]
  val READY, FAILED = Value
}
