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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import java.time.Instant

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST.format
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadDetails}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata, ScanStatus}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.http.{HeaderCarrier, HttpVerbs}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.{failed, successful}

class FileStoreControllerSpec extends UnitSpec with Matchers
  with GuiceOneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val appConfig = mock[AppConfig]
  private val service = mock[FileStoreService]
  private val controller = new FileStoreController(appConfig, service)

  private val fakeRequest = FakeRequest()

  override protected def afterEach(): Unit = {
    super.beforeEach()
    Mockito.reset(service)
  }

  "deleteAll()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/cases")

    "return 403 if the test mode is disabled" in {

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual FORBIDDEN
      jsonBodyOf(result).toString() shouldEqual s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(service.deleteAll()).thenReturn(successful(()))

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(service.deleteAll()).thenReturn(failed(error))

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "Get By ID" should {
    "return 200 when found" in {
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      when(service.getById("id")).thenReturn(successful(Some(attachment)))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(attachment).toString()
    }

    "return 404 when not found" in {
      when(service.getById("id")).thenReturn(successful(None))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Get By IDs" should {
    "return 200 with empty array when empty id array are provided" in {
      when(service.getByIds(Seq.empty)).thenReturn(successful(Seq.empty))

      val result = await(controller.getFiles(Some(Seq.empty))(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq.empty).toString()
    }

    "return 200 with empty array when no ids are provided" in {
      when(service.getByIds(Seq.empty)).thenReturn(successful(Seq.empty))

      val result = await(controller.getFiles(None)(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq.empty).toString()
    }

    "return 200 with file metadata array when ids are provided" in {
      val attachment1 = FileMetadata(id="id1", fileName = "file1", mimeType = "type1")
      val attachment2 = FileMetadata(id="id2", fileName = "file2", mimeType = "type2")

      when(service.getByIds(Seq("id1", "id2"))).thenReturn(successful(Seq(attachment1, attachment2)))

      val result = await(controller.getFiles(Some(Seq("id1", "id2")))(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq(attachment1, attachment2)).toString()
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

      status(result) shouldBe CREATED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      when(service.getById("id")).thenReturn(successful(None))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result = await(controller.notification("id")(request))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Publish" should {
    "return 201 when found" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY))
      val attachmentUpdated = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY), url = Some("url"))
      when(service.getById("id")).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(attachmentExisting)).thenReturn(successful(Some(attachmentUpdated)))

      val result: Result = await(controller.publish("id")(fakeRequest))

      status(result) shouldBe ACCEPTED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      when(service.getById("id")).thenReturn(successful(None))

      val result: Result = await(controller.publish("id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }

    "return 404 when publish returns not found" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY))
      when(service.getById("id")).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(attachmentExisting)).thenReturn(successful(None))

      val result: Result = await(controller.publish("id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Upload" should {

    val fileName = "file.txt"

    "return 202 on valid file" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = "name", mimeType = "text/plain")
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When=
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some("text/plain"), ref = TemporaryFile("example-file.txt"))
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(fakeRequest.withBody(form)))

      // Then
      status(result) shouldBe ACCEPTED
    }

    "Throw exception on missing mime type" in {
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = None, ref = TemporaryFile("example-file.txt"))
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val exception = intercept[RuntimeException] {
        controller.upload(fakeRequest.withBody(form))
      }
      exception.getMessage shouldBe "Missing file type"
    }

    "return 400 on missing file" in {
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(), badParts = Seq.empty)

      val result: Result = await(controller.upload(fakeRequest.withBody(form)))

      status(result) shouldBe BAD_REQUEST
    }

  }
}
