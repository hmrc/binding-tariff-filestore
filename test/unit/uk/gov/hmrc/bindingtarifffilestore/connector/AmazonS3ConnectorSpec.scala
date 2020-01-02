/*
 * Copyright 2020 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockTestServer}
import uk.gov.hmrc.play.test.UnitSpec

class AmazonS3ConnectorSpec extends UnitSpec with WiremockTestServer
  with MockitoSugar with BeforeAndAfterEach with ResourceFiles {

  private val s3Config = S3Configuration("key", "secret", "region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config = mock[AppConfig]

  private val connector = new AmazonS3Connector(config)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.s3Configuration).willReturn(s3Config)
  }

  "Get All" should {

    "Delegate to S3" in {
      // Given
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )

      // When
      val all: Seq[String] = connector.getAll

      // Then
      all should have size 1
      all.head shouldBe "image.jpg"
    }

  }

  "Upload" should {

    "Delegate to S3" in {
      // Given
      stubFor(
        put("/bucket/id")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val url = TemporaryFile("example.txt").file.toURI.toURL.toString
      val fileUploading = FileMetadata("id", "file.txt", "text/plain", Some(url))

      // Then
      val result = connector.upload(fileUploading)
      result.id shouldBe "id"
      result.fileName shouldBe "file.txt"
      result.mimeType shouldBe "text/plain"
      result.url.get shouldBe s"$wireMockUrl/bucket/id"
    }

    "Throw Exception on missing URL" in {
      // Given
      val fileUploading = FileMetadata("id", "file.txt", "text/plain")

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
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_GATEWAY)
          )
      )
      val url = TemporaryFile("example.txt").file.toURI.toURL.toString
      val fileUploading = FileMetadata("id", "file.txt", "text/plain", Some(url))

      // Then
      val exception = intercept[AmazonS3Exception] {
        connector.upload(fileUploading)
      }
      exception.getMessage shouldBe "Bad Gateway (Service: Amazon S3; Status Code: 502; Error Code: 502 Bad Gateway; Request ID: null; S3 Extended Request ID: null)"
    }
  }

  "Sign" should {
    "append token to URL" in {
      // Given
      val file = FileMetadata("id", "file.txt", "text/plain", Some("url"))

      // When
      connector.sign(file).url.get should startWith(s"$wireMockUrl/bucket/id?X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    "not append token to empty URL" in {
      // Given
      val file = FileMetadata("id", "file.txt", "text/plain", None)

      // When
      connector.sign(file).url shouldBe None
    }
  }

  "Delete All" should {
    "Delegate to S3" in {
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/list-objects_response.xml"))
          )
      )
      stubFor(
        post("/bucket/?delete")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/delete-objects_response.xml"))
          )
      )

      connector.deleteAll()

      verify(
        postRequestedFor(urlEqualTo("/bucket/?delete"))
          .withRequestBody(equalToXml(fromFile("aws/delete-objects_request.xml")))
      )
    }

    "Do nothing for no files" in {
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("aws/empty-list-objects_response.xml"))
          )
      )

      connector.deleteAll()

      verify(0, postRequestedFor(urlEqualTo("/bucket/?delete")))
    }
  }

  "Delete One" should {
    "Delegate to S3" in {
      stubFor(
        delete("/bucket/id")
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      connector.delete("id")

      verify(deleteRequestedFor(urlEqualTo("/bucket/id")))
    }
  }

}
