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

package uk.gov.hmrc.bindingtarifffilestore

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, HeadBucketRequest, NoSuchBucketException}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.LocalDevelopmentS3CredentialsProvider
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST.format
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2.FileStoreInitiateRequest
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.jdk.CollectionConverters._

trait FileStoreHelpers extends WiremockFeatureTestServer {

  private def fromFile(path: String): String = {
    val url     = getClass.getClassLoader.getResource(path)
    val source  = Source.fromURL(url, "UTF-8")
    val content = source.getLines().mkString
    source.close()
    content
  }

  val timeout: FiniteDuration = 5.seconds

  val serviceUrl = s"http://localhost:$port"

  val filePath = "it/test/resources/file.txt"

  val conf: AppConfig = app.injector.instanceOf[AppConfig]

  private lazy val s3Client: S3Client =
    S3Client
      .builder()
      .region(Region.of(conf.s3Configuration.region))
      .forcePathStyle(true)
      .credentialsProvider(new LocalDevelopmentS3CredentialsProvider)
      .endpointOverride(new URI(conf.s3Configuration.endpoint.getOrElse("")))
      .build()

  def getFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2.get(url"$serviceUrl/file/$id").execute[HttpResponse]

  def deleteFiles()(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2.delete(url"$serviceUrl/file").execute[HttpResponse]

  private def doesBucketExistV2: Boolean =
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(conf.s3Configuration.bucket).build)
      true
    } catch {
      case _: NoSuchBucketException =>
        false
    }

  def createBucket(): Unit =
    if (!doesBucketExistV2) {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(conf.s3Configuration.bucket).build())
    }

  def deleteFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2.delete(url"$serviceUrl/file/$id").execute[HttpResponse]

  def getFiles(queryParams: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val queryParamsFinal =
      if (queryParams.isEmpty) "" else queryParams.map(pair => s"${pair._1}=${pair._2}").mkString("?", "&", "")
    val finalUrl         = URI.create(s"$serviceUrl/file$queryParamsFinal").toURL

    httpClientV2.get(url"$finalUrl").execute[HttpResponse]
  }

  def publishSafeFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2
      .post(url"$serviceUrl/file/$id/publish")
      .withBody(Json.toJson(FileMetadata(id, None, None)))
      .execute[HttpResponse]

  // Should NOT call S3 Upload
  def publishUnsafeFile(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClientV2
      .post(url"$serviceUrl/file/$id/publish")
      .withBody(Json.toJson(FileMetadata(id, None, None)))
      .execute[HttpResponse]

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

    httpClientV2
      .post(url"$serviceUrl/file/$id/notify")
      .withBody(Json.toJson(model))
      .execute[HttpResponse]
  }

  def notifyFailure(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val model =
      FailedScanResult(reference = "reference", failureDetails = FailureDetails(FailureReason.QUARANTINE, "message"))
    httpClientV2
      .post(url"$serviceUrl/file/$id/notify")
      .withBody(Json.toJson(model))
      .execute[HttpResponse]
  }

  def upload(id: Option[String], filename: String, contentType: String, publishable: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] = {
    stubUpscanInitiate
    stubUpscanUpload

    val tempFile = SingletonTemporaryFileCreator.create(filename)
    Files.write(tempFile.path, List("foo").asJava)

    httpClientV2
      .post(url"$serviceUrl/file")
      .withBody(
        Json.toJson(UploadRequest(id = id, fileName = filename, mimeType = contentType, publishable = publishable))
      )
      .execute[HttpResponse]
  }

  def initiateV2(id: Option[String] = None, publishable: Boolean)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    stubUpscanInitiateV2

    httpClientV2
      .post(url"$serviceUrl/file/initiate")
      .withBody(Json.toJson(FileStoreInitiateRequest(id = id, publishable = publishable)))
      .execute[HttpResponse]
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

  def dbFileStoreSize: Int =
    await(repository.collection.countDocuments().toFuture()).toInt

}
