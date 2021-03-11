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

import play.api.Logging
import play.api.mvc.{ Action, AnyContent, BaseController, Request, Result }
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtarifffilestore.model.{ ErrorCode, JsErrorResponse }

import scala.concurrent.{ ExecutionContext, Future }

trait ErrorHandling { self: BaseController with Logging =>
  private val DuplicateKeyError = 11000

  private[controllers] def mongoErrorHandler: PartialFunction[Throwable, Result] = {
    case e: DatabaseException if e.code.contains(DuplicateKeyError) =>
      Conflict(JsErrorResponse(ErrorCode.CONFLICT, "Entity already exists"))
    case e: Throwable =>
      logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
      InternalServerError(JsErrorResponse(ErrorCode.UNKNOWN_ERROR, "An unexpected error occurred"))
  }

  def withErrorHandling(f: Request[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { request: Request[AnyContent] =>
      f(request).recover(mongoErrorHandler)
    }

  def withErrorHandling[A](action: Action[A])(implicit ec: ExecutionContext): Action[A] =
    Action(action.parser).async { request =>
      action(request).recover(mongoErrorHandler)
    }
}