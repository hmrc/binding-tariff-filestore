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

package uk.gov.hmrc.bindingtarifffilestore.connector

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.apache.pekko.actor.Status.Success
import org.apache.pekko.stream.Materializer
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{mock, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, inject}
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path.Directory
import uk.gov.hmrc.objectstore.client.{ObjectSummary, Path, RetentionPeriod}
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.play.test.stub
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClient

import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class ObjectStoreConnectorSpec
    extends UnitSpec
    with WithFakeApplication
    with WiremockTestServer
    with BeforeAndAfterEach
    with ResourceFiles {

  private implicit val hc: HeaderCarrier                 = mock(classOf[HeaderCarrier])
  private implicit val mat: Materializer                 = mock(classOf[Materializer])
  private implicit val config: AppConfig                 = mock(classOf[AppConfig])
  private val client                                     = fakeApplication.injector.instanceOf[PlayObjectStoreClient]
  private val objectStoreConnector: ObjectStoreConnector = new ObjectStoreConnector(client, config)
  private val directory: Path.Directory                  =
    Path.Directory("test")

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure("microservices.services.object-store.port" -> wirePort)
    .build()

  lazy val objectStoreClientStub: StubPlayObjectStoreClient = {
    val baseUrl = s"baseUrl-${randomUUID().toString}"
    val owner   = s"owner-${randomUUID().toString}"
    val token   = s"token-${randomUUID().toString}"
    val config  = ObjectStoreClientConfig(baseUrl, owner, token, RetentionPeriod.OneWeek)

    new StubPlayObjectStoreClient(config)
  }

  private val connector = new ObjectStoreConnector(objectStoreClientStub, config)

  "Get All" should {
    "list files" in {
      // When
      stubFor(
        get(urlMatching("/object-store/list/.*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Length", s"${5}")
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )
      // Then
//      val all = await(objectStoreConnector.getAll)
      WireMock.verify(
        getRequestedFor(urlEqualTo("/object-store/list/.*"))
      )
//      all.length shouldBe 3
    }
  }

  "Upload" should {

    "add content to the object store" in {
      //Given
      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url.toString))
      // When
      verify(postRequestedFor(urlEqualTo("/object-store/ops/upload-from-url")))

      stubFor(
        post(urlPathMatching("/object-store/ops/upload-from-url"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile(fileUploading.url.get))
          )
      )

      val result = await(connector.upload(fileUploading))
      // Then
      result.url shouldBe Some("http://example.txt")
    }

    "Throw Exception on missing URL" in {
      // Given
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"))
      // Then
      val exception     = intercept[IllegalArgumentException] {
        connector.upload(fileUploading)
      }

      exception.getMessage shouldBe "Missing URL"
    }

//    "Throw Exception on upload failure" in {
//      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
//      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))
//
//      // Then
////      val error: Throwable = connector.upload(fileUploading).value.get
////
////      error mustBe a[DraftAttachmentsConnectorException]
////      error
////        .asInstanceOf[DraftAttachmentsConnectorException]
////        .errors
////        .toList                must contain only "Digest header missing"
////      exception.getMessage shouldBe
////        """Failed to upload to the object store;""".stripMargin.replaceAll("\n", "")
//    }
  }

  "Sign" should {
    "append token to URL" in {
      // Given
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("http://foo.bar/test-123.txt"))
      // When
      verify(postRequestedFor(urlEqualTo("/object-store/ops/presigned-url")))

      val result = await(connector.sign(file))
      //
      result.url.get should startWith(s"http://foo.bar/test-123.txt")

    }

    "not append token to empty URL" in {
      // Given
      val file   = FileMetadata("id", Some("file.txt"), Some("text/plain"), None)
      val result = Await.result(connector.sign(file), 5.seconds)
      // When
      result.url shouldBe None
    }
  }

  "Delete All" should {
    "delete all files from object store if present" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("http://foo.bar/test-123.txt"))

      stubFor(
        get(urlMatching("/object-store/object/list/.*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Length", s"${5}")
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )

      stubFor(
        delete(urlPathMatching(s"/object-store/object/.*"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/delete-objects_response.xml"))
          )
      )
    }

    "Do nothing for no files" in {
      val result = Await.result(connector.deleteAll(), 5.seconds)

      WireMock.verify(0, postRequestedFor(urlEqualTo("/bucket/?delete")))
    }
  }

  "Delete One" should {
    "delete file from object store" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("http://foo.bar/test-123.txt"))
      stubFor(
        delete(urlPathMatching(s"/object-store/ops/${file.id}"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val result = await(connector.delete(file.id))

      result shouldBe ()
    }
  }

}
