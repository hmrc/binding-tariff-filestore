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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtarifffilestore.model.TemporaryAttachment.format
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult
import uk.gov.hmrc.bindingtarifffilestore.model.{ErrorCode, JsErrorResponse, TemporaryAttachment}
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton()
class FileStoreController @Inject()(service: FileStoreService) extends BaseController {

  def listAllFiles: Action[AnyContent] = Action.async { implicit request =>
    service.getAll.map(attachments => Ok(Json.toJson(attachments)))
  }

  def upload: Action[AnyContent] = Action.async { implicit request =>
    throw new IllegalArgumentException
//    val attachment = request.body.file("file").map { file =>
//      TemporaryAttachment(
//        url = "",
//        fileName = file.filename,
//        mimeType = file.contentType.getOrElse(throw new RuntimeException("Unknown file type"))
//      )
//    }.getOrElse(throw new RuntimeException("Invalid upload"))
//
//    service.upload(attachment).map(att => Ok(Json.toJson(att)))
  }

  def get(id: String): Action[AnyContent] = Action.async { implicit request =>
    service.getById(id).map {
      case Some(att: TemporaryAttachment) => Ok(Json.toJson(att))
      case _ => NotFound()
    }
  }

  def notification(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ScanResult] { scanResult =>
      service.getById(id).flatMap {
        case Some(att: TemporaryAttachment) =>
          service
            .notify(att, scanResult) // TODO pull this from the request
            .map(attachment => Ok(Json.toJson(attachment)))
        case _ => Future.successful(NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "File Not Found")))
      }
    }
  }

}
