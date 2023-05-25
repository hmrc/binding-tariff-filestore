/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.audit

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.DefaultAuditConnector

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AuditService @Inject() (auditConnector: DefaultAuditConnector)(implicit ec: ExecutionContext) {

  import AuditPayloadType._

  def auditUpScanInitiated(fileId: String, fileName: String, upScanRef: String)(implicit
    hc: HeaderCarrier
  ): Unit =
    sendExplicitAuditEvent(
      auditEventType = UpScanInitiated,
      auditPayload = fileDetailsAuditPayload(fileId, fileName) + ("upScanReference" -> upScanRef)
    )

  def auditFileScanned(fileId: String, fileName:  String, upScanRef: String, upScanStatus: String)(implicit
    hc: HeaderCarrier
  ): Unit =
    sendExplicitAuditEvent(
      auditEventType = FileScanned,
      auditPayload =
        fileDetailsAuditPayload(fileId, fileName) ++ Map("upScanReference" -> upScanRef, "upScanStatus" -> upScanStatus)
    )

  def auditFilePublished(fileId: String, fileName: String)(implicit hc: HeaderCarrier): Unit =
    sendExplicitAuditEvent(
      auditEventType = FilePublished,
      auditPayload = fileDetailsAuditPayload(fileId, fileName)
    )

  private def fileDetailsAuditPayload(fileId: String, fileName: String): Map[String, String] =
    Map(
      "fileId"   -> fileId,
      "fileName" -> fileName
    )

  private def sendExplicitAuditEvent(auditEventType: String, auditPayload: Map[String, String])(implicit
    hc: HeaderCarrier
  ): Unit                                                                                            =
    auditConnector.sendExplicitAudit(auditType = auditEventType, detail = auditPayload)

}

object AuditPayloadType {
  val UpScanInitiated = "upScanInitiated"
  val FileScanned     = "fileScanned"
  val FilePublished   = "filePublished"
}
