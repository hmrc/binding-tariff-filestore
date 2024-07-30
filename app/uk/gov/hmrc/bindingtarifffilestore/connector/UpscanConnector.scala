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

package uk.gov.hmrc.bindingtarifffilestore.connector

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileWithMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{UploadSettings, UpscanInitiateResponse, UpscanTemplate, v2}
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.nio.file.Files
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanConnector @Inject() (appConfig: AppConfig, httpClient: HttpClientV2)(implicit
  executionContext: ExecutionContext
) extends Logging {

  def initiate(uploadSettings: UploadSettings)(implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] =
    httpClient
      .post(url"${appConfig.upscanInitiateUrl}/upscan/initiate")
      .withBody(Json.toJson(uploadSettings))
      .execute[UpscanInitiateResponse]

  def initiateV2(
    uploadRequest: v2.UpscanInitiateRequest
  )(implicit hc: HeaderCarrier): Future[v2.UpscanInitiateResponse] =
    httpClient
      .post(url"${appConfig.upscanInitiateUrl}/upscan/v2/initiate")
      .withBody(Json.toJson(uploadRequest))
      .execute[v2.UpscanInitiateResponse]

  def upload(template: UpscanTemplate, fileWithMetaData: FileWithMetadata)(implicit hc: HeaderCarrier): Future[Unit] = {
    log.info(s"Uploading file [${fileWithMetaData.metadata.id}] with template [$template]")

    val dataParts: Seq[MultipartFormData.DataPart] =
      template.fields.map(entry => MultipartFormData.DataPart(entry._1, entry._2)).toSeq

    val filePart: Seq[MultipartFormData.FilePart[Source[ByteString, _]]] = Seq(
      MultipartFormData.FilePart(
        key = "file",
        filename = fileWithMetaData.metadata.fileName.getOrElse(fileWithMetaData.file.path.getFileName.toString),
        contentType =
          fileWithMetaData.metadata.mimeType.flatMap(typ => Option(typ)).orElse(Option("application/octet-stream")),
        ref = Source.single(ByteString(Files.readAllBytes(fileWithMetaData.file.path)))
      )
    )

    httpClient
      .post(url"${template.href}")
      .withBody(Source(dataParts ++ filePart))
      .execute[HttpResponse]
      .flatMap { response =>
        if (response.status >= 200 && response.status < 300) {
          log.info(s"Uploaded file [${fileWithMetaData.metadata.id}] successfully to Upscan Bucket [${template.href}]")
          Future.successful(())
        } else {
          Future.failed(
            new RuntimeException(
              s"Bad AWS response for file [${fileWithMetaData.metadata.id}] with status [${response.status}] body [${response.body}]"
            )
          )
        }
      }
  }

}
