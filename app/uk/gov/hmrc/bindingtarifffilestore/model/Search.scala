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

package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.mvc.QueryStringBindable

import scala.util.Try

case class Search(
  ids: Option[Set[String]] = None,
  published: Option[Boolean] = None
)

object Search {
  private val idKey        = "id"
  private val publishedKey = "published"

  implicit def bindable(implicit
    stringBinder: QueryStringBindable[String],
    booleanBinder: QueryStringBindable[Boolean]
  ): QueryStringBindable[Search] = new QueryStringBindable[Search] {

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, Search]] = {
      def params[T](name: String, map: String => T): Option[Set[T]] =
        requestParams
          .get(name)
          .map {
            _.flatMap(_.split(",").filter(_.nonEmpty))
              .map(v => Try(map(v)))
              .filter(_.isSuccess)
              .map(_.get)
              .toSet
          }
          .filter(_.nonEmpty)

      def param[T](name: String, map: String => T): Option[T] =
        params(name, map).map(_.head)

      Some(
        Right(
          Search(
            ids = params(idKey, s => s),
            published = param(publishedKey, _.toBoolean)
          )
        )
      )
    }

    override def unbind(key: String, search: Search): String =
      Seq(
        search.ids.map(_.map(stringBinder.unbind(idKey, _)).mkString("&")),
        search.published.map(booleanBinder.unbind(publishedKey, _))
      ).filter(_.isDefined).map(_.get).mkString("&")
  }

}
