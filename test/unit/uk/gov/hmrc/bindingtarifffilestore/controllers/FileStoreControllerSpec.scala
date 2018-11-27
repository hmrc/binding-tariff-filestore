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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import java.io.File
import java.time.Instant

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.then
import org.mockito.Mockito
import org.mockito.Mockito.{never, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadDetails}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class FileStoreControllerSpec extends UnitSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val service = mock[FileStoreService]
  private val controller = new FileStoreController(service)

  private val fakeRequest = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(service)
  }

  "Get By ID" should {
    "return 200 when found" in {
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      when(service.getById("id")).thenReturn(successful(Some(attachment)))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe Status.OK
      bodyOf(result) shouldEqual Json.toJson(attachment).toString()
    }

    "return 404 when not found" in {
      when(service.getById("id")).thenReturn(successful(None))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "Notify" should {
    "return 201 when found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      val attachmentUpdated = FileMetadata(id="id", fileName = "file", mimeType = "type", url = Some("url"))
      when(service.getById("id")).thenReturn(successful(Some(attachment)))
      when(service.notify(attachment, scanResult)).thenReturn(successful(Some(attachmentUpdated)))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result = await(controller.notification("id")(request))

      status(result) shouldBe Status.CREATED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      when(service.getById("id")).thenReturn(successful(None))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result = await(controller.notification("id")(request))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "Publish" should {
    "return 201 when found" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY))
      val attachmentUpdated = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY), url = Some("url"))
      when(service.getById("id")).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(attachmentExisting)).thenReturn(successful(attachmentUpdated))

      val result: Result = await(controller.publish("id")(fakeRequest))

      status(result) shouldBe Status.ACCEPTED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 403 when invalid status" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.FAILED))
      when(service.getById("id")).thenReturn(successful(Some(attachmentExisting)))

      val result: Result = await(controller.publish("id")(fakeRequest))

      then(service).should(never()).publish(any[FileMetadata])
      status(result) shouldBe Status.FORBIDDEN
    }

    "return 403 when unscanned" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = None)
      when(service.getById("id")).thenReturn(successful(Some(attachmentExisting)))

      val result: Result = await(controller.publish("id")(fakeRequest))

      then(service).should(never()).publish(any[FileMetadata])
      status(result) shouldBe Status.FORBIDDEN
    }

    "return 404 when not found" in {
      when(service.getById("id")).thenReturn(successful(None))

      val result: Result = await(controller.publish("id")(fakeRequest))

      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "Upload" should {
    "return 202 on valid file" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = "name", mimeType = "text/plain")
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When
      val file = new File("test/unit/resources/file.txt")
      val filePart = FilePart[TemporaryFile](key = "file", "file.txt", contentType = Some("text/plain"), ref = TemporaryFile(file))
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(fakeRequest.withBody(form)))

      // Then
      file exists() shouldBe true
      status(result) shouldBe Status.ACCEPTED
    }

    "return 400 on missing file" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = "name", mimeType = "text/plain")
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(), badParts = Seq.empty)

      val result: Result = await(controller.upload(fakeRequest.withBody(form)))

      // Then
      status(result) shouldBe Status.BAD_REQUEST
    }

  }
}
