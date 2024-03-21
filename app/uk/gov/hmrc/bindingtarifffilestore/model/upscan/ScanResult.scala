/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.model.upscan

import java.time.Instant

import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.{FAILED, READY, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.FailureReason.FailureReason
import uk.gov.hmrc.play.json.Union

case class SuccessfulScanResult(
  override val reference: String,
  downloadUrl: String,
  uploadDetails: UploadDetails
) extends ScanResult {
  override val fileStatus: model.ScanStatus.Value = READY
}

case class FailedScanResult(
  override val reference: String,
  failureDetails: FailureDetails
) extends ScanResult {
  override val fileStatus: model.ScanStatus.Value = FAILED
}

sealed trait ScanResult {
  val reference: String
  val fileStatus: ScanStatus
}

object ScanResult {
  implicit val formatSuccess: OFormat[SuccessfulScanResult] = Json.format[SuccessfulScanResult]
  implicit val formatFailed: OFormat[FailedScanResult]      = Json.format[FailedScanResult]
  implicit val format: Format[ScanResult]                   =
    Union
      .from[ScanResult]("fileStatus")
      .and[SuccessfulScanResult](READY.toString)
      .and[FailedScanResult](FAILED.toString)
      .format
}

case class UploadDetails(
  fileName: String,
  fileMimeType: String,
  uploadTimestamp: Instant,
  checksum: String
)

object UploadDetails {
  implicit val format: OFormat[UploadDetails] = Json.format
}

case class FailureDetails(
  failureReason: FailureReason,
  message: String
)

object FailureDetails {
  implicit val format: OFormat[FailureDetails] = Json.format
}
