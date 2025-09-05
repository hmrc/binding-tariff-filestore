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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditService
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, FileStoreSizeConfiguration}
import uk.gov.hmrc.bindingtarifffilestore.connector.{ObjectStoreConnector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.model.*
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.*
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class FileStoreServiceSpec extends UnitSpec with BeforeAndAfterEach with Eventually {

  private val config: AppConfig                          = mock(classOf[AppConfig])
  private val objectStoreConnector: ObjectStoreConnector = mock(classOf[ObjectStoreConnector])
  private val repository: FileMetadataMongoRepository    = mock(classOf[FileMetadataMongoRepository])
  private val upscanConnector: UpscanConnector           = mock(classOf[UpscanConnector])
  private val auditService: AuditService                 = mock(classOf[AuditService])
  private implicit val hc: HeaderCarrier                 = HeaderCarrier()

  private val service: FileStoreService =
    new FileStoreService(config, objectStoreConnector, repository, upscanConnector, auditService)

  private final val emulatedFailure: RuntimeException = new RuntimeException("Emulated failure.")

  private val (minimumFileSize, maximumFileSize): (Int, Int) = (1, 1000)

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(config)
    reset(objectStoreConnector)
    reset(repository)
    reset(upscanConnector)
    reset(auditService)
  }

  "Service 'delete all' " should {

    "Clear the Database & File Store" in {
      when(repository.deleteAll()).thenReturn(successful(()))

      await(service.deleteAll()) shouldBe ((): Unit)

      verify(repository).deleteAll()
      verify(objectStoreConnector).deleteAll()
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

      await(service.delete("id", "fileName")) shouldBe ((): Unit)

      verify(repository).delete("id")
      verify(objectStoreConnector).delete("fileName")
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

      when(attachment.published).thenReturn(true)
      when(repository.get("id")).thenReturn(successful(Some(attachment)))
      when(objectStoreConnector.sign(attachment)).thenReturn(Future.successful(attachmentSigned))

      await(service.find("id")) shouldBe Some(attachmentSigned)
    }

    "Not sign unpublished files" in {
      val attachment = mock(classOf[FileMetadata])
      when(repository.get("id")).thenReturn(successful(Some(attachment)))

      await(service.find("id")) shouldBe Some(attachment)

      verify(objectStoreConnector, never).sign(any[FileMetadata])(any[HeaderCarrier])
    }
  }

  "Service 'getAll by search' " should {

    "delegate to repository" in {
      when(repository.get(Search(), Pagination())).thenReturn(successful(Paged.empty[FileMetadata]))

      await(service.find(Search(), Pagination())) shouldBe Paged.empty[FileMetadata]
    }

    "return all attachment requested already signed" in {
      val attachment1 = mock(classOf[FileMetadata], "attachment")
      val attSigned1  = mock(classOf[FileMetadata], "attachmentSigned")

      val attachment2 = mock(classOf[FileMetadata], "attachment2")

      when(attachment1.published).thenReturn(true)
      when(objectStoreConnector.sign(attachment1)).thenReturn(successful(attSigned1))

      when(attachment2.published).thenReturn(false)

      when(repository.get(Search(), Pagination())).thenReturn(successful(Paged(Seq(attachment1, attachment2))))

      await(service.find(Search(), Pagination())) shouldBe Paged(Seq(attSigned1, attachment2))
    }
  }

  "Service 'initiate'" should {

    "Delegate to Connector" in {
      val fileMetadata        = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("text/plain"))
      val fileMetaDataCreated = mock(classOf[FileMetadata])
      val uploadTemplate      = UpscanTemplate(href = "href", fields = Map("key" -> "value"))
      val initiateResponse    = UpscanInitiateResponse("ref", uploadTemplate)

      when(config.filestoreUrl).thenReturn("host")
      when(config.fileStoreSizeConfiguration).thenReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      when(config.authorization).thenReturn("auth-token")
      when(repository.insertFile(fileMetadata)).thenReturn(successful(Some(fileMetaDataCreated)))
      when(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).thenReturn(successful(initiateResponse))

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

      when(config.filestoreSSL).thenReturn(false)
      when(config.filestoreUrl).thenReturn("host")
      when(config.fileStoreSizeConfiguration).thenReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      when(config.authorization).thenReturn("auth-token")
      when(repository.insertFile(any[FileMetadata])).thenReturn(successful(Some(fileMetadata)))
      when(upscanConnector.initiateV2(any[v2.UpscanInitiateRequest])(any[HeaderCarrier]))
        .thenReturn(successful(initiateResponse))

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
      when(config.filestoreUrl).thenReturn("host")
      when(config.fileStoreSizeConfiguration).thenReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      when(config.authorization).thenReturn("auth-token")
      when(repository.insertFile(fileMetadata)).thenReturn(successful(Some(fileMetaDataCreated)))

      when(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).thenReturn(successful(initiateResponse))
      when(upscanConnector.upload(any[UpscanTemplate], any[FileWithMetadata]))
        .thenReturn(successful((): Unit))

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
      when(config.filestoreUrl).thenReturn("host")
      when(config.fileStoreSizeConfiguration).thenReturn(FileStoreSizeConfiguration(minimumFileSize, maximumFileSize))
      when(config.authorization).thenReturn("auth-token")
      when(repository.insertFile(fileMetadata)).thenReturn(successful(Some(fileMetaDataCreated)))

      when(upscanConnector.initiate(any[UploadSettings])(any[HeaderCarrier])).thenReturn(successful(initiateResponse))
      when(upscanConnector.upload(any[UpscanTemplate], any[FileWithMetadata]))
        .thenReturn(failed(new Exception("Test")))

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

      when(repository.update(expectedAttachment)).thenReturn(successful(Some(attachmentUpdated)))

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

      when(attachment.withScanResult(scanResult)).thenReturn(attachmentUpdating)
      when(attachment.publishable).thenReturn(true)
      when(attachment.published).thenReturn(false)
      when(attachment.isLive).thenReturn(true)
      when(attachment.id).thenReturn("id")
      when(attachment.fileName).thenReturn(Some("file"))

      when(attachmentUpdating.publishable).thenReturn(true)
      when(attachmentUpdating.published).thenReturn(false)
      when(attachmentUpdating.isLive).thenReturn(true)

      when(attachmentUpdated.publishable).thenReturn(true)
      when(attachmentUpdated.published).thenReturn(false)
      when(attachmentUpdated.isLive).thenReturn(true)
      when(attachmentUpdated.scanStatus).thenReturn(Some(ScanStatus.READY))
      when(attachmentUpdated.id).thenReturn("id")
      when(attachmentUpdated.fileName).thenReturn(Some("file"))

      when(attachmentUploaded.published).thenReturn(true)
      when(attachmentUploaded.publishable).thenReturn(true)
      when(attachmentUploaded.isLive).thenReturn(true)
      when(attachmentUploaded.fileName).thenReturn(Some("file"))
      when(attachmentUploaded.mimeType).thenReturn(Some("mimetype"))
      when(attachmentUploaded.url).thenReturn(Some("url"))
      when(attachmentUploaded.scanStatus).thenReturn(Some(ScanStatus.READY))
      when(attachmentUploaded.copy(published = true, publishable = true)).thenReturn(attachmentUploadedUpdating)

      when(attachmentUploadedUpdated.published).thenReturn(true)
      when(attachmentUploadedUpdated.isLive).thenReturn(true)

      when(repository.update(attachmentUpdating)).thenReturn(successful(Some(attachmentUpdated)))
      when(objectStoreConnector.upload(attachmentUpdated)).thenReturn(attachmentUploaded)
      when(repository.update(attachmentUploadedUpdating)).thenReturn(successful(Some(attachmentUploadedUpdated)))
      when(objectStoreConnector.sign(attachmentUploadedUpdated)).thenReturn(successful(attachmentSigned))

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

        when(attachment.withScanResult(scanResult)).thenReturn(attachmentUpdating)
        when(attachment.publishable).thenReturn(true)
        when(attachment.id).thenReturn("id")
        when(attachment.fileName).thenReturn(Some("file"))

        when(attachmentUpdated.published).thenReturn(true)
        when(attachmentUpdated.scanStatus).thenReturn(Some(ScanStatus.READY))
        when(attachmentUpdated.id).thenReturn("id")
        when(attachmentUpdated.fileName).thenReturn(Some("file"))

        when(repository.update(attachmentUpdating)).thenReturn(successful(None))

        await(service.notify(attachment, scanResult)) shouldBe None

        verify(objectStoreConnector, never()).upload(any[FileMetadata]())(any[HeaderCarrier]())
        verify(objectStoreConnector, never()).sign(any[FileMetadata]())(any[HeaderCarrier]())
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

      when(repository.update(expectedAttachment)).thenReturn(successful(Some(attachmentUpdated)))

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

      when(fileUploading.scanStatus).thenReturn(Some(ScanStatus.READY))
      when(fileUploading.published).thenReturn(false)
      when(fileUploading.isLive).thenReturn(true)
      when(fileUploading.id).thenReturn("id")
      when(fileUploading.fileName).thenReturn(Some("file"))

      when(fileUploaded.copy(published = true)).thenReturn(fileUpdating)

      when(fileUpdated.published).thenReturn(true)

      when(objectStoreConnector.upload(fileUploading)).thenReturn(fileUploaded)
      when(repository.update(any[FileMetadata])).thenReturn(successful(Some(fileUpdated)))
      when(objectStoreConnector.sign(fileUpdated)).thenReturn(Future.successful(fileSigned))

      await(service.publish(fileUploading)) shouldBe Some(fileSigned)

      verify(auditService, times(1)).auditFilePublished(fileId = "id", fileName = "file")
      verifyNoMoreInteractions(auditService)
    }

    "Clear up unpublished expired files" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")

      when(fileUploading.scanStatus).thenReturn(Some(ScanStatus.READY))
      when(fileUploading.published).thenReturn(false)
      when(fileUploading.isLive).thenReturn(false)
      when(fileUploading.id).thenReturn("id")

      when(repository.delete(any[String])(any[ExecutionContext])).thenReturn(successful(()))

      await(service.publish(fileUploading)) shouldBe None

      verify(repository).delete("id")
      verifyNoInteractions(auditService, objectStoreConnector)
    }

    "Not delegate to the File Store if pre published" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")

      when(fileUploading.scanStatus).thenReturn(Some(ScanStatus.READY))
      when(fileUploading.published).thenReturn(true)

      await(service.publish(fileUploading)) shouldBe Some(fileUploading)

      verifyNoInteractions(auditService, objectStoreConnector, repository)
    }

    "Not delegate to the File Store if Scanned UnSafe" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")
      val fileUpdating  = mock(classOf[FileMetadata], "Updating")
      val fileUpdated   = mock(classOf[FileMetadata], "Updated")

      when(fileUploading.scanStatus).thenReturn(Some(ScanStatus.FAILED))
      when(fileUploading.copy(published = true)).thenReturn(fileUpdating)

      when(repository.update(any[FileMetadata])).thenReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)

      verifyNoInteractions(auditService, objectStoreConnector)
    }

    "Not delegate to the File Store if Unscanned" in {
      val fileUploading = mock(classOf[FileMetadata], "Uploading")
      val fileUpdating  = mock(classOf[FileMetadata], "Updating")
      val fileUpdated   = mock(classOf[FileMetadata], "Updated")

      when(fileUploading.scanStatus).thenReturn(None)
      when(fileUploading.copy(published = true)).thenReturn(fileUpdating)

      when(repository.update(any[FileMetadata])).thenReturn(successful(Some(fileUpdated)))

      await(service.publish(fileUploading)) shouldBe Some(fileUpdated)

      verifyNoInteractions(auditService, objectStoreConnector)
    }
  }

}
