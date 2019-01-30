package uk.gov.hmrc.bindingtarifffilestore.audit

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class AuditService @Inject()(auditConnector: DefaultAuditConnector) {

  import AuditPayloadType._

  def auditUpScanInitiated(fileId: String, fileName: String, upScanRef: String)(implicit hc: HeaderCarrier): Unit = {
    sendExplicitAuditEvent(
      auditEventType = UpScanInitiated,
      auditPayload = fileDetailsAuditPayload(fileId, fileName) + ("upScanReference" -> upScanRef)
    )
  }

  def auditFileScanned(fileId: String, fileName: String, upScanRef: String, upScanStatus: String)(implicit hc: HeaderCarrier): Unit = {
    sendExplicitAuditEvent(
      auditEventType = FileScanned,
      auditPayload = fileDetailsAuditPayload(fileId, fileName) + ("upScanReference" -> upScanRef, "upScanStatus" -> upScanStatus)
    )
  }

  def auditFilePublished(fileId: String, fileName: String)(implicit hc: HeaderCarrier): Unit = {
    sendExplicitAuditEvent(
      auditEventType = FilePublished,
      auditPayload = fileDetailsAuditPayload(fileId, fileName)
    )
  }

  private def fileDetailsAuditPayload(fileId: String, fileName: String): Map[String, String] = {
    Map(
      "fileId" -> fileId,
      "fileName" -> fileName
    )
  }

  private def sendExplicitAuditEvent(auditEventType: String, auditPayload: Map[String, String])
                                    (implicit hc: HeaderCarrier): Unit = {
    auditConnector.sendExplicitAudit(auditType = auditEventType, detail = auditPayload)
  }

}

object AuditPayloadType {

  val UpScanInitiated = "UpScanInitiated"
  val FileScanned = "FileScanned"
  val FilePublished = "FilePublished"
}
