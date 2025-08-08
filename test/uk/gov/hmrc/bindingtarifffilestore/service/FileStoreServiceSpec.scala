/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditService
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, FileStoreSizeConfiguration}
import uk.gov.hmrc.bindingtarifffilestore.connector.{ObjectStoreConnector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class FileStoreServiceSpec extends UnitSpec with BeforeAndAfterEach with Eventually {

  private val config: AppConfig                       = mock(classOf[AppConfig])
  private val s3Connector: ObjectStoreConnector       = mock(classOf[ObjectStoreConnector])
  private val repository: FileMetadataMongoRepository = mock(classOf[FileMetadataMongoRepository])
  private val upscanConnector: UpscanConnector        = mock(classOf[UpscanConnector])
  private val auditService: AuditService              = mock(classOf[AuditService])
  private implicit val hc: HeaderCarrier              = HeaderCarrier()

  private val service: FileStoreService =
    new FileStoreService(config, s3Connector, repository, upscanConnector, auditService)

  private final val emulatedFailure: RuntimeException = new RuntimeException("Emulated failure.")

  private val (minimumFileSize, maximumFileSize): (Int, Int) = (1, 1000)

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(config)
    reset(s3Connector)
    reset(repository)
    reset(upscanConnector)
    reset(auditService)
  }

  "Service 'delete all' " should {

    "Clear the Database & File Store" in {
      when(repository.deleteAll()).thenReturn(successful(()))

      await(service.deleteAll()) shouldBe ((): Unit)

      verify(repository).deleteAll()
      verify(s3Connector).deleteAll()
    }

    "Propagate any error" in {
      when(repository.deleteAll()).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteAll())
      }
      caught shouldBe emulatedFailure
    }
  }

  "Service 'delete one' " should {

    "Clear the Database & File Store" in {
      when(repository.delete("id")).thenReturn(successful(()))

      await(service.delete("id")) shouldBe ((): Unit)

      verify(repository).delete("id")
      verify(s3Connector).delete("id")
    }

    "Propagate any error" in {
      when(repository.deleteAll()).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteAll())
      }
      caught shouldBe emulatedFailure
    }
  }

  "Service 'getAll by id' " should {

    "Delegate to Connector" in {
      val attachment       = mock(classOf[FileMetadata], "attachment")
      val attachmentSigned = mock(classOf[FileMetadata], "attachmentSigned")

      given(attachment.published).willReturn(true)
      given(repository.get("id")).willReturn(successful(Some(attachment)))
      given(s3Connector.sign(attachment)).willReturn(attachmentSigned)

      await(service.find("id")) shouldBe Some(attachmentSigned)
    }

    "Not sign unpublished files" in {
      val attachment = mock(classOf[FileMetadata])
      given(repository.get("id")).willReturn(successful(Some(attachment)))

      await(service.find("id")) shouldBe Some(attachment)

      verify(s3Connector, never()).sign(any[FileMetadata])(any[HeaderCarrier])
    }
  }

  "Service 'getAll by search' " should {

    "delegate to repository" in {
      given(repository.get(Search(), Pagination())).willReturn(successful(Paged.empty[FileMetadata]))

      await(service.find(Search(), Pagination())) shouldBe Paged.empty[FileMetadata]
    }

    "return all attachment requested already signed" in {
      val attachment1 = mock(classOf[FileMetadata], "attachment")
      val attSigned1  = mock(classOf[FileMetadata], "attachmentSigned")

      val attachment2 = mock(classOf[FileMetadata], "attachment2")

      given(attachment1.published).willReturn(true)
      given(s3Connector.sign(attachment1)).willReturn(attSigned1)

      given(attachment2.published).willReturn(false)

      given(repository.get(Search(), Pagination())).willReturn(successful(Paged(Seq(attachment1, attachment2))))

      await(service.find(Search(), Pagination())) shouldBe Paged(Seq(attSigned1, attachment2))
    }
  }

  "Service 'initiate'" should {

    "Delegate to Connector" in {
      val fileMetadata        = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("text/plain"))
      val fileMetaDataCreated = mock(classOf[FileMetadata])
      val uploadTemplate      = UpscanTemplate(href = "href", fields = Map("key" -> "value"))
      val initiateResponse    = UpscanInitiateResponse("ref", uploadTemplate)

      given(config.filestoreUrl).willReturn("host")
      given(config.fileStoreSizeConfiguration).willReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      given(config.authorization).willReturn("auth-token")
      given(repository.insertFile(fileMetadata)).willReturn(successful(Some(fileMetaDataCreated)))
      given(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).willReturn(successful(initiateResponse))

      await(service.initiate(fileMetadata)) shouldBe UploadTemplate(
        id = "id",
        href = "href",
        fields = Map("key" -> "value")
      )

      verify(auditService, times(1)).auditUpScanInitiated(fileId = "id", fileName = Some("file"), upScanRef = "ref")
      verifyNoMoreInteractions(auditService)

      theInitiatePayload shouldBe UploadSettings(
        "http://host/file/id/notify?X-Api-Token=2yL0YYIInq0TGnTCyaUwQhXpxtIktdzWH7QIx9mmMWU=",
        minimumFileSize,
        maximumFileSize
      )
    }
  }

  "Service 'initiateV2'" should {
    "Delegate to Connector" in {
      val initiateRequest  = v2.FileStoreInitiateRequest(id = Some("id"))
      val fileMetadata     = FileMetadata("id", None, None)
      val uploadTemplate   = v2.UpscanFormTemplate(href = "href", fields = Map("key" -> "value"))
      val initiateResponse = v2.UpscanInitiateResponse("ref", uploadTemplate)

      given(config.filestoreSSL).willReturn(false)
      given(config.filestoreUrl).willReturn("host")
      given(config.fileStoreSizeConfiguration).willReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      given(config.authorization).willReturn("auth-token")
      given(repository.insertFile(any[FileMetadata])).willReturn(successful(Some(fileMetadata)))
      given(upscanConnector.initiateV2(any[v2.UpscanInitiateRequest])(any[HeaderCarrier]))
        .willReturn(successful(initiateResponse))

      await(service.initiateV2(initiateRequest)) shouldBe v2
        .FileStoreInitiateResponse("id", "ref", initiateResponse.uploadRequest)

      verify(auditService, times(1)).auditUpScanInitiated(fileId = "id", fileName = None, upScanRef = "ref")
      verifyNoMoreInteractions(auditService)

      theInitiateV2Payload shouldBe v2.UpscanInitiateRequest(
        callbackUrl = "http://host/file/id/notify?X-Api-Token=2yL0YYIInq0TGnTCyaUwQhXpxtIktdzWH7QIx9mmMWU=",
        successRedirect = None,
        errorRedirect = None,
        minimumFileSize = Some(minimumFileSize),
        maximumFileSize = Some(maximumFileSize),
        expectedContentType = None
      )
    }
  }

  private def theInitiateV2Payload: v2.UpscanInitiateRequest = {
    val captor: ArgumentCaptor[v2.UpscanInitiateRequest] = ArgumentCaptor.forClass(classOf[v2.UpscanInitiateRequest])
    verify(upscanConnector).initiateV2(captor.capture())(any[HeaderCarrier])
    captor.getValue
  }

  "Service 'upload'" should {
    val file: TemporaryFile                      = mock(classOf[TemporaryFile])
    val fileMetadata: FileMetadata               = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("text/plain"))
    val fileWithMetadata: FileWithMetadata       = FileWithMetadata(file, fileMetadata)
    val fileMetaDataCreated: FileMetadata        = mock(classOf[FileMetadata])
    val uploadTemplate: UpscanTemplate           = mock(classOf[UpscanTemplate])
    val initiateResponse: UpscanInitiateResponse = UpscanInitiateResponse("ref", uploadTemplate)

    "Delegate to Connector" in {
      given(config.filestoreUrl).willReturn("host")
      given(config.fileStoreSizeConfiguration).willReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      given(config.authorization).willReturn("auth-token")
      given(repository.insertFile(fileMetadata)).willReturn(successful(Some(fileMetaDataCreated)))

      given(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).willReturn(successful(initiateResponse))
      given(upscanConnector.upload(any[UpscanTemplate], any[FileWithMetadata]))
        .willReturn(successful((): Unit))

      await(service.upload(fileWithMetadata)) shouldBe Some(fileMetaDataCreated)

      verify(auditService, times(1)).auditUpScanInitiated(fileId = "id", fileName = Some("file"), upScanRef = "ref")
      verifyNoMoreInteractions(auditService)

      theInitiatePayload shouldBe UploadSettings(
        "http://host/file/id/notify?X-Api-Token=2yL0YYIInq0TGnTCyaUwQhXpxtIktdzWH7QIx9mmMWU=",
        minimumFileSize,
        maximumFileSize
      )
    }

    "return an exception" in {
      given(config.filestoreUrl).willReturn("host")
      given(config.fileStoreSizeConfiguration).willReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      given(config.authorization).willReturn("auth-token")
      given(repository.insertFile(fileMetadata)).willReturn(successful(Some(fileMetaDataCreated)))

      given(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).willReturn(successful(initiateResponse))
      given(upscanConnector.upload(any[UpscanTemplate], any[FileWithMetadata]))
        .willReturn(failed(new Exception("Test")))

      val result = await(service.upload(fileWithMetadata))
      result.failed.map(_ shouldBe an[Exception])
    }
  }

  private def theInitiatePayload: UploadSettings = {
    val captor: ArgumentCaptor[UploadSettings] = ArgumentCaptor.forClass(classOf[UploadSettings])
    verify(upscanConnector).initiate(captor.capture())(any[HeaderCarrier])
    captor.getValue
  }

  "Service 'notify' " should {

    "Update the attachment for Successful Scan and Delegate to Connector" in {
      val attachment         = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"))
      val attachmentUpdated  = mock(classOf[FileMetadata], "AttachmentUpdated")
      val uploadDetails      = UploadDetails("file", "type", Instant.now(), "checksum")
      val scanResult         = SuccessfulScanResult(reference = "ref", downloadUrl = "url", uploadDetails = uploadDetails)
      val expectedAttachment = attachment.copy(url = Some("url"), scanStatus = Some(ScanStatus.READY))

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)

      verify(auditService, times(1))
        .auditFileScanned(fileId = "id", fileName = Some("file"), upScanRef = "ref", upScanStatus = "READY")
      verifyNoMoreInteractions(auditService)
    }

    "Call publish when notifying published files" in {
      val attachment                 = mock(classOf[FileMetadata], "Attachment")
      val uploadDetails              = UploadDetails("file", "type", Instant.now(), "checksum")
      val scanResult                 = SuccessfulScanResult(reference = "ref", downloadUrl = "url", uploadDetails = uploadDetails)
      val attachmentUpdating         = mock(classOf[FileMetadata], "AttachmentUpdating")
      val attachmentUpdated          = mock(classOf[FileMetadata], "AttachmentUpdated")
      val attachmentUploaded         = mock(classOf[FileMetadata], "AttachmentUploaded")
      val attachmentUploadedUpdating = mock(classOf[FileMetadata], "AttachmentUploadedUpdating")
      val attachmentUploadedUpdated  = mock(classOf[FileMetadata], "AttachmentUploadedUpdated")
      val attachmentSigned           = mock(classOf[FileMetadata], "AttachmentSigned")

      given(attachment.withScanResult(scanResult)).willReturn(attachmentUpdating)
      given(attachment.publishable).willReturn(true)
      given(attachment.published).willReturn(false)
      given(attachment.isLive).willReturn(true)
      given(attachment.id).willReturn("id")
      given(attachment.fileName).willReturn(Some("file"))

      given(attachmentUpdating.publishable).willReturn(true)
      given(attachmentUpdating.published).willReturn(false)
      given(attachmentUpdating.isLive).willReturn(true)

      given(attachmentUpdated.publishable).willReturn(true)
      given(attachmentUpdated.published).willReturn(false)
      given(attachmentUpdated.isLive).willReturn(true)
      given(attachmentUpdated.scanStatus).willReturn(Some(ScanStatus.READY))
      given(attachmentUpdated.id).willReturn("id")
      given(attachmentUpdated.fileName).willReturn(Some("file"))

      given(attachmentUploaded.published).willReturn(true)
      given(attachmentUploaded.publishable).willReturn(true)
      given(attachmentUploaded.isLive).willReturn(true)
      given(attachmentUploaded.fileName).willReturn(Some("file"))
      given(attachmentUploaded.mimeType).willReturn(Some("mimetype"))
      given(attachmentUploaded.url).willReturn(Some("url"))
      given(attachmentUploaded.scanStatus).willReturn(Some(ScanStatus.READY))
      given(attachmentUploaded.copy(published = true, publishable = true)).willReturn(attachmentUploadedUpdating)

      given(attachmentUploadedUpdated.published).willReturn(true)
      given(attachmentUploadedUpdated.isLive).willReturn(true)

      given(repository.update(attachmentUpdating)).willReturn(successful(Some(attachmentUpdated)))
      given(s3Connector.upload(attachmentUpdated)).willReturn(attachmentUploaded)
      given(repository.update(attachmentUploadedUpdating)).willReturn(successful(Some(attachmentUploadedUpdated)))
      given(s3Connector.sign(attachmentUploadedUpdated)).willReturn(attachmentSigned)

      val test = await(service.notify(attachment, scanResult))
      test shouldBe Some(attachmentSigned)

      verify(auditService, times(1))
        .auditFileScanned(fileId = "id", fileName = Some("file"), upScanRef = "ref", upScanStatus = "READY")
      verify(auditService, times(1)).auditFilePublished(fileId = "id", fileName = "file")
      verifyNoMoreInteractions(auditService)
    }

    def test(value: Boolean): Unit =
      s"Skip publishing when the file no longer exists and publishable for attachmentUpdating is set to $value" in {
        val attachment: FileMetadata         = mock(classOf[FileMetadata], "Attachment")
        val uploadDetails: UploadDetails     = UploadDetails("file", "type", Instant.now(), "checksum")
        val scanResult: SuccessfulScanResult =
          SuccessfulScanResult(reference = "ref", downloadUrl = "url", uploadDetails = uploadDetails)
        val attachmentUpdating: FileMetadata =
          FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"), publishable = value)
        val attachmentUpdated: FileMetadata  = mock(classOf[FileMetadata], "AttachmentUpdated")

        given(attachment.withScanResult(scanResult)).willReturn(attachmentUpdating)
        given(attachment.publishable).willReturn(true)
        given(attachment.id).willReturn("id")
        given(attachment.fileName).willReturn(Some("file"))

        given(attachmentUpdated.published).willReturn(true)
        given(attachmentUpdated.scanStatus).willReturn(Some(ScanStatus.READY))
        given(attachmentUpdated.id).willReturn("id")
        given(attachmentUpdated.fileName).willReturn(Some("file"))

        given(repository.update(attachmentUpdating)).willReturn(successful(None))

        await(service.notify(attachment, scanResult)) shouldBe None

        verify(s3Connector, never()).upload(any[FileMetadata]())(any[HeaderCarrier]())
        verify(s3Connector, never()).sign(any[FileMetadata]())(any[HeaderCarrier]())
        verify(auditService, times(1))
          .auditFileScanned(fileId = "id", fileName = Some("file"), upScanRef = "ref", upScanStatus = "READY")
        verify(auditService, never).auditFilePublished(fileId = "id", fileName = "file")
        verifyNoMoreInteractions(auditService)
      }

    Seq(false, true).foreach(test)

    "Update the attachment for Failed Scan and Delegate to Connector" in {
      val attachment         = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"))
      val scanResult         = FailedScanResult(reference = "ref", failureDetails = mock(classOf[FailureDetails]))
      val expectedAttachment = attachment.copy(scanStatus = Some(ScanStatus.FAILED))
      val attachmentUpdated  = mock(classOf[FileMetadata], "AttachmentUpdated")

      given(repository.update(expectedAttachment)).willReturn(successful(Some(attachmentUpdated)))

      await(service.notify(attachment, scanResult)) shouldBe Some(attachmentUpdated)

      verify(auditService, times(1))
        .auditFileScanned(fileId = "id", fileName = Some("file"), upScanRef = "ref", upScanStatus = "FAILED")
      verifyNoMoreInteractions(auditService)
    }

  }

  "Service 'publish' " should {

    "Delegate to the File Store if Scanned Safe" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")
      val fileUploaded  = mock(classOf[FileMetadata], "Uploaded")
      val fileUpdating  = mock(classOf[FileMetadata], "Updating")
      val fileUpdated   = mock(classOf[FileMetadata], "Updated")
      val fileSigned    = mock(classOf[FileMetadata], "Signed")

      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.READY))
      given(fileUploading.published).willReturn(false)
      given(fileUploading.isLive).willReturn(true)
      given(fileUploading.id).willReturn("id")
      given(fileUploading.fileName).willReturn(Some("file"))

      given(fileUploaded.copy(published = true)).willReturn(fileUpdating)

      given(fileUpdated.published).willReturn(true)

      given(s3Connector.upload(fileUploading)).willReturn(fileUploaded)
      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))
      given(s3Connector.sign(fileUpdated)).willReturn(fileSigned)

      await(service.publish(fileUploading)) shouldBe Some(fileSigned)

      verify(auditService, times(1)).auditFilePublished(fileId = "id", fileName = "file")
      verifyNoMoreInteractions(auditService)
    }

    "Clear up unpublished expired files" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")

      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.READY))
      given(fileUploading.published).willReturn(false)
      given(fileUploading.isLive).willReturn(false)
      given(fileUploading.id).willReturn("id")

      given(repository.delete(any[String])(any[ExecutionContext])).willReturn(successful(()))

      await(service.publish(fileUploading)) shouldBe None

      verify(repository).delete("id")
      verifyNoInteractions(auditService, s3Connector)
    }

    "Not delegate to the File Store if pre published" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")

      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.READY))
      given(fileUploading.published).willReturn(true)

      await(service.publish(fileUploading)) shouldBe Some(fileUploading)

      verifyNoInteractions(auditService, s3Connector, repository)
    }

    "Not delegate to the File Store if Scanned UnSafe" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")
      val fileUpdating  = mock(classOf[FileMetadata], "Updating")
      val fileUpdated   = mock(classOf[FileMetadata], "Updated")

      given(fileUploading.scanStatus).willReturn(Some(ScanStatus.FAILED))
      given(fileUploading.copy(published = true)).willReturn(fileUpdating)

      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)

      verifyNoInteractions(auditService, s3Connector)
    }

    "Not delegate to the File Store if Unscanned" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")
      val fileUpdating  = mock(classOf[FileMetadata], "Updating")
      val fileUpdated   = mock(classOf[FileMetadata], "Updated")

      given(fileUploading.scanStatus).willReturn(None)
      given(fileUploading.copy(published = true)).willReturn(fileUpdating)

      given(repository.update(any[FileMetadata])).willReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)

      verifyNoInteractions(auditService, s3Connector)
    }
  }

}
