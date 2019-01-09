/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.Files
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST._
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@Singleton
class FileStoreController @Inject()(service: FileStoreService) extends BaseController {

  def upload: Action[MultipartFormData[Files.TemporaryFile]] = Action.async(parse.multipartFormData) { implicit request =>
    val attachment: Option[FileWithMetadata] = request.body.file("file") map { file =>
      FileWithMetadata(
        file.ref,
        FileMetadata(
          fileName = file.filename,
          mimeType = file.contentType.getOrElse(throw new RuntimeException("Missing file type"))
        )
      )
    }

    attachment
      .map( service.upload(_).map(f => Accepted(Json.toJson(f))) )
      .getOrElse(successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid File"))))
  }

  def get(id: String): Action[AnyContent] = Action.async { implicit request =>
    handleNotFound(id, (att: FileMetadata) => successful(Ok(Json.toJson(att))))
  }

  def notification(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ScanResult] { scanResult =>
      handleNotFound(id, (att: FileMetadata) => service.notify(att, scanResult).map(f => Created(Json.toJson(f))))
    }
  }

  def publish(id: String): Action[AnyContent] = Action.async { implicit request =>
    handleNotFound(id, (att: FileMetadata) =>
      service.publish(att).map {
        case Some(metadata) => Accepted(Json.toJson(metadata))
        case None => NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "File Not Found"))
      }
    )
  }

  def getFiles(ids: Option[Seq[String]]): Action[AnyContent] = Action.async { implicit request =>
    service.getByIds(ids.getOrElse(Seq.empty)) map {
      fileMetadataObjects: Seq[FileMetadata] => Ok(Json.toJson(fileMetadataObjects))
    }
  }

  private def handleNotFound(id: String, result: FileMetadata => Future[Result]): Future[Result] = {
    service.getById(id) flatMap {
      case Some(att: FileMetadata) => result(att)
      case _ => successful(NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "File Not Found")))
    }
  }

}
