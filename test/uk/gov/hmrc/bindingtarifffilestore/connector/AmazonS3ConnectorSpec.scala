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

import software.amazon.awssdk.services.s3.model.S3Exception
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.mockito.Mockito.{mock, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.inject.DefaultApplicationLifecycle

class AmazonS3ConnectorSpec
    extends UnitSpec
    with WiremockTestServer
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with ResourceFiles {

  private val s3Config: S3Configuration =
    S3Configuration("region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config                    = {
    val m = mock(classOf[AppConfig])
    when(m.s3Configuration).thenReturn(s3Config)
    m
  }
  private val date                      = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
  private val lifecycle                 = new DefaultApplicationLifecycle()
  private val connector                 = new AmazonS3Connector(config, lifecycle)

  override protected def beforeEach(): Unit =
    super.beforeEach()

  override protected def afterAll(): Unit = {
    await(lifecycle.stop())
    super.afterAll()
  }

  "Get All" should {

    "Delegate to S3" in {
      stubFor(
        get("/bucket")
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

      // Then
      val exception = intercept[S3Exception] {
        connector.upload(fileUploading)
      }
      exception.statusCode() shouldBe 502
    }

    "Close the input stream even on upload failure" in {
      stubFor(
        put("/bucket/id")
          .willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR))
      )

      val tempFile      = SingletonTemporaryFileCreator.create("example.txt")
      val url           = tempFile.path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      var streamClosed = false

      val trackingConnector = new AmazonS3Connector(config, lifecycle) {
        override protected def openStream(url: java.net.URL): java.io.BufferedInputStream =
          new java.io.BufferedInputStream(url.openStream()) {
            override def close(): Unit = {
              streamClosed = true
              super.close()
            }
          }
      }

      intercept[S3Exception] {
        trackingConnector.upload(fileUploading)
      }

      streamClosed shouldBe true
    }
  }

  "Sign" should {
    "append token to URL" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("url"))

      connector.sign(file).url.get should startWith(s"$wireMockUrl/bucket/id?")
      connector.sign(file).url.get should include("X-Amz-Algorithm=AWS4-HMAC-SHA256")
      connector.sign(file).url.get should include("X-Amz-Expires=3600")
    }

    "not append token to empty URL" in {
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), None)

      connector.sign(file).url shouldBe None
    }
  }

  "Delete All" should {
    "Delegate to S3" in {
      stubFor(
        get("/bucket")
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
        get("/bucket")
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

      noException should be thrownBy connector.deleteAll()

      WireMock.verify(0, postRequestedFor(urlEqualTo("/bucket?delete")))
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
