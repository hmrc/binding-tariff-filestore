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

package uk.gov.hmrc.bindingtarifffilestore.model

import play.api.mvc.QueryStringBindable

case class Search
(
  ids: Option[Set[String]] = None
)

object Search {
  private val idKey = "id"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Search] = new QueryStringBindable[Search] {
    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, Search]] = {
      def params(name: String): Option[Set[String]] = {
        requestParams.get(name).map(_.flatMap(_.split(",")).toSet).filter(_.exists(_.nonEmpty))
      }

      def param(name: String): Option[String] = {
        params(name).map(_.head)
      }

      Some(Right(Search(
        ids = params(idKey)
      )))
    }

    override def unbind(key: String, search: Search): String = {
      Seq(
        search.ids.map(_.map(stringBinder.unbind(idKey, _)).mkString(","))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }

}
