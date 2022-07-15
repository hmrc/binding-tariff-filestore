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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.io.IOUtils
import play.api.Application
import play.api.http.{ContentTypes, HeaderNames, HttpVerbs, Status}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json._
import scalaj.http.{Http, HttpResponse, MultiPart}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.{Pagination, Search, UploadRequest}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockFeatureTestServer}

import java.io.{File, InputStream}
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.time.Instant
import scala.collection.JavaConverters._
import scala.collection.Map
import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class FileStoreSpec extends WiremockFeatureTestServer with ResourceFiles {

  private val timeout: FiniteDuration = 2.seconds
  private val serviceUrl = s"http://localhost:$port"

  private val filePath = "test/resources/file.txt"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    repository.deleteAll()
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "s3.endpoint" -> s"http://localhost:$wirePort",
      "microservice.services.upscan-initiate.port" -> s"$wirePort"
    )
    .overrides(bind[FileMetadataMongoRepository].to(repository))
    .build()

  Feature("Delete All") {
    Scenario("Clear collections & files") {
      Given("There are some documents in the collection")
      upload("some-file1.txt", "text/plain")
      upload("some-file2.txt", "text/plain")
      dbFileStoreSize shouldBe 2
      stubS3ListAll()
      stubS3DeleteAll()

      When("I delete all documents")
      val deleteResult = Http(s"$serviceUrl/file")
        .header(apiTokenKey, appConfig.authorization)
        .method(HttpVerbs.DELETE)
        .asString

      Then("The response code should be 204")
      deleteResult.code shouldEqual Status.NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      dbFileStoreSize shouldBe 0

      And("the are no files")
      val files = Http(s"$serviceUrl/file")
        .header(apiTokenKey, appConfig.authorization)
        .method(HttpVerbs.GET)
        .execute(convertingArrayResponseToJS)
      files.code shouldBe 200
      files.body.toString() shouldBe "[]"
    }

  }

  Feature("Delete") {
    Scenario("Delete the file") {
      Given("A file has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      dbFileStoreSize shouldBe 1

      When("I request the file details")
      val response = deleteFile(id)

      Then("The response code should be Ok")
      response.code shouldBe Status.NO_CONTENT

      And("The response body is empty")
      response.body shouldBe ""

      And("No documents exist in the mongo collection")
      dbFileStoreSize shouldBe 0
    }
  }

  Feature("Upload") {
    Scenario("Should persist") {
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

  Feature("Initiate") {
    Scenario("Should persist") {
      Given("A Client of the FileStore has a file")
      val filename = "some-file.txt"
      val contentType = "text/plain"

      When("It is initiated")
      val response: HttpResponse[Map[String, JsValue]] = initiate(filename, contentType)

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")
      response.body("href") shouldBe JsString("http://localhost:20001/upscan/upload")
      response.body("fields") shouldBe Json.obj("key" -> "value")
    }
  }

  Feature("Initiate V2") {
    Scenario("Should accept initiate requests without ID") {
      Given("A Client of the FileStore needs an upload form")
      When("It is requested")
      val response: HttpResponse[Map[String, JsValue]] = initiateV2()

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")

      response.body("id") shouldBe a[JsString]

      response.body("uploadRequest") shouldBe Json.obj(
        "href" -> "http://localhost:20001/upscan/upload",
        "fields" -> Json.obj("key" -> "value")
      )
    }

    Scenario("Should accept initiate requests with client generated ID") {
      Given("A Client of the FileStore needs an upload form")
      When("It is requested")
      val response: HttpResponse[Map[String, JsValue]] = initiateV2(Some("slurm"))

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")

      response.body("id") shouldBe JsString("slurm")

      response.body("uploadRequest") shouldBe Json.obj(
        "href" -> "http://localhost:20001/upscan/upload",
        "fields" -> Json.obj("key" -> "value")
      )
    }
  }

  Feature("Get") {
    Scenario("Should show the file is persisted") {
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

  Feature("Get files") {
    Scenario("Should return all files matching search") {
      Given("Files have been uploaded")
      val id1 = upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      val id2 = upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response = getFiles(Search(ids = Some(Set(id1, id2))))

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")

      response.body.asInstanceOf[JsArray].value.size shouldBe 2
      (response.body \\ "fileName").map(_.as[String]) should contain only("some-file1.txt", "some-file2.txt")
    }

    Scenario("Should return all files for empty search") {
      Given("Files have been uploaded")

      upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response = getFiles(Search())

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")

      (response.body \\ "fileName").map(_.as[String]) should contain allOf("some-file1.txt", "some-file2.txt")
    }

  }

  Feature("Get files with pagination") {
    Scenario("Should return all files matching search") {
      Given("Files have been uploaded")
      val id1 = upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      val id2 = upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response = getFiles(Search(ids = Some(Set(id1, id2))), Some(Pagination()))

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")

      response.body.asInstanceOf[JsObject].value("resultCount").toString().toInt shouldBe 2
      (response.body \\ "fileName").map(_.as[String]) should contain only("some-file1.txt", "some-file2.txt")
    }

    Scenario("Should return all files for empty search") {
      Given("Files have been uploaded")

      upload("some-file1.txt", "text/plain").body("id").as[JsString].value
      upload("some-file2.txt", "text/plain").body("id").as[JsString].value

      When("I request the file details")
      val response = getFiles(Search(), Some(Pagination()))

      Then("The response code should be Ok")
      response.code shouldBe Status.OK

      And("The response body contains the file details")
      response.body.asInstanceOf[JsObject].value("resultCount").toString().toInt shouldBe 2
      (response.body \\ "fileName").map(_.as[String]) should contain allOf("some-file1.txt", "some-file2.txt")
    }

  }

  Feature("Notify") {
    Scenario("Successful scan should update the status") {
      Given("A File has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("Notify is Called")
      val uri = new File(filePath).toURI
      val response = notifySuccess(id, "some-file.txt", uri)

      Then("The response code should be Created")
      response.code shouldBe Status.CREATED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body("url") shouldBe JsString(uri.toString)

      And("The response shows the file is marked as safe")
      response.body("scanStatus") shouldBe JsString("READY")
    }

    Scenario("Quarantined scan should update the status") {
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

  Feature("Publish") {
    Scenario("Should persist the file to permanent storage") {
      Given("A File has been uploaded and marked as safe")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      notifySuccess(id, "some-file.txt")

      When("It is Published")
      val response = publishSafeFile(id)

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body("scanStatus") shouldBe JsString("READY")
      response.body("publishable") shouldBe JsBoolean(true)
      response.body("published") shouldBe JsBoolean(true)

      And("The response shows the file published")
      response.body("url").as[JsString].value should include(s"$id?X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    Scenario("Should mark an un-safe file as publishable, but not persist") {
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
      publishResponse.body("publishable") shouldBe JsBoolean(true)
      publishResponse.body("published") shouldBe JsBoolean(false)

      And("I can call GET and see the file is unpublished")
      val getResponse = getFile(id)
      getResponse.code shouldBe Status.OK
      getResponse.body("fileName") shouldBe JsString("some-file.txt")
      getResponse.body("mimeType") shouldBe JsString("text/plain")
      getResponse.body("scanStatus") shouldBe JsString("FAILED")
      getResponse.body("publishable") shouldBe JsBoolean(true)
      getResponse.body("published") shouldBe JsBoolean(false)
      getResponse.body.contains("url") shouldBe false
    }

    Scenario("Should remove publishable file which has expired") {
      Given("A File has been uploaded and marked as safe")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      val uri = new File(filePath).toURI
      notifySuccess(id, "some-file.txt", uri = new URI(uri.toString + "?X-Amz-Date=19700101T000000Z&X-Amz-Expires=0"))

      When("It is Published")
      val response = publishSafeFile(id)

      Then("The response code should be Not Found")
      response.code shouldBe Status.NOT_FOUND

      And("I can call GET and see the file does not exist")
      val getResponse = getFile(id)
      getResponse.code shouldBe Status.NOT_FOUND
    }
  }

  private def getFile(id: String): HttpResponse[Map[String, JsValue]] = {
    Http(s"$serviceUrl/file/$id")
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.GET)
      .execute(convertingResponseToJS)
  }

  private def deleteFile(id: String): HttpResponse[String] = {
    stubS3DeleteOne(id)

    Http(s"$serviceUrl/file/$id")
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.DELETE)
      .asString
  }

  private def getFiles(search: Search, pagination: Option[Pagination] = None): HttpResponse[JsValue] = {
    val queryParams = Search.bindable.unbind("", search) + pagination.map(p => "&" + Pagination.bindable.unbind("", p)).getOrElse("")
    Http(s"$serviceUrl/file?$queryParams")
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.GET)
      .execute(convertingArrayResponseToJS)
  }

  private def publishSafeFile(id: String): HttpResponse[Map[String, JsValue]] = {
    stubS3Upload(id)
    Http(s"$serviceUrl/file/$id/publish")
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.POST)
      .execute(convertingResponseToJS)
  }

  private def publishUnSafeFile(id: String): HttpResponse[Map[String, JsValue]] = {
    // Should NOT call S3 Upload
    Http(s"$serviceUrl/file/$id/publish")
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.POST)
      .execute(convertingResponseToJS)
  }

  private def notifySuccess(id: String, fileName: String, uri: URI = new File(filePath).toURI): HttpResponse[Map[String, JsValue]] = {
    val url = uri.toURL.toString
    val model = SuccessfulScanResult("reference", url, UploadDetails(fileName, "text/plain", Instant.now(), "checksum"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson[ScanResult](model).toString())
      .header(HeaderNames.CONTENT_TYPE, ContentTypes.JSON)
      .param(apiTokenKey, hash(appConfig.authorization))
      .execute(convertingResponseToJS)
  }

  private def notifyFailure(id: String): HttpResponse[Map[String, JsValue]] = {
    val model = FailedScanResult("reference", FailureDetails(FailureReason.QUARANTINE, "message"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson[ScanResult](model).toString())
      .header(HeaderNames.CONTENT_TYPE, ContentTypes.JSON)
      .param(apiTokenKey, hash(appConfig.authorization))
      .execute(convertingResponseToJS)
  }

  private def upload(filename: String, contentType: String): HttpResponse[Map[String, JsValue]] = {
    stubUpscanInitiate
    stubUpscanUpload

    val tempFile = SingletonTemporaryFileCreator.create(filename)
    Files.write(tempFile.path, List("foo").asJava)

    val form = MultiPart(
      "file",
      filename,
      contentType,
      Files.readAllBytes(tempFile.path)
    )

    Http(s"$serviceUrl/file")
      .header(apiTokenKey, appConfig.authorization)
      .postMulti(form)
      .execute(convertingResponseToJS)
  }

  private def initiate(filename: String, contentType: String): HttpResponse[Map[String, JsValue]] = {
    stubUpscanInitiate

    Http(s"$serviceUrl/file")
      .header("Content-Type", "application/json")
      .header(apiTokenKey, appConfig.authorization)
      .postData(Json.toJson(UploadRequest(fileName = filename, mimeType = contentType)).toString())
      .execute(convertingResponseToJS)
  }

  private def initiateV2(id: Option[String] = None): HttpResponse[Map[String, JsValue]] = {
    stubUpscanInitiateV2

    Http(s"$serviceUrl/file/initiate")
      .header("Content-Type", "application/json")
      .header(apiTokenKey, appConfig.authorization)
      .postData(Json.toJson(v2.FileStoreInitiateRequest(id = id)).toString())
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

  private def stubUpscanInitiateV2 = {
    stubFor(
      post("/upscan/v2/initiate")
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
      get("/digital-tariffs-local/?encoding-type=url")
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

  private def stubS3DeleteOne(id: String) = {
    stubFor(
      delete(s"/digital-tariffs-local/$id")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )
  }

  private def convertingResponseToJS: InputStream => Map[String, JsValue] = { is =>
    val body = IOUtils.toString(is, Charset.defaultCharset)
    Try(Json.parse(body))
      .map(_.as[JsObject].value)
      .getOrElse(throw new AssertionError(s"The response was not valid JSON object:\n $body"))
  }

  private def convertingArrayResponseToJS: InputStream => JsValue = { is =>
    val body = IOUtils.toString(is, Charset.defaultCharset)
    Try(Json.parse(body))
      .getOrElse(throw new AssertionError(s"The response was not valid JSON array:\n $body"))
  }

  private def dbFileStoreSize: Int = {
    result(repository.collection.countDocuments().toFuture(), timeout).toInt
  }

}
