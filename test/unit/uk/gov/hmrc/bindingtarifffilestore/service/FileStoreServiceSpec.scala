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
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class FileStoreServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val config = mock[AppConfig]
  private val s3Connector = mock[AmazonS3Connector]
  private val repository = mock[FileMetadataRepository]
  private val upscanConnector = mock[UpscanConnector]
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val service = new FileStoreService(config, s3Connector, repository, upscanConnector)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(repository)
  }

  "Service 'get by id'" should {
    "Delegate to Connector" in {
      val attachment = mock[FileMetadata]
      given(repository.get("id")).willReturn(successful(Some(attachment)))

      await(service.getById("id")) shouldBe Some(attachment)
    }
  }

  "Service 'upload'" should {
    "Delegate to Connector" in {
      val file = mock[TemporaryFile]
      val fileMetadata = mock[FileMetadata]
      val fileWithMetadata = FileWithMetadata(file, fileMetadata)
      val fileMetaDataCreated = mock[FileMetadata]
      given(repository.insert(fileMetadata)).willReturn(successful(fileMetaDataCreated))

      await(service.upload(fileWithMetadata)) shouldBe fileMetaDataCreated
    }
  }

  "Service 'notify'" should {
    val attachment = FileMetadata(fileName = "file", mimeType = "type")
    val attachmentUpdated = mock[FileMetadata]

    "Update the attachment for Successful Scan and Delegate to Connector" in {
      val scanResult = SuccessfulScanResult("ref", "url", mock[UploadDetails])
      val expectedAttachment = attachment.copy(url = Some("url"), scanStatus = Some(ScanStatus.READY))

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

    "Update the attachment for Failed Scan and Delegate to Connector" in {
      val scanResult = FailedScanResult("ref", mock[FailureDetails])
      val expectedAttachment = attachment.copy(scanStatus = Some(ScanStatus.FAILED))

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

  }

  "Service 'publish'" should {

    "Delegate to the File Store" in {
      val fileUploading = mock[FileMetadata]
      val fileUploaded = mock[FileMetadata]
      val fileUpdated = mock[FileMetadata]
      given(s3Connector.upload(fileUploading)).willReturn(fileUploaded)
      given(repository.update(fileUploaded)).willReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe fileUpdated
    }
  }

}
