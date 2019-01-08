/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, reset, verify}
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

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(repository, s3Connector)
  }

  "Service 'get by id'" should {
    "Delegate to Connector" in {
      val attachment = mock[FileMetadata]
      val attachmentSigned = mock[FileMetadata]
      given(attachment.published).willReturn(true)
      given(repository.get("id")).willReturn(successful(Some(attachment)))
      given(s3Connector.sign(attachment)).willReturn(attachmentSigned)

      await(service.getById("id")) shouldBe Some(attachmentSigned)
    }

    "Not sign unpublished files" in {
      val attachment = mock[FileMetadata]
      given(repository.get("id")).willReturn(successful(Some(attachment)))

      await(service.getById("id")) shouldBe Some(attachment)

      verify(s3Connector, never()).sign(any[FileMetadata])
    }
  }

  "Service 'get by id list'" should {

    "return empty when no ids are requested" in {
      given(repository.getAll(Seq.empty)).willReturn(successful(Seq.empty))
      await(service.getByIds(Seq())) shouldBe Seq.empty
    }

    "return empty when no ids are found on the db" in {
      given(repository.getAll(Seq("unknownFile"))).willReturn(successful(Seq.empty))
      await(service.getByIds(Seq("unknownFile"))) shouldBe Seq.empty
    }

    "return all attachment requested already signed" in {
      val attatchment1 = mock[FileMetadata]
      val attSigned1 = mock[FileMetadata]
      given(attatchment1.published).willReturn(true)
      given(s3Connector.sign(attatchment1)).willReturn(attSigned1)

      val attatchment2 = mock[FileMetadata]
      val attSigned2 = mock[FileMetadata]
      given(attatchment2.published).willReturn(true)
      given(s3Connector.sign(attatchment2)).willReturn(attSigned2)

      given(repository.getAll(Seq("filename_1","filename_2"))).willReturn(successful(Seq(attatchment1,attatchment2)))

      await(service.getByIds(Seq("filename_1","filename_2"))) shouldBe Seq(attSigned1,attSigned2)
    }
  }

  "Service 'upload'" should {
    "Delegate to Connector" in {
      val file = mock[TemporaryFile]
      val fileMetadata = FileMetadata(id = "id", fileName = "file", mimeType = "text/plain")
      val fileWithMetadata = FileWithMetadata(file, fileMetadata)
      val fileMetaDataCreated = mock[FileMetadata]
      val uploadTemplate = mock[UploadRequestTemplate]
      val initiateResponse = UpscanInitiateResponse("ref", uploadTemplate)
      given(repository.insert(fileMetadata)).willReturn(successful(fileMetaDataCreated))
      given(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).willReturn(successful(initiateResponse))

      await(service.upload(fileWithMetadata)) shouldBe fileMetaDataCreated
    }
  }

  "Service 'notify'" should {


    "Update the attachment for Successful Scan and Delegate to Connector" in {
      val attachment = FileMetadata(fileName = "file", mimeType = "type")
      val attachmentUpdated = mock[FileMetadata]("updated")
      val scanResult = SuccessfulScanResult("ref", "url", mock[UploadDetails])
      val expectedAttachment = attachment.copy(url = Some("url"), scanStatus = Some(ScanStatus.READY))

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

    "Call publish when notifying published files" in {
      val attachment = mock[FileMetadata]("Attachment")
      val scanResult = SuccessfulScanResult("ref", "url", mock[UploadDetails])
      val attachmentUpdating = mock[FileMetadata]("AttachmentUpdating")
      val attachmentUpdated = mock[FileMetadata]("AttachmentUpdated")
      val attachmentUploaded = mock[FileMetadata]("AttachmentUploaded")
      val attachmentUploadedUpdated = mock[FileMetadata]("AttachmentUploadedAndUpdated")
      val attachmentSigned = mock[FileMetadata]("AttachmentSigned")

      given(attachment.copy(scanStatus = Some(ScanStatus.READY), url = Some("url"))).willReturn(attachmentUpdating)
      given(attachment.published).willReturn(true)
      given(attachmentUpdating.published).willReturn(true)
      given(attachmentUpdated.published).willReturn(true)
      given(attachmentUpdated.scanStatus).willReturn(Some(ScanStatus.READY))
      given(attachmentUploaded.published).willReturn(true)

      given(repository.update(attachmentUpdating)).willReturn(successful(Some(attachmentUpdated)))
      given(s3Connector.upload(attachmentUpdated)).willReturn(attachmentUploaded)
      given(repository.update(attachmentUploaded)).willReturn(successful(Some(attachmentUploadedUpdated)))
      given(s3Connector.sign(attachmentUploadedUpdated)).willReturn(attachmentSigned)

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentSigned)
    }

    "Update the attachment for Failed Scan and Delegate to Connector" in {
      val attachment = FileMetadata(fileName = "file", mimeType = "type")
      val scanResult = FailedScanResult("ref", mock[FailureDetails])
      val expectedAttachment = attachment.copy(scanStatus = Some(ScanStatus.FAILED))
      val attachmentUpdated = mock[FileMetadata]("updated")

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)
    }

  }

  "Service 'publish'" should {

    "Delegate to the File Store if Scanned Safe" in {
      val fileUploading = mock[FileMetadata]("Uploading")
      val fileUploaded = mock[FileMetadata]("Uploaded")
      val fileUpdating = mock[FileMetadata]("Updating")
      val fileUpdated = mock[FileMetadata]("Updated")
      val fileSigned = mock[FileMetadata]("Signed")
      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.READY))
      given(fileUploaded.copy(published = true)).willReturn(fileUpdating)
      given(s3Connector.upload(fileUploading)).willReturn(fileUploaded)
      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))
      given(s3Connector.sign(fileUpdated)).willReturn(fileSigned)

      await(service.publish(fileUploading)) shouldBe Some(fileSigned)
    }

    "Not delegate to the File Store if Scanned UnSafe" in {
      val fileUploading = mock[FileMetadata]("Uploading")
      val fileUpdating = mock[FileMetadata]("Updating")
      val fileUpdated = mock[FileMetadata]("Updated")
      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.FAILED))
      given(fileUploading.copy(published = true)).willReturn(fileUpdating)
      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)
      verify(s3Connector, never()).upload(any[FileMetadata])
    }

    "Not delegate to the File Store if Unscanned" in {
      val fileUploading = mock[FileMetadata]("Uploading")
      val fileUpdating = mock[FileMetadata]("Updating")
      val fileUpdated = mock[FileMetadata]("Updated")
      given(fileUploading.scanStatus).willReturn(None)
      given(fileUploading.copy(published = true)).willReturn(fileUpdating)
      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)
      verify(s3Connector, never()).upload(any[FileMetadata])
    }
  }

}
