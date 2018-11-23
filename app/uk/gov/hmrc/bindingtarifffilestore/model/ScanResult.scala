package uk.gov.hmrc.bindingtarifffilestore.model

import java.time.Instant

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model.FailureReason.FailureReason
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.ScanStatus

case class ScanResult(
      reference: String,
      fileStatus: ScanStatus,
      downloadUrl: Option[String],
      uploadDetails: Option[UploadDetails],
      failureDetails: Option[FailureDetails])

object ScanResult {
  implicit val format = Json.format[ScanResult]
}

case class UploadDetails(uploadTimestamp: Instant, checksum: String)

object UploadDetails {
  implicit val format: OFormat[UploadDetails] = Json.format
}

case class FailureDetails(failureReason: FailureReason, message: String)

object FailureDetails {
  implicit val format: OFormat[FailureDetails] = Json.format
}
