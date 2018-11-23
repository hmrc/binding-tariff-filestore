package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.libs.json.Json

object ScanStatus extends Enumeration {
  type ScanStatus = Value
  implicit val scanStatusFormat = Json.format[ScanStatus]
  val READY, FAILED = Value
}
