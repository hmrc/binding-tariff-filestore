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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import com.mongodb.{MongoWriteException, ServerAddress, WriteError}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito._
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import play.api.http.Status._
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST.format
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2.{FileStoreInitiateRequest, FileStoreInitiateResponse, UpscanFormTemplate}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult, UploadDetails}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.bindingtarifffilestore.util._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class FileStoreControllerSpec extends UnitSpec with Matchers with WithFakeApplication with BeforeAndAfterEach {

  private val appConfig: AppConfig                  = mock(classOf[AppConfig])
  private val service: FileStoreService             = mock(classOf[FileStoreService])
  private lazy val playBodyParsers: PlayBodyParsers = mock(classOf[PlayBodyParsers])
  lazy val cc: MessagesControllerComponents         = fakeApplication.injector.instanceOf[MessagesControllerComponents]
  private val controller: FileStoreController       = new FileStoreController(appConfig, service, playBodyParsers, cc)
  implicit val hc: HeaderCarrier                    = mock(classOf[HeaderCarrier])

  private val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest()

  private def jsonRequest[T](body: T)(implicit writes: Writes[T]): Request[AnyContent] =
    fakeRequest
      .withJsonBody(Json.toJson(body))
      .withHeaders("Content-Type" -> "application/json")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(service)
  }

  "Get By ID" should {
    "return 200 when found" in {
      val id         = "id"
      val attachment = FileMetadata(id = id, fileName = Some("file"), mimeType = Some("type"))

      when(appConfig.isTestMode).thenReturn(true)
      when(service.find(eqTo(id))(any[HeaderCarrier])).thenReturn(Future.successful(Some(attachment)))

      val result = await(controller.get(id)(fakeRequest))

      status(result)    shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(attachment).toString()
    }

    "return 404 when not found" in {
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(None))

      val result = await(controller.get("id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Get By Search" should {
    "return 200 with empty array" in {
      when(service.find(eqTo(Search(ids = Some(Set.empty))), eqTo(Pagination.max))(any[HeaderCarrier]))
        .thenReturn(successful(Paged.empty[FileMetadata]))

      val result = await(controller.getAll(Search(ids = Some(Set.empty)), None)(fakeRequest))

      status(result)    shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq.empty).toString()
    }

    "return 200 with non empty array" in {
      val attachment1 = FileMetadata(id = "id1", fileName = Some("file1"), mimeType = Some("type1"))
      val attachment2 = FileMetadata(id = "id2", fileName = Some("file2"), mimeType = Some("type2"))

      when(service.find(eqTo(Search(ids = Some(Set("id1", "id2")))), eqTo(Pagination.max))(any[HeaderCarrier]))
        .thenReturn(successful(Paged(Seq(attachment1, attachment2))))

      val result = await(controller.getAll(Search(ids = Some(Set("id1", "id2"))), None)(fakeRequest))

      status(result)    shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Seq(attachment1, attachment2)).toString()
    }

    "return 200 with pagination and non empty pager" in {
      val attachment1 = FileMetadata(id = "id1", fileName = Some("file1"), mimeType = Some("type1"))
      val attachment2 = FileMetadata(id = "id2", fileName = Some("file2"), mimeType = Some("type2"))

      when(service.find(eqTo(Search(ids = Some(Set("id1", "id2")))), eqTo(Pagination()))(any[HeaderCarrier]))
        .thenReturn(successful(Paged(Seq(attachment1, attachment2))))

      val result = await(controller.getAll(Search(ids = Some(Set("id1", "id2"))), Some(Pagination()))(fakeRequest))

      status(result)    shouldBe OK
      bodyOf(result) shouldEqual Json.toJson(Paged(Seq(attachment1, attachment2))).toString()
    }
  }

  "Delete All" should {

    val req = FakeRequest(method = "DELETE", path = "/file")

    "return 403 if the test mode is disabled" in {

      when(appConfig.isTestMode).thenReturn(false)

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual FORBIDDEN
      jsonBodyOf(result)
        .toString()  shouldEqual s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      val attachment = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"))

      when(appConfig.isTestMode).thenReturn(true)
      when(service.deleteAll()).thenReturn(successful(()))
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(Some(attachment))

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(service.deleteAll()).thenReturn(Future.failed(error))

      val result = await(controller.deleteAll()(req))

      status(result)                shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "Delete By ID" should {

    val id  = "ABC-123_000"
    val req = FakeRequest(method = "DELETE", path = s"/file/$id")

    "return 204" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(service.delete(id)).thenReturn(Future.successful((): Unit))

      val result = await(controller.delete(id)(req))

      status(result) shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(service.delete(id)).thenReturn(Future.failed(error))

      val result = await(controller.delete(id)(req))

      status(result)                shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "Notify" should {
    "return 201 when found" in {
      val scanResult        = SuccessfulScanResult(
        reference = "ref",
        downloadUrl = "url",
        uploadDetails = UploadDetails("file", "type", Instant.now(), "checksum")
      )
      val attachment        = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"))
      val attachmentUpdated =
        FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"), url = Some("url"))
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(Some(attachment)))
      when(service.notify(refEq(attachment), refEq(scanResult))(any[HeaderCarrier]))
        .thenReturn(successful(Some(attachmentUpdated)))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result                = await(controller.notification(id = "id")(request))

      status(result)     shouldBe CREATED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      val scanResult = SuccessfulScanResult(
        reference = "ref",
        downloadUrl = "url",
        uploadDetails = UploadDetails("file", "type", Instant.now(), "checksum")
      )
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(None))

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result                = await(controller.notification(id = "id")(request))

      status(result) shouldBe NOT_FOUND
    }

    "return 409 when a MongoWriteException occurred" in {
      val code: Int                        = 11000
      val scanResult: SuccessfulScanResult =
        SuccessfulScanResult(
          reference = "ref",
          downloadUrl = "url",
          uploadDetails = UploadDetails("file", "type", Instant.now(), "checksum")
        )

      val attachment: FileMetadata = FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"))
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(Some(attachment)))
      when(service.notify(refEq(attachment), refEq(scanResult))(any[HeaderCarrier]))
        .thenThrow(
          new MongoWriteException(
            new WriteError(code, "", new BsonDocument()),
            new ServerAddress(),
            Collections.emptySet
          )
        )

      val request: FakeRequest[JsValue] = fakeRequest.withBody(Json.toJson[ScanResult](scanResult))
      val result: Result                = await(controller.notification(id = "id")(request))

      status(result)                shouldEqual CONFLICT
      jsonBodyOf(result).toString() shouldEqual """{"code":"CONFLICT","message":"Entity already exists"}"""
    }

    "return 400" when {
      "the body is an invalid JSON" in {
        val result: Result = await(controller.notification(id = "id")(fakeRequest.withBody(Json.obj())))

        status(result) shouldBe BAD_REQUEST
      }

      "the body cannot be parsed" in {
        val result: Result = await(controller.notification(id = "id")(fakeRequest.withBody(None.orNull)))

        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "Publish" should {
    "return 201 when found" in {
      val attachmentExisting =
        FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"), scanStatus = Some(ScanStatus.READY))
      val attachmentUpdated  = FileMetadata(
        id = "id",
        fileName = Some("file"),
        mimeType = Some("type"),
        scanStatus = Some(ScanStatus.READY),
        url = Some("url")
      )
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(refEq(attachmentExisting))(any[HeaderCarrier]))
        .thenReturn(successful(Some(attachmentUpdated)))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result)     shouldBe ACCEPTED
      jsonBodyOf(result) shouldBe Json.toJson(attachmentUpdated)
    }

    "return 404 when not found" in {
      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(None))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }

    "return 404 when publish returns not found" in {
      val attachmentExisting =
        FileMetadata(id = "id", fileName = Some("file"), mimeType = Some("type"), scanStatus = Some(ScanStatus.READY))

      when(service.find(eqTo("id"))(any[HeaderCarrier])).thenReturn(successful(Some(attachmentExisting)))
      when(service.publish(refEq(attachmentExisting))(any[HeaderCarrier])).thenReturn(Future.successful(None))

      val result: Result = await(controller.publish(id = "id")(fakeRequest))

      status(result) shouldBe NOT_FOUND
    }
  }

  "Initiate" should {
    "return 202 on valid json" in {
      // Given
      val response = FileStoreInitiateResponse("id", "ref", UpscanFormTemplate("href", Map()))
      when(service.initiateV2(any[FileStoreInitiateRequest])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request        = FileStoreInitiateRequest(publishable = true)
      val result: Result = await(controller.initiate(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED
    }

    "return 202 on valid json with ID" in {
      // Given
      val response = FileStoreInitiateResponse("id", "ref", UpscanFormTemplate("href", Map()))
      when(service.initiateV2(any[FileStoreInitiateRequest])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request        = FileStoreInitiateRequest(id = Some("id"), publishable = true)
      val result: Result = await(controller.initiate(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED
    }

    "return 400" when {
      "the body is an invalid JSON" in {
        val result: Result = await(controller.initiate(jsonRequest(Json.obj())))

        status(result) shouldBe BAD_REQUEST
      }

      "the body cannot be parsed" in {
        val result: Result = await(controller.initiate(fakeRequest.withBody(None.orNull)))

        status(result) shouldBe BAD_REQUEST
      }

      "no body is supplied" in {
        val result: Result = await(controller.initiate(fakeRequest))

        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "Upload" should {

    val fileName = "file.txt"
    val mimeType = "text/plain"
    val tmpFile  = SingletonTemporaryFileCreator.create("example-file.txt")

    def multipartRequest(body: MultipartFormData[TemporaryFile]): Request[AnyContent] =
      fakeRequest
        .withMultipartFormDataBody(body)
        .withHeaders("Content-Type" -> "multipart/form-data")

    "return 202 on valid json" in {
      // Given
      val response = UploadTemplate("id", "href", Map())
      when(service.initiate(any[FileMetadata])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request        = UploadRequest(fileName = "file.txt", mimeType = "text/plain", publishable = true)
      val result: Result = await(controller.upload(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileInitiated
      metadata.publishable shouldBe true
      metadata.fileName    shouldBe Some("file.txt")
      metadata.mimeType    shouldBe Some("text/plain")
    }

    "return 202 on valid json with ID" in {
      // Given
      val response = UploadTemplate("id", "href", Map())
      when(service.initiate(any[FileMetadata])(any[HeaderCarrier])).thenReturn(successful(response))

      // When
      val request        = UploadRequest(id = Some("id"), fileName = "file.txt", mimeType = "text/plain", publishable = true)
      val result: Result = await(controller.upload(jsonRequest(request)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileInitiated
      metadata.id          shouldBe "id"
      metadata.publishable shouldBe true
      metadata.fileName    shouldBe Some("file.txt")
      metadata.mimeType    shouldBe Some("text/plain")
    }

    "return 202 on valid file" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = Some(fileName), mimeType = Some(mimeType))
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(Some(metadataUploaded)))

      // When
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.published shouldBe false
      metadata.fileName  shouldBe Some("file.txt")
      metadata.mimeType  shouldBe Some("text/plain")
    }

    "return 202 on valid file with id" in {
      // Given
      val metadataUploaded = FileMetadata(id = "id", fileName = Some(fileName), mimeType = Some(mimeType))
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(Some(metadataUploaded)))

      // When
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](
        dataParts = Map("id" -> Seq("id")),
        files = Seq(filePart),
        badParts = Seq.empty
      )

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.id        shouldBe "id"
      metadata.published shouldBe false
      metadata.fileName  shouldBe Some("file.txt")
      metadata.mimeType  shouldBe Some("text/plain")
    }

    "return 202 on valid file with publish=true" in {
      // Given
      val metadataUploaded =
        FileMetadata(id = "id", fileName = Some("name"), mimeType = Some(mimeType), published = true)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(Some(metadataUploaded)))

      // When=
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](
        dataParts = Map("publish" -> Seq("true")),
        files = Seq(filePart),
        badParts = Seq.empty
      )

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.publishable shouldBe true
      metadata.fileName    shouldBe Some("file.txt")
      metadata.mimeType    shouldBe Some("text/plain")
    }

    "return 202 on valid file with publish=false" in {
      // Given
      val metadataUploaded =
        FileMetadata(id = "id", fileName = Some("name"), mimeType = Some(mimeType), published = true)
      when(service.upload(any[FileWithMetadata])(any[HeaderCarrier])).thenReturn(successful(Some(metadataUploaded)))

      // When=
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = Some(mimeType), ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](
        dataParts = Map("publish" -> Seq("false")),
        files = Seq(filePart),
        badParts = Seq.empty
      )

      val result: Result = await(controller.upload(multipartRequest(form)))

      // Then
      status(result) shouldBe ACCEPTED

      val metadata = theFileUploaded.metadata
      metadata.published shouldBe false
      metadata.fileName  shouldBe Some("file.txt")
      metadata.mimeType  shouldBe Some("text/plain")
    }

    "Throw exception on missing mime type" in {
      val filePart = FilePart[TemporaryFile](key = "file", fileName, contentType = None, ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

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
      val filePart =
        FilePart[TemporaryFile](key = "file", filename = "", contentType = Some("text/plain"), ref = tmpFile)
      val form     = MultipartFormData[TemporaryFile](dataParts = Map(), files = Seq(filePart), badParts = Seq.empty)

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

    "return 400 on missing multipart form data body when content type is multipart/form-data" in {
      val result: Result = await(
        controller.upload(
          fakeRequest
            .withHeaders("Content-Type" -> "multipart/form-data")
        )
      )

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
