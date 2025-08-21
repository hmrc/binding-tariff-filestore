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

import com.amazonaws.services.s3.model.AmazonS3Exception
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

import java.net.{URI, URL}
import java.time.{Instant, LocalDate}
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

  private implicit val hc: HeaderCarrier = mock(classOf[HeaderCarrier])
  private implicit val mat: Materializer = mock(classOf[Materializer])
  private val directory: Path.Directory  =
    Path.Directory("test")

  private def objectListingJson: String =
    """{
    "objectSummaries": [
      {
        "location": "/object-store/object/something/0993180f-8f31-41b2-905c-71f0273bb7d4",
        "contentLength": 49,
        "contentMD5": "4033ff85a6fdc6a2f51e60d89236a244",
        "lastModified": "2020-07-21T13:16:42.859Z"
      },
      {
        "location": "/object-store/object/something/23265eab-268e-4fcc-904f-775586b362c2",
        "contentLength": 49,
        "contentMD5": "a3c2f1e38701bd2c7b54ebd7b1cd0dbc",
        "lastModified": "2020-07-21T13:16:41.226Z"
      }
    ]
  }"""

  lazy val objectStoreClientStub: StubPlayObjectStoreClient = {
    val baseUrl = wireMockUrl
    val owner   = s"owner-${randomUUID().toString}"
    val token   = s"token-${randomUUID().toString}"
    val config  = ObjectStoreClientConfig(baseUrl, owner, token, RetentionPeriod.OneWeek)

    new StubPlayObjectStoreClient(config)
  }

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.object-store.port" -> s"$wirePort"
    )
    .bindings(inject.bind(classOf[PlayObjectStoreClient]).to(objectStoreClientStub))
    .build()

  private implicit val config: AppConfig = fakeApplication.injector.instanceOf[AppConfig]

  private val connector = new ObjectStoreConnector(objectStoreClientStub, config)

  "Get All" should {
    "list files" in {
      // When
      stubFor(
        get(urlMatching(s"/object-store/list/.*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withHeader("Content-Length", s"${objectListingJson.length}")
              .withBody(objectListingJson)
          )
      )

      // Then
      val all = objectStoreClientStub.listObjects(directory)

//      println(s"Connector base url: ${config.filestoreUrl}")
//      println(s"Wiremock base url: $wireMockUrl")
//      println(config.filestoreUrl)
//      println(config.upscanInitiateUrl)
//      println(await(connector.getAll(directory)))
//      println(objectStoreClientStub.listObjects(directory))
//      println(WireMock.getAllServeEvents)

      verify(getRequestedFor(urlMatching("/object-store/list/.*")))

    }
  }

  "Upload" should {

    "add content to the object store" in {
      //Given
      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url.toString))
      // When
      verify(postRequestedFor(urlEqualTo("/object-store/object/.*")))

      stubFor(
        post(urlPathMatching("/object-store/object/.*"))
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

    "Throw Exception on upload failure" in {
      // Given
      stubFor(
        post(urlPathMatching("/object-store/object/.*"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_GATEWAY)
          )
      )
      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      // Then
      val exception = intercept[AmazonS3Exception] {
        connector.upload(fileUploading)
      }

      exception.getMessage shouldBe
        """Bad Gateway (Service: Amazon S3;
          | Status Code: 502;
          | Error Code: 502 Bad Gateway;
          | Request ID: null;
          | S3 Extended Request ID: null;
          | Proxy: null)""".stripMargin.replaceAll("\n", "")
    }
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
