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
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import software.amazon.awssdk.services.s3.model.S3Exception
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util._

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AmazonS3ConnectorSpec extends UnitSpec with WiremockTestServer with BeforeAndAfterEach with ResourceFiles {

  private val s3Config  = S3Configuration("region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config    = mock(classOf[AppConfig])
  private val date      = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))
  private val connector = new AmazonS3Connector(config)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.s3Configuration).willReturn(s3Config)
  }

  "Get All" should {

    "Delegate to S3" in {
      stubFor(
        get("/bucket?list-type=2")
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

      val all: Seq[String] = connector.getAll

      all        should have size 1
      all.head shouldBe "image.jpg"
    }
  }

  "Upload" should {

    "Delegate to S3" in {
      stubFor(
        put("/bucket/id")
          .withHeader(
            "Authorization",
            matching(s"AWS4-HMAC-SHA256 Credential=(.*)/$date/${s3Config.region}/s3/aws4_request, .*")
          )
          .withHeader("Content-Type", equalTo("application/octet-stream"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val url           = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      val result = connector.upload(fileUploading)

      result.id       shouldBe "id"
      result.fileName shouldBe Some("file.txt")
      result.mimeType shouldBe Some("text/plain")
      result.url.get  shouldBe s"$wireMockUrl/bucket/id"
    }

    "Throw Exception on missing URL" in {
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"))

      val exception = intercept[IllegalArgumentException] {
        connector.upload(fileUploading)
      }
      exception.getMessage shouldBe "Missing URL"
    }

    "Throw Exception on upload failure" in {
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

      val exception = intercept[S3Exception] {
        connector.upload(fileUploading)
      }

      exception.getMessage shouldBe s"(Service: S3, Status Code: 404, Request ID: null) (SDK Attempt Count: 2)"
    }

    "Throw UncheckedIOException and transform into S3Exception" in {
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
      val url       = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val wrongFile = s"$url/asd"

      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(wrongFile))

      val exception = intercept[S3Exception] {
        connector.upload(fileUploading)
      }

      exception.getMessage shouldBe s"Failed to read the file: java.nio.file.FileSystemException: ${wrongFile.replace("file:", "")}: Not a directory (Service: null, Status Code: 0, Request ID: null)"
    }
  }

  "Sign" should {
    "append token to URL" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("url"))

      connector.sign(file).url.get should include("X-Amz-Algorithm=AWS4-HMAC-SHA256")
    }

    "not append token to empty URL" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), None)

      connector.sign(file).url shouldBe None
    }
  }

  "Delete All" should {
    "Delegate to S3" in {
      stubFor(
        get("/bucket?list-type=2")
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
        post("/bucket?delete")
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
        postRequestedFor(urlEqualTo("/bucket?delete"))
      )
    }

    "Do nothing for no files" in {
      stubFor(
        get("/bucket?list-type=2")
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
