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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{AmazonS3Connector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileStoreServiceSpec extends UnitSpec with MockitoSugar {

  private val config = mock[AppConfig]
  private val s3Connector = mock[AmazonS3Connector]
  private val repository = mock[FileMetadataRepository]
  private val upscanConnector = mock[UpscanConnector]
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val service = new FileStoreService(config, s3Connector, repository, upscanConnector)

  "Service 'get by id'" should {
    "Delegate to Connector" in {
      val attachment = mock[FileMetadata]
      given(repository.get("id")).willReturn(Future.successful(Some(attachment)))

      await(service.getById("id")) shouldBe Some(attachment)
    }
  }

  "Service 'upload'" should {
    "Delegate to Connector" in {
      val file = mock[TemporaryFile]
      val fileMetadata = mock[FileMetadata]
      val fileWithMetadata = FileWithMetadata(file, fileMetadata)
      val fileMetaDataCreated = mock[FileMetadata]
      given(repository.insert(fileMetadata)).willReturn(Future.successful(fileMetaDataCreated))

      await(service.upload(fileWithMetadata)) shouldBe fileMetaDataCreated
    }
  }

  "Service 'notify'" should {
    val attachment = FileMetadata(fileName = "file", mimeType = "type")
    val attachmentUpdated = mock[FileMetadata]

    "Update the attachment for Successful Scan and Delegate to Connector" in {
      val scanResult = SuccessfulScanResult("ref", "url", mock[UploadDetails])
      val expectedAttachment = attachment.copy(url = Some("url"), scanStatus = Some(ScanStatus.READY))

      given(repository.update(expectedAttachment)).willReturn(Future.successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

    "Update the attachment for Failed Scan and Delegate to Connector" in {
      val scanResult = FailedScanResult("ref", mock[FailureDetails])
      val expectedAttachment = attachment.copy(scanStatus = Some(ScanStatus.FAILED))

      given(repository.update(expectedAttachment)).willReturn(Future.successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

  }

  "Service 'publish'" should {

    "Upload the attachment to the Filestore" in {
      val file = mock[TemporaryFile]
      val metadata = FileMetadata(fileName = "file", mimeType = "type", url = Some("url"))
      val uploaded = FileWithMetadata(file, metadata)
      given(s3Connector.upload(any[FileWithMetadata])).willReturn(uploaded)

      await(service.publish(metadata)) shouldBe metadata
      val upload = theFileUploaded
      upload.file.file.getName shouldBe "url"
      upload.metadata shouldBe metadata
    }

    "Throw exception for missing URL" in {
      val exception = intercept[RuntimeException] {
        service.publish(FileMetadata(fileName = "file", mimeType = "type", url = None))
      }

      exception.getMessage shouldBe "Cannot publish a file without a URL"
    }
  }

  def theFileUploaded: FileWithMetadata = {
    val captor: ArgumentCaptor[FileWithMetadata] = ArgumentCaptor.forClass(classOf[FileWithMetadata])
    verify(s3Connector).upload(captor.capture())
    captor.getValue
  }
}
