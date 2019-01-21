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

package uk.gov.hmrc.bindingtarifffilestore

import java.io.{File, InputStream}
import java.net.URI
import java.nio.file.Files
import java.time.Instant

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.io.IOUtils
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.{ContentTypes, HeaderNames, HttpVerbs, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import scalaj.http.{Http, HttpResponse, MultiPart}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult.format
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockFeatureTestServer}

import scala.collection.Map
import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class FileStoreSpec extends WiremockFeatureTestServer with ResourceFiles with BeforeAndAfterEach {

  override lazy val port = 14681

  private val timeout: FiniteDuration = 2.seconds
  private val serviceUrl = s"http://localhost:$port"

  private val filePath = "test/resources/file.txt"

  private lazy val fileStore: FileMetadataMongoRepository = app.injector.instanceOf[FileMetadataMongoRepository]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    dropFileStore()
    ensureFileStoreIndexes()
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "s3.endpoint" -> s"http://localhost:$wirePort",
      "microservice.services.upscan-initiate.port" -> s"$wirePort"
    )
    .build()

  feature("Delete All") {
    scenario("Clear Collection") {
      Given("There are some documents in the collection")
      upload("some-file1.txt", "text/plain")
      upload("some-file2.txt", "text/plain")
      fileStoreSize shouldBe 2
      stubS3ListAll()
      stubS3DeleteAll()

      When("I delete all documents")
      val deleteResult = Http(s"$serviceUrl/file")
        .method(HttpVerbs.DELETE)
        .asString

      Then("The response code should be 204")
      deleteResult.code shouldEqual Status.NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      fileStoreSize shouldBe 0
    }

  }

  feature("Upload") {
    scenario("Should persist") {
      Given("A Client of the FileStore has a file")
      val filename = "some-file.txt"
      val contentType = "text/plain"

      When("It is uploaded")
      val response: HttpResponse[Map[String, JsValue]] = upload(filename, contentType)

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString(filename)
      response.body("mimeType") shouldBe JsString(contentType)
      response.body.contains("url") shouldBe false
      response.body.contains("scanStatus") shouldBe false
    }
  }

  feature("Get") {
    scenario("Should show the file is persisted") {
      Given("A file has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("I request the file details")
      val response = getFile(id)

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body.contains("url") shouldBe false
      response.body.contains("scanStatus") shouldBe false
    }
  }

  feature("Get files") {
    scenario("Should show the files are persisted") {
      Given("Files have been uploaded")
      val id1 = upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      val id2 = upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response = getFiles(id1, id2)

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")

      response.body.asInstanceOf[JsArray].value.size shouldBe 2
      (response.body \\ "fileName").map(_.as[String])  should contain only ("some-file1.txt", "some-file2.txt")
    }

    scenario("Should return all files where no ids are provided") {
      Given("Files have been uploaded")

      upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response =  Http(s"$serviceUrl/file").method(HttpVerbs.GET).execute(convertingArrayResponseToJS)

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")

      (response.body \\ "fileName").map(_.as[String]) should contain allOf ("some-file1.txt", "some-file2.txt")
    }
  }

  feature("Notify") {
    scenario("Successful scan should update the status") {
      Given("A File has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("Notify is Called")
      val uri = new File(filePath).toURI
      val response = notifySuccess(id, uri)

      Then("The response code should be Created")
      response.code shouldBe Status.CREATED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body("url") shouldBe JsString(uri.toString)

      And("The response shows the file is marked as safe")
      response.body("scanStatus") shouldBe JsString("READY")
    }

    scenario("Quarantined scan should update the status") {
      Given("A File has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("Notify is Called")
      val response = notifyFailure(id)

      Then("The response code should be Created")
      response.code shouldBe Status.CREATED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body.contains("url") shouldBe false

      And("The response shows the file is marked as quarantined")
      response.body("scanStatus") shouldBe JsString("FAILED")
    }
  }

  feature("Publish") {
    scenario("Should persist the file to permanent storage") {
      Given("A File has been uploaded and marked as safe")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      notifySuccess(id)

      When("It is Published")
      val response = publishSafeFile(id)

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body("scanStatus") shouldBe JsString("READY")
      response.body("published") shouldBe JsBoolean(true)

      And("The response shows the file published")
      response.body("url").as[JsString].value should include(s"$id?X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    scenario("Should mark an un-safe file as published, but not persist") {
      Given("A File has been uploaded and marked as quarantined")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      notifyFailure(id)

      When("It is Published")
      val publishResponse = publishUnSafeFile(id)

      Then("The response code should be Forbidden")
      publishResponse.code shouldBe Status.ACCEPTED

      And("The response body contains the file details")
      publishResponse.body("fileName") shouldBe JsString("some-file.txt")
      publishResponse.body("mimeType") shouldBe JsString("text/plain")
      publishResponse.body("scanStatus") shouldBe JsString("FAILED")
      publishResponse.body("published") shouldBe JsBoolean(true)

      And("I can call GET and see the file is unpublished")
      val getResponse = getFile(id)
      getResponse.code shouldBe Status.OK
      getResponse.body("fileName") shouldBe JsString("some-file.txt")
      getResponse.body("mimeType") shouldBe JsString("text/plain")
      getResponse.body("scanStatus") shouldBe JsString("FAILED")
      getResponse.body("published") shouldBe JsBoolean(true)
      getResponse.body.contains("url") shouldBe false
    }
  }

  private def getFile(id: String): HttpResponse[Map[String, JsValue]] = {
    Http(s"$serviceUrl/file/$id")
      .method(HttpVerbs.GET)
      .execute(convertingResponseToJS)
  }

  private def getFiles(ids: String*): HttpResponse[JsValue] = {

    val queryParams = ids.map(id => s"id=$id").mkString("&")

    Http(s"$serviceUrl/file?$queryParams")
      .method(HttpVerbs.GET)
      .execute(convertingArrayResponseToJS)
  }

  private def publishSafeFile(id: String): HttpResponse[Map[String, JsValue]] = {
    stubS3Upload(id)
    Http(s"$serviceUrl/file/$id/publish")
      .method(HttpVerbs.POST)
      .execute(convertingResponseToJS)
  }

  private def publishUnSafeFile(id: String): HttpResponse[Map[String, JsValue]] = {
    // Should NOT call S3 Upload
    Http(s"$serviceUrl/file/$id/publish")
      .method(HttpVerbs.POST)
      .execute(convertingResponseToJS)
  }

  private def notifySuccess(id: String, uri: URI = new File(filePath).toURI): HttpResponse[Map[String, JsValue]] = {
    val url = uri.toURL.toString
    val model = SuccessfulScanResult("reference", url, UploadDetails(Instant.now(), "checksum"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson(model).toString())
      .header(HeaderNames.CONTENT_TYPE, ContentTypes.JSON)
      .execute(convertingResponseToJS)
  }

  private def notifyFailure(id: String): HttpResponse[Map[String, JsValue]] = {
    val model = FailedScanResult("reference", FailureDetails(FailureReason.QUARANTINED, "message"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson(model).toString())
      .header(HeaderNames.CONTENT_TYPE, ContentTypes.JSON)
      .execute(convertingResponseToJS)
  }

  private def upload(filename: String, contentType: String): HttpResponse[Map[String, JsValue]] = {
    stubUpscanInitiate
    stubUpscanUpload
    val form = MultiPart(
      "file",
      filename,
      contentType,
      Files.readAllBytes(TemporaryFile(filename).file.toPath)
    )
    Http(s"$serviceUrl/file")
      .postMulti(form)
      .execute(convertingResponseToJS)
  }

  private def stubUpscanUpload = {
    stubFor(
      post("/upscan/upload")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )
  }

  private def stubUpscanInitiate = {
    stubFor(
      post("/upscan/initiate")
        .willReturn(
          aResponse()
            .withBody(fromFile("upscan/initiate_wiremock-response.json"))
        )
    )
  }

  private def stubS3Upload(id: String) = {
    stubFor(
      put(s"/digital-tariffs-local/$id")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )
  }

  private def stubS3ListAll() = {
    stubFor(
      get(s"/digital-tariffs-local/?encoding-type=url")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(fromFile("aws/list-objects_response.xml"))
        )
    )
  }

  private def stubS3DeleteAll() = {
    stubFor(
      post(s"/digital-tariffs-local/?delete")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(fromFile("aws/delete-objects_response.xml"))
        )
    )
  }

  private def convertingResponseToJS: InputStream => Map[String, JsValue] = { is =>
    val body = IOUtils.toString(is)
    Try(Json.parse(body))
      .map(_.as[JsObject].value)
      .getOrElse(throw new AssertionError(s"The response was not valid JSON object:\n $body"))
  }

  private def convertingArrayResponseToJS: InputStream => JsValue = { is =>
    val body = IOUtils.toString(is)
    Try(Json.parse(body))
      .getOrElse(throw new AssertionError(s"The response was not valid JSON array:\n $body"))
  }

  private def fileStoreSize: Int = {
    result(fileStore.collection.count(), timeout)
  }

  private def dropFileStore(): Unit = {
    result(fileStore.drop, timeout)
  }

  private def ensureFileStoreIndexes(): Unit = {
    result(fileStore.ensureIndexes, timeout)
  }

}
