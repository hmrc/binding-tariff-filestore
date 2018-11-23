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

import javax.inject.{Inject, Singleton}
import play.api
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileWithMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{UploadRequestTemplate, UploadSettings, UpscanInitiateResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanConnector @Inject()(appConfig: AppConfig, http: HttpClient)(
  implicit executionContext: ExecutionContext) {

  def initiate(uploadSettings: UploadSettings)
              (implicit headerCarrier: HeaderCarrier): Future[UpscanInitiateResponse] = {
    http.POST[UploadSettings, UpscanInitiateResponse](s"${appConfig.upscanInitiateUrl}/upscan/initiate", uploadSettings)
  }

  def upload(template: UploadRequestTemplate, file: FileWithMetadata)
            (implicit headerCarrier: HeaderCarrier): Future[Boolean] = {
    val formFile = new api.mvc.MultipartFormData.FilePart[TemporaryFile]("file", file.metadata.fileName, Some(file.metadata.mimeType), file.file)
    val params: Map[String, Seq[String]] = template.fields.map {
      case (key, value) => (key, Seq(value))
    }
    val form = new MultipartFormData[TemporaryFile](
      dataParts = params,
      files = Seq(formFile),
      badParts = Nil
    )
    http.POSTString[String](template.href, form).map(_ => true)
  }

}
