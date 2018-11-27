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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.{EqualToPattern, RegexPattern}
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.bindingtarifffilestore.config.{AppConfig, S3Configuration}
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, FileWithMetadata}
import uk.gov.hmrc.bindingtarifffilestore.util.{ResourceFiles, WiremockTestServer}
import uk.gov.hmrc.play.test.UnitSpec

class AmazonS3ConnectorSpec extends UnitSpec with WiremockTestServer with MockitoSugar with BeforeAndAfterEach with ResourceFiles {

  private val s3Config = S3Configuration("key", "secret", "region", "bucket", Some(s"http://localhost:$wirePort"))
  private val config = mock[AppConfig]

  private val connector = new AmazonS3Connector(config)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.s3Configuration).willReturn(s3Config)
  }

  "Connector" should {
    "Get All" in {
      // Given
      stubFor(
        get("/bucket/?encoding-type=url")
          .withHeader("Authorization", new RegexPattern(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fromFile("test/unit/resources/aws/list-objects_response.xml"))
          )
      )

      // When
      val all: Seq[FileMetadata] = connector.getAll

      // Then
      all should have size 1
      all.head.fileName shouldBe "image.jpg"
    }

    "Upload" in {
      // Given
      stubFor(
        put("/bucket/id")
          .withHeader("Authorization", new RegexPattern(s"AWS4-HMAC-SHA256 Credential=${s3Config.key}/\\d+/${s3Config.region}/s3/aws4_request, .*"))
          .withHeader("Content-Type", new EqualToPattern("text/plain"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
          )
      )

      val exampleFile = TemporaryFile("example-file.json")
      val fileUploading = FileWithMetadata(
        exampleFile,
        FileMetadata("id", "file.txt", "text/plain")
      )
      val fileUploaded = FileWithMetadata(
        exampleFile,
        FileMetadata("id", "file.txt", "text/plain", Some(s"$wireMockUrl/id"))
      )

      // Then
      connector.upload(fileUploading) shouldBe fileUploaded
    }
  }



}