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

import java.io.InputStream
import java.nio.file.Files

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.io.IOUtils
import play.api.Application
import play.api.http.{HttpVerbs, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFile
import play.api.libs.json._
import scalaj.http.{Http, HttpResponse, MultiPart}
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockFeatureTestServer}

import scala.collection.Map
import scala.util.Try

class FileStoreTTLSpec extends WiremockFeatureTestServer with ResourceFiles {

  override lazy val port = 14681
  protected val serviceUrl = s"http://localhost:$port/binding-tariff-filestore"

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "s3.endpoint" -> s"http://localhost:$wirePort",
      "microservice.services.upscan-initiate.port" -> s"$wirePort",
      "mongodb.timeToLiveInSeconds" -> "1"
    )
    .build()

  feature("Upload") {
    scenario("Should persist only for timeout") {
      Given("A file has been uploaded")
      val id = upload("some-file.txt", "text/plain")
        .body("id").as[JsString].value

      When("I try to retrieve the file after the Time To Live")
      Thread.sleep(10000) // TODO: do not use `sleep` in Scala

      val response = getFile(id)

      Then("The response code should be Not Found")
      response.code shouldBe Status.NOT_FOUND
    }
  }

  private def getFile(id: String): HttpResponse[Map[String, JsValue]] = {
    Http(s"$serviceUrl/file/$id")
      .method(HttpVerbs.GET)
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
            .withBody(fromFile("/upscan/initiate_wiremock-response.json"))
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
