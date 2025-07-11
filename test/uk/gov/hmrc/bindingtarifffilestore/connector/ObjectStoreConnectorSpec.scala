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
import org.apache.pekko.stream.Materializer
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global

class ObjectStoreConnectorSpec extends UnitSpec with WiremockTestServer with BeforeAndAfterEach with ResourceFiles {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val s3Config  = S3Configuration("region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config    = mock(classOf[AppConfig])
  private val mockObjectStoreClient = mock(classOf[PlayObjectStoreClient])
  private val date      = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))
  private val connector = new ObjectStoreConnector(mockObjectStoreClient, config)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.s3Configuration).willReturn(s3Config)
  }

  "Get All" should {

    "Delegate to S3" in {
      // Given
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )

      // When
      val all: Seq[String] = connector.getAll

      // Then
      all        should have size 1
      all.head shouldBe "image.jpg"
    }

  }

  "Upload" should {

    "Delegate to S3" in {
      // Given
      stubFor(
        put("/bucket/id")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      // Then
      val result = connector.upload(fileUploading)
      result.id       shouldBe "id"
      result.fileName shouldBe Some("file.txt")
      result.mimeType shouldBe Some("text/plain")
      result.url.get  shouldBe s"$wireMockUrl/bucket/id"
    }

    "Throw Exception on missing URL" in {
      // Given
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"))

      // Then
      val exception = intercept[IllegalArgumentException] {
        connector.upload(fileUploading)
      }
      exception.getMessage shouldBe "Missing URL"
    }

    "Throw Exception on upload failure" in {
      // Given
      stubFor(
        put("/bucket/id")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
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
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("url"))

      // When
      connector.sign(file).url.get should startWith(s"$wireMockUrl/bucket/id?")
      connector.sign(file).url.get should include("X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    "not append token to empty URL" in {
      // Given
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), None)

      // When
      connector.sign(file).url shouldBe None
    }
  }

  "Delete All" should {
    "Delegate to S3" in {
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )
      stubFor(
        post("/bucket/?delete")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/delete-objects_response.xml"))
          )
      )

      connector.deleteAll()

      WireMock.verify(
        postRequestedFor(urlEqualTo("/bucket/?delete"))
      )
    }

    "Do nothing for no files" in {
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/empty-list-objects_response.xml"))
          )
      )

      connector.deleteAll()

      WireMock.verify(0, postRequestedFor(urlEqualTo("/bucket/?delete")))
    }
  }

  "Delete One" should {
    "Delegate to S3" in {
      stubFor(
        delete("/bucket/id")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      connector.delete("id")

      WireMock.verify(deleteRequestedFor(urlEqualTo("/bucket/id")))
    }
  }

}
