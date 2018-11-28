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
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import scalaj.http.{Http, HttpResponse, MultiPart}
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult.format
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.util.ResourceFiles

import scala.collection.Map
import scala.util.Try

class FileStoreSpec extends BaseFeatureSpec with WiremockFeatureTestServer with ResourceFiles {

  override lazy val port = 14681
  protected val serviceUrl = s"http://localhost:$port/binding-tariff-filestore"

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "s3.endpoint" -> s"http://localhost:$wirePort",
      "microservice.services.upscan-initiate.port" -> s"$wirePort"
    )
    .build()

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

  feature("Notify") {
    scenario("Successful scan should update the status") {
      Given("A File has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("Notify is Called")
      val uri = new File("test/util/resources/file.txt").toURI
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
      val response = publish(id)

      Then("The response code should be Accepted")
      response.code shouldBe Status.ACCEPTED

      And("The response body contains the file details")
      response.body("fileName") shouldBe JsString("some-file.txt")
      response.body("mimeType") shouldBe JsString("text/plain")
      response.body("scanStatus") shouldBe JsString("READY")

      And("The response shows the file published")
      response.body("url").as[JsString].value should include(s"$id?X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    scenario("Should return an error for an un-safe file") {
      Given("A File has been uploaded and marked as quarantined")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value
      notifyFailure(id)

      When("It is Published")
      val publishResponse = publish(id)

      Then("The response code should be Forbidden")
      publishResponse.code shouldBe Status.FORBIDDEN

      And("The response body contains An error")
      publishResponse.body shouldBe Map(
        "code" -> JsString("FORBIDDEN"),
        "message" -> JsString("Can not publish file with status FAILED")
      )

      And("I can call GET and see the file is unpublished")
      val getResponse = getFile(id)
      getResponse.code shouldBe Status.OK
      getResponse.body("fileName") shouldBe JsString("some-file.txt")
      getResponse.body("mimeType") shouldBe JsString("text/plain")
      getResponse.body("scanStatus") shouldBe JsString("FAILED")
      getResponse.body.contains("url") shouldBe false
    }
  }

  private def getFile(id: String): HttpResponse[Map[String, JsValue]] = {
    Http(s"$serviceUrl/file/$id")
      .method("GET")
      .execute(convertingResponseToJS)
  }

  private def publish(id: String): HttpResponse[Map[String, JsValue]] = {
    stubS3Upload(id)
    Http(s"$serviceUrl/file/$id/publish")
      .method("POST")
      .execute(convertingResponseToJS)
  }

  private def notifySuccess(id: String, uri: URI = new File("test/util/resources/file.txt").toURI): HttpResponse[Map[String, JsValue]] = {
    val url = uri.toURL.toString
    val model = SuccessfulScanResult("reference", url, UploadDetails(Instant.now(), "checksum"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson(model).toString())
      .header("Content-Type", "application/json")
      .execute(convertingResponseToJS)
  }

  private def notifyFailure(id: String): HttpResponse[Map[String, JsValue]] = {
    val model = FailedScanResult("reference", FailureDetails(FailureReason.QUARANTINED, "message"))

    Http(s"$serviceUrl/file/$id/notify")
      .postData(Json.toJson(model).toString())
      .header("Content-Type", "application/json")
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
            .withBody(fromFile("test/util/resources/upscan/initiate_wiremock-response.json"))
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

  private def convertingResponseToJS: InputStream => Map[String, JsValue] = { is =>
    val body = IOUtils.toString(is)
    Try(Json.parse(body))
      .map(_.as[JsObject].value)
      .getOrElse(throw new AssertionError(s"The response was not valid JSON:\n $body"))
  }

}
