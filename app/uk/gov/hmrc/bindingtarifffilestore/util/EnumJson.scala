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

package uk.gov.hmrc.bindingtarifffilestore.util

import play.api.libs.json._

import scala.language.implicitConversions
import scala.util.Try

object EnumJson {

  private def enumReads[E <: Enumeration](`enum`: E): Reads[E#Value] = {
    case JsString(s) =>
      Try(JsSuccess(enum.withName(s))).recover { case _: NoSuchElementException =>
        JsError(
          s"Expected an enumeration of type: '${enum.getClass.getSimpleName}', but it does not contain the name: '$s'"
        )
      }.get

    case _ => JsError("String value is expected")
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = (v: E#Value) => JsString(v.toString)

  implicit def format[E <: Enumeration](`enum`: E): Format[E#Value] = Format(enumReads(enum), enumWrites)

}
