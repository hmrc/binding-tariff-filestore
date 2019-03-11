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

package uk.gov.hmrc.bindingtarifffilestore.filters

import java.security.MessageDigest

import akka.stream.Materializer
import com.google.common.io.BaseEncoding
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig

import scala.concurrent.Future

@Singleton
class AuthFilter @Inject()(appConfig: AppConfig)(implicit override val mat: Materializer) extends Filter {

  private lazy val authTokenName = "X-Api-Token"
  private lazy val healthEndpointUri = "/ping/ping"
  private lazy val hashedTokenValue: String = hash(appConfig.authorization)

  private def hash: String => String = { s: String =>
    BaseEncoding.base64Url().encode(
      MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))
    )
  }

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {

    rh.uri match {
      case uri if uri.contains(healthEndpointUri) => f(rh)
      case _ => ensureAuthTokenIsPresent(f, rh)
    }
  }

  private def ensureAuthTokenIsPresent(f: RequestHeader => Future[Result], rh: RequestHeader) = {

    val headerValue: Option[String] = rh.headers.get(authTokenName)
    val hashedQueryParamValues: Option[String] = rh.queryString.get(authTokenName).map(_.head)

    (headerValue, hashedQueryParamValues) match {
      case (Some(appConfig.authorization), Some(`hashedTokenValue`)) => f(rh)
      case (Some(appConfig.authorization), None) => f(rh)
      case (None, Some(`hashedTokenValue`)) => f(rh)
      case _ => Future.successful(Results.Forbidden)
    }
  }
}
