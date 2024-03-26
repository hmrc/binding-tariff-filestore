/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.util.ResourceFiles
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Instant
import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait FileStoreHelpers extends WiremockFeatureTestServer with ResourceFiles {

  val timeout: FiniteDuration = 5.seconds

  val serviceUrl = s"http://localhost:$port"

  val filePath = "test/resources/file.txt"

  def getFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient.GET(url"$serviceUrl/file/$id")

  def deleteFiles()(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient.DELETE(url"$serviceUrl/file")

  def deleteFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    stubS3DeleteOne(id)

    val response: Future[HttpResponse] = httpClient.DELETE(url"$serviceUrl/file/$id")
    response
  }

  def getFiles(queryParams: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"$serviceUrl/file"
    httpClient.GET(url, queryParams = queryParams)
  }

  def publishSafeFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    stubS3Upload(id)

    httpClient.POST(
      url"$serviceUrl/file/$id/publish",
      Json.toJson[FileMetadata](FileMetadata(id, None, None))
    )
  }

  def publishUnsafeFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    // Should NOT call S3 Upload
    httpClient.POST(
      url"$serviceUrl/file/$id/publish",
      Json.toJson[FileMetadata](FileMetadata(id, None, None))
    )

  def notifySuccess(
    id: String,
    fileName: String,
    uri: URI = new File(filePath).toURI
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url   = uri.toURL.toString
    val model =
      SuccessfulScanResult(
        reference = "reference",
        downloadUrl = url,
        uploadDetails = UploadDetails(fileName, "text/plain", Instant.now(), "checksum")
      )

    httpClient.POST(url"$serviceUrl/file/$id/notify", model)
  }

  def notifyFailure(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val model =
      FailedScanResult(reference = "reference", failureDetails = FailureDetails(FailureReason.QUARANTINE, "message"))
    httpClient.POST(url"$serviceUrl/file/$id/notify", model)
  }

  def upload(id: Option[String], filename: String, contentType: String, publishable: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] = {
    stubUpscanInitiate
    stubUpscanUpload

    val tempFile = SingletonTemporaryFileCreator.create(filename)
    Files.write(tempFile.path, List("foo").asJava)

    val response: Future[HttpResponse] =
      httpClient.POST(
        url"$serviceUrl/file",
        UploadRequest(id = id, fileName = filename, mimeType = contentType, publishable = publishable)
      )

    response
  }

  def initiateV2(id: Option[String] = None, publishable: Boolean)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    stubUpscanInitiateV2
    httpClient.POST(
      url"$serviceUrl/file/initiate",
      v2.FileStoreInitiateRequest(id = id, publishable = publishable)
    )
  }

  def stubUpscanUpload: StubMapping =
    stubFor(
      post("/upscan/upload")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )

  def stubUpscanInitiate: StubMapping =
    stubFor(
      post("/upscan/initiate")
        .willReturn(
          aResponse()
            .withBody(fromFile("upscan/initiate_wiremock-response.json"))
        )
    )

  def stubUpscanInitiateV2: StubMapping =
    stubFor(
      post("/upscan/v2/initiate")
        .willReturn(
          aResponse()
            .withBody(fromFile("upscan/initiate_wiremock-response.json"))
        )
    )

  def stubS3Upload(id: String): StubMapping =
    stubFor(
      put(s"/digital-tariffs-local/$id")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )

  def stubS3ListAll(): StubMapping =
    stubFor(
      get("/digital-tariffs-local/?encoding-type=url")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(fromFile("aws/list-objects_response.xml"))
        )
    )

  def stubS3DeleteAll(): StubMapping =
    stubFor(
      post(s"/digital-tariffs-local/?delete")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(fromFile("aws/delete-objects_response.xml"))
        )
    )

  def stubS3DeleteOne(id: String): StubMapping =
    stubFor(
      delete(s"/digital-tariffs-local/$id")
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
        )
    )

  def dbFileStoreSize: Int =
    result(repository.collection.countDocuments().toFuture(), timeout).toInt

}
