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

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileWithMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{UploadSettings, UpscanInitiateResponse, UpscanTemplate, v2}
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

  def upload(template: UpscanTemplate, fileWithMetaData: FileWithMetadata): Future[Unit] = {
    log.info(s"Uploading file [${fileWithMetaData.metadata.id}] with template [$template]")

    val builder: MultipartEntityBuilder = MultipartEntityBuilder.create

    template.fields.foreach(entry => builder.addPart(entry._1, new StringBody(entry._2, ContentType.TEXT_PLAIN)))

    builder.addPart(
      "file",
      new FileBody(
        fileWithMetaData.file.path.toFile,
        fileWithMetaData.metadata.mimeType
          .flatMap(typ => Option(ContentType.getByMimeType(typ)))
          .getOrElse(ContentType.DEFAULT_BINARY),
        fileWithMetaData.metadata.fileName
          .getOrElse(fileWithMetaData.file.path.getFileName.toString)
      )
    )

    val request: HttpPost = new HttpPost(template.href)
    request.setEntity(builder.build())

    val client = HttpClientBuilder.create.build

    val attempt = Try(client.execute(request)).map { (response: HttpResponse) =>
      val code = response.getStatusLine.getStatusCode
      if (code >= 200 && code < 300) {
        log.info(s"Uploaded file [${fileWithMetaData.metadata.id}] successfully to Upscan Bucket [${template.href}]")
        Future.successful((): Unit)
      } else {
        Future.failed(
          new RuntimeException(
            s"Bad AWS response for file [${fileWithMetaData.metadata.id}] with status [$code] body [${EntityUtils
                .toString(response.getEntity)}]"
          )
        )
      }
    }

    client.close()
    attempt.get
  }

}
