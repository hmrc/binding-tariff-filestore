/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.service

import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtarifffilestore.connector.AmazonS3Connector
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.{ScanStatus, TemporaryAttachment}
import uk.gov.hmrc.bindingtarifffilestore.repository.TemporaryAttachmentRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileStoreServiceTest extends UnitSpec with MockitoSugar {

  private val connector = mock[AmazonS3Connector]
  private val repository = mock[TemporaryAttachmentRepository]

  val service = new FileStoreService(connector, repository)

  "Service 'get by id'" should {

    "Delegate to Connector" in {
      val attachment = mock[TemporaryAttachment]
      given(repository.get("id")).willReturn(Future.successful(Some(attachment)))

      await(service.getById("id")) shouldBe Some(attachment)
    }
  }

  "Service 'upload'" should {

    "Delegate to Connector" in {
      val attachment = mock[TemporaryAttachment]
      val attachmentCreated = mock[TemporaryAttachment]
      given(repository.insert(attachment)).willReturn(Future.successful(attachmentCreated))

      await(service.upload(attachment)) shouldBe attachmentCreated
    }
  }

  "Service 'notify'" should {
    val attachment = TemporaryAttachment(fileName = "file", mimeType = "type")
    val attachmentUpdated = mock[TemporaryAttachment]

    "Update the attachment for Successful Scan and Delegate to Connector" in {
      val scanResult = SuccessfulScanResult("ref", "url", mock[UploadDetails])
      val expectedAttachment = attachment.copy(url = Some("url"), scanStatus = Some(ScanStatus.READY))

      given(repository.update(expectedAttachment)).willReturn(Future.successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe attachmentUpdated
    }

    "Update the attachment for Failed Scan and Delegate to Connector" in {
      val scanResult = FailedScanResult("ref", mock[FailureDetails])
      val expectedAttachment = attachment.copy(scanStatus = Some(ScanStatus.FAILED))

      given(repository.update(expectedAttachment)).willReturn(Future.successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe attachmentUpdated
    }

  }

}
