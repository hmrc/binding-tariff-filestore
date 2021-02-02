/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.bindingtarifffilestore.model.JsErrorResponse
import uk.gov.hmrc.bindingtarifffilestore.model.ErrorCode._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.{Failure, Success, Try}

trait JsonParsing { self: BackendBaseController =>
  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => successful(BadRequest(JsErrorResponse(INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
      case Failure(e) => successful(BadRequest(JsErrorResponse(UNKNOWN_ERROR, e.getMessage)))
    }
  }

  protected def asJson[T]
  (f: T => Future[Result])(implicit request: Request[AnyContent], reads: Reads[T]): Future[Result] = {
    Try(request.body.asJson.map(_.validate[T])) match {
      case Success(Some(JsSuccess(payload, _))) => f(payload)
      case Success(Some(JsError(errs))) => successful(BadRequest(JsErrorResponse(INVALID_REQUEST_PAYLOAD, JsError.toJson(errs))))
      case Success(None) => successful(BadRequest)
      case Failure(e) => successful(BadRequest(JsErrorResponse(UNKNOWN_ERROR, e.getMessage)))
    }
  }
}
