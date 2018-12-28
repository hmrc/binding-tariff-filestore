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

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.apache.http.HttpStatus
import play.api.Logger
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileWithMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{UploadRequestTemplate, UploadSettings, UpscanInitiateResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanConnector @Inject()(appConfig: AppConfig, http: HttpClient, ws: WSClient)(
  implicit executionContext: ExecutionContext) {

  def initiate(uploadSettings: UploadSettings)
              (implicit headerCarrier: HeaderCarrier): Future[UpscanInitiateResponse] = {
    http.POST[UploadSettings, UpscanInitiateResponse](s"${appConfig.upscanInitiateUrl}/upscan/initiate", uploadSettings)
  }

  def upload(template: UploadRequestTemplate, fileWithMetaData: FileWithMetadata)
            (implicit headerCarrier: HeaderCarrier): Future[Unit] = {
    Logger.info(s"Uploading file [${fileWithMetaData.metadata.id}] with template [$template]")
    val dataParts: List[DataPart] = List(
      DataPart("x-amz-meta-callback-url", template.fields("x-amz-meta-callback-url")),
      DataPart("x-amz-date", template.fields("x-amz-date")),
      DataPart("x-amz-credential", template.fields("x-amz-credential")),
      DataPart("x-amz-meta-original-filename", template.fields("x-amz-meta-original-filename")),
      DataPart("x-amz-algorithm", template.fields("x-amz-algorithm")),
      DataPart("key", template.fields("key")),
      DataPart("acl", template.fields("acl")),
      DataPart("x-amz-signature", template.fields("x-amz-signature")),
      DataPart("x-amz-meta-session-id", template.fields("x-amz-meta-session-id")),
      DataPart("x-amz-meta-request-id", template.fields("x-amz-meta-request-id")),
      DataPart("x-amz-meta-consuming-service", template.fields("x-amz-meta-consuming-service")),
      DataPart("x-amz-meta-upscan-initiate-received", template.fields("x-amz-meta-upscan-initiate-received")),
      DataPart("x-amz-meta-upscan-initiate-response", template.fields("x-amz-meta-upscan-initiate-response")),
      DataPart("policy", template.fields("policy"))
    )

    val filePart: MultipartFormData.Part[Source[ByteString, Future[IOResult]]] = FilePart(
      "file",
      fileWithMetaData.metadata.fileName,
      Some(fileWithMetaData.metadata.mimeType),
      FileIO.fromPath(fileWithMetaData.file.file.toPath)
    )

    ws.url(template.href)
      .post(Source(dataParts :+ filePart))
      .map {
        case response: WSResponse if response.status == HttpStatus.SC_OK =>
          Logger.info(s"Uploaded file [${fileWithMetaData.metadata.id}] successfully to Upscan Bucket [${template.href}]")
        case response: WSResponse =>
          Logger.info(s"Bad AWS response for file [${fileWithMetaData.metadata.id}] with status [${response.status}] body [${response.body}]")
      }
  }

}
