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

import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request, Result}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtarifffilestore.model.{ErrorCode, JsErrorResponse}
import uk.gov.hmrc.bindingtarifffilestore.model.ErrorCode._
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.{Failure, Success, Try}

class CommonController(
                        mcc: MessagesControllerComponents
                      ) extends BackendController(mcc) {

  private val uniqueIDExceptionMongo = 11000

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

  private[controllers] def recovery: PartialFunction[Throwable, Result] = {
    case e: DatabaseException if e.code.contains(uniqueIDExceptionMongo) =>
      Conflict(JsErrorResponse(ErrorCode.CONFLICT, "Entity already exists"))
    case e: Throwable =>
      Logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(JsErrorResponse(UNKNOWN_ERROR, "An unexpected error occurred"))
  }

}
