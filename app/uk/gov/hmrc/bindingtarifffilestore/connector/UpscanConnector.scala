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
import play.api.libs.ws.WSClient
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
            (implicit headerCarrier: HeaderCarrier): Future[Boolean] = {
    val dataParts: List[DataPart] = template.fields.map {
      case (key, value) => DataPart(key, value)
    } toList

    val filePart: MultipartFormData.Part[Source[ByteString, Future[IOResult]]] = FilePart(
      "file",
      fileWithMetaData.metadata.fileName,
      Some(fileWithMetaData.metadata.mimeType),
      FileIO.fromFile(fileWithMetaData.file.file)
    )
    ws.url(template.href).post(Source(filePart :: dataParts)).map(_ => true)
  }

}
