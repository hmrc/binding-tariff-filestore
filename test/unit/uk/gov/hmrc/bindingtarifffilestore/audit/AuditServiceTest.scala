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

import org.mockito.ArgumentMatchers.refEq
import org.mockito.Mockito.{reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditPayloadType._
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.DefaultAuditConnector

import scala.concurrent.ExecutionContext.Implicits.global

class AuditServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val connector                  = mock[DefaultAuditConnector]

  private val service = new AuditService(connector)

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(connector)
  }

  private val fileId       = "id"
  private val fileName     = "name"
  private val upScanStatus = "upscan-status"
  private val upScanRef    = "upscan-ref"

  "auditUpScanInitiated()" should {

    "send the expected payload to the audit connector" in {
      service.auditUpScanInitiated(fileId, Some(fileName), upScanRef)

      val payload = auditPayload(fileId, fileName) + ("upScanReference" -> upScanRef)

      verify(connector).sendExplicitAudit(refEq(UpScanInitiated), refEq(payload))(refEq(hc), refEq(global))
    }
  }

  "auditFileScanned()" should {

    "send the expected payload to the audit connector" in {
      service.auditFileScanned(fileId, Some(fileName), upScanRef, upScanStatus)

      val payload =
        auditPayload(fileId, fileName) ++ Map("upScanReference" -> upScanRef, "upScanStatus" -> upScanStatus)

      verify(connector).sendExplicitAudit(refEq(FileScanned), refEq(payload))(refEq(hc), refEq(global))
    }
  }

  "auditFilePublished()" should {

    "send the expected payload to the audit connector" in {
      service.auditFilePublished(fileId, fileName)

      val payload = auditPayload(fileId, fileName)

      verify(connector).sendExplicitAudit(refEq(FilePublished), refEq(payload))(refEq(hc), refEq(global))
    }
  }

  private def auditPayload(fileId: String, fileName: String): Map[String, String] =
    Map(
      "fileId"   -> fileId,
      "fileName" -> fileName
    )

}
