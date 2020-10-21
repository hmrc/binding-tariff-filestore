/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import java.util.UUID

import javax.inject.{Inject, Singleton}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.ErrorCode.NOTFOUND
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST._
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class FileStoreController @Inject()(
                                     appConfig: AppConfig,
                                     service: FileStoreService,
                                     parser: BodyParsers.Default,
                                     mcc: MessagesControllerComponents
                                   ) extends CommonController(mcc) {

  lazy private val testModeFilter = TestMode.actionFilter(appConfig, parser)

  def deleteAll(): Action[AnyContent] = testModeFilter.async {
    service.deleteAll() map (_ => NoContent) recover recovery
  }

  def delete(id: String): Action[AnyContent] = Action.async {
    service.delete(id) map (_ => NoContent) recover recovery
  }

  def upload: Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    if (request.contentType.contains("application/json")) {
      asJson[UploadRequest](initiate)
    } else if (request.contentType.contains("multipart/form-data")) {
      request.body
        .asMultipartFormData.map(upload)
        .getOrElse(successful(BadRequest))
    } else {
      successful(BadRequest("Content-Type must be one of [application/json, multipart/form-data]"))
    }
  }

  def get(id: String): Action[AnyContent] = Action.async {
    handleNotFound(id, (att: FileMetadata) => successful(Ok(Json.toJson(att))))
  }

  def notification(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ScanResult] { scanResult =>
      handleNotFound(id, (att: FileMetadata) => service.notify(att, scanResult).map(f => Created(Json.toJson(f)))) recover recovery
    }
  }

  def publish(id: String): Action[AnyContent] = Action.async { implicit request =>
    handleNotFound(id, (att: FileMetadata) =>
      service.publish(att) map {
        case Some(metadata) => Accepted(Json.toJson(metadata))
        case None => NotFound(JsErrorResponse(NOTFOUND, "File Not Found"))
      }
    ) recover recovery
  }

  def getAll(search: Search, pagination: Option[Pagination]): Action[AnyContent] = Action.async {
    service.find(search, pagination.getOrElse(Pagination.max)) map { pagedResults =>
      if(pagination.isDefined) {
        Ok(Json.toJson(pagedResults))
      } else {
        Ok(Json.toJson(pagedResults.results))
      }
    } recover recovery
  }

  private def initiate(template: UploadRequest)(implicit hc: HeaderCarrier): Future[Result] = {
    service.initiate(
      FileMetadata(
        id = template.id.getOrElse(UUID.randomUUID().toString),
        fileName = template.fileName,
        mimeType = template.mimeType,
        publishable = template.publishable
      )
    ).map(t => Accepted(Json.toJson(t))) recover recovery
  }

  private def upload(body: MultipartFormData[TemporaryFile])(implicit hc: HeaderCarrier): Future[Result] = {
    val formFile = body.file("file").filter(_.filename.nonEmpty)
    val publishable: Boolean = body.dataParts.getOrElse("publish", Seq.empty).contains("true")
    val id: String = body.dataParts.getOrElse("id", Seq.empty).headOption.getOrElse(UUID.randomUUID().toString)

    val attachment: Option[FileWithMetadata] = formFile map { file =>
      FileWithMetadata(
        file.ref,
        FileMetadata(
          id = id,
          fileName = file.filename,
          mimeType = file.contentType.getOrElse(throw new RuntimeException("Missing file type")),
          publishable = publishable
        )
      )
    }

    attachment
      .map(service.upload(_).map(f => Accepted(Json.toJson(f))))
      .getOrElse(successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid File")))) recover recovery
  }

  private def handleNotFound(id: String, result: FileMetadata => Future[Result]): Future[Result] = {
    service.find(id) flatMap {
      case Some(att: FileMetadata) => result(att)
      case _ => successful(NotFound(JsErrorResponse(NOTFOUND, "File Not Found")))
    }
  }

}
