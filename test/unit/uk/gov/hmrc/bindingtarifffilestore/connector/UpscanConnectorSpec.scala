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

package uk.gov.hmrc.bindingtarifffilestore.connector

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{UploadRequestTemplate, UploadSettings, UpscanInitiateResponse}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata}
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockTestServer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class UpscanConnectorSpec extends UnitSpec with WithFakeApplication with WiremockTestServer
  with MockitoSugar with BeforeAndAfterEach with ResourceFiles {

  private val config = mock[AppConfig]

  private val actorSystem = ActorSystem.create("test")
  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, fakeApplication.injector.instanceOf[Environment])
  private val hmrcWsClient = new DefaultHttpClient(fakeApplication.configuration, auditConnector, wsClient, actorSystem)

  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val connector = new UpscanConnector(config, hmrcWsClient, wsClient)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.upscanInitiateUrl).willReturn(wireMockUrl)
  }

  "Connector" should {
    "Initiate" in {
      stubFor(
        post("/upscan/initiate")
          .willReturn(
            aResponse()
              .withBody(fromFile("upscan/initiate_response.json"))
          )
      )

      val response = await(connector.initiate(UploadSettings("callback")))
      response shouldBe UpscanInitiateResponse(
        reference = "reference",
        uploadRequest = UploadRequestTemplate(
          href = "href",
          fields = Map(
            "key" -> "value"
          )
        )
      )
    }

    "Upload" in {
      stubFor(
        post("/path")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val templateUploading = UploadRequestTemplate(
        href = s"$wireMockUrl/path",
        fields = Map(
          "x-amz-meta-callback-url" -> "x-amz-meta-callback-url",
          "x-amz-date" -> "x-amz-date",
          "x-amz-credential" -> "x-amz-credential",
          "x-amz-meta-original-filename" -> "x-amz-meta-original-filename",
          "x-amz-algorithm" -> "x-amz-algorithm",
          "key" -> "key",
          "acl" -> "acl",
          "x-amz-signature" -> "x-amz-signature",
          "x-amz-meta-session-id" -> "x-amz-meta-session-id",
          "x-amz-meta-request-id" -> "x-amz-meta-request-id",
          "x-amz-meta-consuming-service" -> "x-amz-meta-consuming-service",
          "x-amz-meta-upscan-initiate-received" -> "x-amz-meta-upscan-initiate-received",
          "x-amz-meta-upscan-initiate-response" -> "x-amz-meta-upscan-initiate-response",
          "policy" -> "policy"
        )
      )
      val fileUploading = FileWithMetadata(
        TemporaryFile("example-file.json"),
        FileMetadata("id", "file.txt", "text/plain")
      )

      await(connector.upload(templateUploading, fileUploading))
      verify(
        postRequestedFor(urlEqualTo("/path"))
          .withRequestBody(containing("x-amz-meta-callback-url"))
          .withRequestBody(containing("x-amz-date"))
          .withRequestBody(containing("x-amz-credential"))
          .withRequestBody(containing("x-amz-meta-original-filename"))
          .withRequestBody(containing("x-amz-algorithm"))
          .withRequestBody(containing("key"))
          .withRequestBody(containing("acl"))
          .withRequestBody(containing("x-amz-signature"))
          .withRequestBody(containing("x-amz-meta-session-id"))
          .withRequestBody(containing("x-amz-meta-request-id"))
          .withRequestBody(containing("x-amz-meta-consuming-service"))
          .withRequestBody(containing("x-amz-meta-upscan-initiate-received"))
          .withRequestBody(containing("x-amz-meta-upscan-initiate-response"))
          .withRequestBody(containing("policy"))
          .withRequestBody(containing("Content-Disposition: form-data"))
          .withRequestBody(containing("Content-Type: text/plain"))
      )
    }
  }

  private def containing(string: String): ContainsPattern = {
    new ContainsPattern(string)
  }

}
