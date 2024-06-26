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

package uk.gov.hmrc.bindingtarifffilestore.controllers

import play.api.Logging
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataREST._
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2._
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileStoreController @Inject() (
  appConfig: AppConfig,
  service: FileStoreService,
  parse: PlayBodyParsers,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(mcc)
    with ErrorHandling
    with JsonParsing
    with Logging {

  private lazy val FileNotFound =
    NotFound(JsErrorResponse(ErrorCode.NOTFOUND, "File Not Found"))

  private def withFileMetadata(id: String)(f: FileMetadata => Future[Result]): Future[Result] =
    service.find(id).flatMap {
      case Some(meta) =>
        f(meta)
      case None       =>
        Future.successful(FileNotFound)
    }

  lazy private val testModeFilter = TestMode.actionFilter(appConfig, parse.default)

  def deleteAll(): Action[AnyContent] = withErrorHandling {
    testModeFilter.async {
      service
        .deleteAll()
        .map(_ => NoContent)
    }
  }

  def delete(id: String): Action[AnyContent] = withErrorHandling { _ =>
    service
      .delete(id)
      .map(_ => NoContent)
  }

  def initiate: Action[AnyContent] = withErrorHandling {
    Action.async { implicit request =>
      asJson[FileStoreInitiateRequest] { fileStoreRequest =>
        service
          .initiateV2(fileStoreRequest)
          .map((response: FileStoreInitiateResponse) => Accepted(Json.toJson(response)))
      }
    }
  }

  def upload: Action[AnyContent] = withErrorHandling { implicit request =>
    if (request.contentType.contains("application/json")) {
      asJson[UploadRequest] { uploadRequest =>
        service
          .initiate(FileMetadata.fromUploadRequest(uploadRequest))
          .map(template => Accepted(Json.toJson(template)))
      }
    } else if (request.contentType.contains("multipart/form-data")) {
      request.body.asMultipartFormData
        .map(upload)
        .getOrElse(Future.successful(BadRequest))
    } else {
      Future.successful(BadRequest("Content-Type must be one of [application/json, multipart/form-data]"))
    }
  }

  def get(id: String): Action[AnyContent] = Action.async {
    withFileMetadata(id)(meta => Future.successful(Ok(Json.toJson(meta))))
  }

  def notification(id: String): Action[JsValue] = withErrorHandling {
    Action(parse.json).async { implicit req =>
      withJsonBody[ScanResult] { scanResult =>
        withFileMetadata(id) { meta =>
          service
            .notify(meta, scanResult)
            .map(updatedMeta => Created(Json.toJson(updatedMeta)))
        }
      }
    }
  }

  def publish(id: String): Action[AnyContent] = withErrorHandling { implicit request =>
    withFileMetadata(id) { meta =>
      service.publish(meta).map {
        case Some(updatedMeta) =>
          Accepted(Json.toJson(updatedMeta))
        case None              =>
          FileNotFound
      }
    }
  }

  def getAll(search: Search, pagination: Option[Pagination]): Action[AnyContent] = withErrorHandling { _ =>
    service.find(search, pagination.getOrElse(Pagination.max)).map { pagedResults =>
      if (pagination.isDefined) {
        Ok(Json.toJson(pagedResults))
      } else {
        Ok(Json.toJson(pagedResults.results))
      }
    }
  }

  private def upload(body: MultipartFormData[TemporaryFile])(implicit hc: HeaderCarrier): Future[Result] = {
    val formFile             = body.file("file").filter(_.filename.nonEmpty)
    val publishable: Boolean = body.dataParts.getOrElse("publish", Seq.empty).contains("true")
    val id: String           = body.dataParts.getOrElse("id", Seq.empty).headOption.getOrElse(UUID.randomUUID().toString)

    val attachment: Option[FileWithMetadata] = formFile map { file =>
      FileWithMetadata(
        file.ref,
        FileMetadata(
          id = id,
          fileName = Some(file.filename),
          mimeType = Some(file.contentType.getOrElse(throw new RuntimeException("Missing file type"))),
          publishable = publishable
        )
      )
    }

    attachment
      .map { fileWithMetadata =>
        service.upload(fileWithMetadata).map(f => Accepted(Json.toJson(f)))
      }
      .getOrElse(Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid File"))))
  }
}
