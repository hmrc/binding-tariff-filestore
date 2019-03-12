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
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import play.api.http.Status._
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContent, MultipartFormData, Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST.format
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadDetails}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.http.{HeaderCarrier, HttpVerbs}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future.{failed, successful}

class FileStoreControllerSpec extends UnitSpec with Matchers
  with WithFakeApplication with MockitoSugar with BeforeAndAfterEach {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val appConfig = mock[AppConfig]
  private val service = mock[FileStoreService]
  private val controller = new FileStoreController(appConfig, service)

  private val fakeRequest = FakeRequest()

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(service)
  }

  "Delete All" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/file")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)

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

  "Delete By ID" should {

    val id = "ABC-123_000"
    val req = FakeRequest(method = HttpVerbs.DELETE, path = s"/file/$id")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)

      val result = await(controller.delete(id)(req))

      status(result) shouldEqual FORBIDDEN
      jsonBodyOf(result).toString() shouldEqual s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(service.delete(id)).thenReturn(successful((): Unit))

      val result = await(controller.delete(id)(req))

      status(result) shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(service.delete(id)).thenReturn(failed(error))

      val result = await(controller.delete(id)(req))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "Get By ID" should {
    "return 200 when found" in {
      val attachment = FileMetadata(id="id", fileName = "file", mimeType = "type")
      when(service.getById(id = "id")).thenReturn(successful(Some(attachment)))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(attachment).toString()
    }

    "return 404 when not found" in {
      when(service.getById(id = "id")).thenReturn(successful(None))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Get By IDs" should {
    "return 200 with empty array when empty id array are provided" in {
      when(service.getByIds(Seq.empty)).thenReturn(successful(Seq.empty))

      val result = await(controller.getAll(Some(Seq.empty))(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq.empty).toString()
    }

    "return 200 with empty array when no ids are provided" in {
      when(service.getByIds(Seq.empty)).thenReturn(successful(Seq.empty))

      val result = await(controller.getAll(None)(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq.empty).toString()
    }

    "return 200 with file metadata array when ids are provided" in {
      val attachment1 = FileMetadata(id = "id1", fileName = "file1", mimeType = "type1")
      val attachment2 = FileMetadata(id = "id2", fileName = "file2", mimeType = "type2")

      when(service.getByIds(ids = Seq("id1", "id2"))).thenReturn(successful(Seq(attachment1, attachment2)))

      val result = await(controller.getAll(Some(Seq("id1", "id2")))(fakeRequest))

      status(result) shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq(attachment1, attachment2)).toString()
    }
  }

  "Notify" should {
    "return 201 when found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      val attachment = FileMetadata(id = "id", fileName = "file", mimeType = "type")
      val attachmentUpdated = FileMetadata(id = "id", fileName = "file", mimeType = "type", url = Some("url"))
      when(service.getById(id = "id")).thenReturn(successful(Some(attachment)))
      when(service.notify(refEq(attachment), refEq(scanResult))(any[HeaderCarrier])).thenReturn(successful(Some(attachmentUpdated)))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result = await(controller.notification(id = "id")(request))

      status(result) shouldBe CREATED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      val scanResult = SuccessfulScanResult("ref", "url", UploadDetails(Instant.now(), "checksum"))
      when(service.getById("id")).thenReturn(successful(None))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result = await(controller.notification(id = "id")(request))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Publish" should {
    "return 201 when found" in {
      val attachmentExisting = FileMetadata(id = "id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY))
      val attachmentUpdated = FileMetadata(id = "id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY), url = Some("url"))
      when(service.getById(id = "id")).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(refEq(attachmentExisting))(any[HeaderCarrier])).thenReturn(successful(Some(attachmentUpdated)))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result) shouldBe ACCEPTED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      when(service.getById(id = "id")).thenReturn(successful(None))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }

    "return 404 when publish returns not found" in {
      val attachmentExisting = FileMetadata(id="id", fileName = "file", mimeType = "type", scanStatus = Some(ScanStatus.READY))
      when(service.getById(id = "id")).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(refEq(attachmentExisting))(any[HeaderCarrier])).thenReturn(successful(None))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Upload" should {

    val fileName = "file.txt"
    val mimeType = "text/plain"
    val tmpFile = TemporaryFile("example-file.txt")

    def multipartRequest(body: MultipartFormData[TemporaryFile]): Request[AnyContent] = {
      fakeRequest.withMultipartFormDataBody(body)
        .withHeaders("Content-Type" -> "multipart/form-data")
    }

    def jsonRequest[T](body: T)(implicit writes: Writes[T]): Request[AnyContent] = {
      fakeRequest.withJsonBody(Json.toJson(body))
        .withHeaders("Content-Type" -> "application/json")
    }

    "return 202 on valid json" in {
      // Given
      val response = UploadTemplate("id", "href", Map())
      when(service.initiate(any[FileMetadata])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request = UploadRequest(fileName = "file.txt", mimeType = "text/plain", published = true)
      val result: Result = await(controller.upload(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileInitiated
      metadata.published shouldBe true
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "return 202 on valid json with ID" in {
      // Given
      val response = UploadTemplate("id", "href", Map())
      when(service.initiate(any[FileMetadata])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request = UploadRequest(id = Some("id"), fileName = "file.txt", mimeType = "text/plain", published = true)
      val result: Result = await(controller.upload(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileInitiated
      metadata.id shouldBe "id"
      metadata.published shouldBe true
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "return 202 on valid file" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = fileName, mimeType = mimeType)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.published shouldBe false
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "return 202 on valid file with id" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = fileName, mimeType = mimeType)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("id" -> Seq("id")), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.id shouldBe "id"
      metadata.published shouldBe false
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "return 202 on valid file with publish=true" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = "name", mimeType = mimeType, published = true)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When=
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("publish" -> Seq("true")), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.published shouldBe true
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "return 202 on valid file with publish=false" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = "name", mimeType = mimeType, published = true)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(metadataUploaded))

      // When=
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map("publish" -> Seq("false")), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.published shouldBe false
      metadata.fileName shouldBe "file.txt"
      metadata.mimeType shouldBe "text/plain"
    }

    "Throw exception on missing mime type" in {
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = None, ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val exception = intercept[RuntimeException] {
        controller.upload(multipartRequest(form))
      }
      exception.getMessage shouldBe "Missing file type"
    }

    "return 400 on missing file" in {
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 on missing filename" in {
      val filePart = FilePart[TemporaryFile](key = "file", filename = "", contentType = Some("text/plain"), ref = tmpFile)
      val form = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 on invalid json" in {
      // When
      val result: Result = await(controller.upload(jsonRequest(Json.obj())))

      // Then
      status(result) shouldBe BAD_REQUEST
    }

    "return 400 on missing content type" in {
      val result: Result = await(controller.upload(FakeRequest()))

      status(result) shouldBe BAD_REQUEST
    }

    def theFileUploaded: FileWithMetadata = {
      val captor = ArgumentCaptor.forClass(classOf[FileWithMetadata])
      verify(service).upload(captor.capture())(any[HeaderCarrier])
      captor.getValue
    }

    def theFileInitiated: FileMetadata = {
      val captor = ArgumentCaptor.forClass(classOf[FileMetadata])
      verify(service).initiate(captor.capture())(any[HeaderCarrier])
      captor.getValue
    }

  }

}
