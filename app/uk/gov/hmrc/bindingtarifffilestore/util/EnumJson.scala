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

package uk.gov.hmrc.bindingtarifffilestore.util

import play.api.libs.json._

import scala.language.implicitConversions
import scala.util.Try

object EnumJson {

  private def enumReads[E <: Enumeration](customEnum: E): Reads[customEnum.Value] = {
    case JsString(s) =>
      Try(JsSuccess(customEnum.withName(s))).recover { case _: NoSuchElementException =>
        JsError(
          s"Expected an enumeration of type: '${customEnum.getClass.getSimpleName}', but it does not contain the name: '$s'"
        )
      }.get

    case _ => JsError("String value is expected")
  }

  private def enumWrites[E <: Enumeration, V <: Enumeration#Value]: Writes[V] =
    Writes((v: V) => JsString(v.toString))

  implicit def format[E <: Enumeration](customEnum: E): Format[customEnum.Value] =
    Format(enumReads(customEnum), enumWrites[E, customEnum.Value])
}
