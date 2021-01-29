/*
 * Copyright 2021 HM Revenue & Customs
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

import better.files._
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.GpsDirectory
import com.github.tomakehurst.wiremock.client.WireMock._
import java.nio.file.{Files, Paths}
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, UnitSpec, WiremockTestServer}
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.poi.xwpf.usermodel.XWPFDocument

class AmazonS3ConnectorSpec extends UnitSpec with WiremockTestServer
  with MockitoSugar with BeforeAndAfterEach with ResourceFiles {

  private val s3Config = S3Configuration("key", "secret", "region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config = mock[AppConfig]

  private val connector = new AmazonS3Connector(config, SingletonTemporaryFileCreator)

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

      val url = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      // Then
      val result = connector.upload(fileUploading)
      result.id shouldBe "id"
      result.fileName shouldBe Some("file.txt")
      result.mimeType shouldBe Some("text/plain")
      result.url.get shouldBe s"$wireMockUrl/bucket/id"
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
          .withHeader("Authorization", matching(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .withHeader("Content-Type", equalTo("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.BAD_GATEWAY)
          )
      )
      val url = SingletonTemporaryFileCreator.create("example.txt").path.toUri.toURL.toString
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some(url))

      // Then
      val exception = intercept[AmazonS3Exception] {
        connector.upload(fileUploading)
      }
      exception.getMessage shouldBe "Bad Gateway (Service: Amazon S3; Status Code: 502; Error Code: 502 Bad Gateway; Request ID: null; S3 Extended Request ID: null; Proxy: null)"
    }

    "Strip office metadata" in {
      val testFileURI = getClass().getResource("/newwi.docx").toURI()
      val testFilePath = Paths.get(testFileURI)
      val testFile = SingletonTemporaryFileCreator.create(testFilePath)
      val fileMetaData = FileMetadata("id", Some("test.docx"), Some("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), None)

      using(Files.newInputStream(testFilePath)) { testFileIs =>
        using(new XWPFDocument(testFileIs)) { docBefore =>
          docBefore.getProperties().getCoreProperties().getCreator() shouldNot be(null)

          val strippedFile = connector.stripMetadata(fileMetaData, testFile)

          using(Files.newInputStream(strippedFile.path)) { strippedFileIs =>
            using(new XWPFDocument(strippedFileIs)) { docAfter =>
              docAfter.getProperties().getCoreProperties().getCreator() shouldBe null
            }
          }
        }
      }
    }

    "Strip PDF metadata" in {
      val testFileURI = getClass().getResource("/eb83_first_en.pdf").toURI()
      val testFilePath = Paths.get(testFileURI)
      val testFile = SingletonTemporaryFileCreator.create(testFilePath)
      val fileMetaData = FileMetadata("id", Some("test.pdf"), Some("application/pdf"), None)

      using(Files.newInputStream(testFile.path)) { testFileIs =>
        using(PDDocument.load(testFileIs, MemoryUsageSetting.setupTempFileOnly())) { docBefore =>
          val infoBefore = docBefore.getDocumentInformation()
          infoBefore.getAuthor() shouldNot be(null)

          val strippedFile = connector.stripMetadata(fileMetaData, testFile)

          using(Files.newInputStream(strippedFile.path)) { strippedFileIs =>
            using(PDDocument.load(strippedFileIs, MemoryUsageSetting.setupTempFileOnly())) { docAfter =>
              val infoAfter = docAfter.getDocumentInformation()
              infoAfter.getAuthor() shouldBe null
            }
          }
        }
      }
    }

    "Strip EXIF metadata" in {
      val testFileURI = getClass().getResource("/Metadata_test_file.jpg").toURI()
      val testFilePath = Paths.get(testFileURI)
      val testFile = SingletonTemporaryFileCreator.create(testFilePath)
      val fileMetaData = FileMetadata("id", Some("test.jpg"), Some("image/jpeg"), None)

      using(Files.newInputStream(testFilePath)) { testFileIs =>
        val metadataBefore = ImageMetadataReader.readMetadata(testFileIs)
        metadataBefore.getDirectoryCount() should be > 0
        val gpsBefore = metadataBefore.getFirstDirectoryOfType(classOf[GpsDirectory])
        gpsBefore.getString(GpsDirectory.TAG_LONGITUDE) shouldNot be(null)

        val strippedFile = connector.stripMetadata(fileMetaData, testFile)

        using(Files.newInputStream(strippedFile.path)) { strippedFileIs =>
          val metadataAfter = ImageMetadataReader.readMetadata(strippedFileIs)
          metadataAfter.getDirectoryCount() shouldBe < (metadataBefore.getDirectoryCount())
          metadataAfter.getFirstDirectoryOfType(classOf[GpsDirectory]) shouldBe null
        }
      }
    }
  }

  "Sign" should {
    "append token to URL" in {
      // Given
      val file = FileMetadata("id", Some("file.txt"), Some("text/plain"), Some("url"))

      // When
      connector.sign(file).url.get should startWith(s"$wireMockUrl/bucket/id?X-Amz-Algorithm=AWS4-HMAC-SHA256")
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
