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
import uk.gov.hmrc.bindingtarifffilestore.model.Pagination.{defaultPageSize, defaultPageStart}

case class Pagination(
  page: Int = defaultPageStart,
  pageSize: Int = defaultPageSize
)

object Pagination {
  val defaultPageStart = 1
  val defaultPageSize  = 100

  val max: Pagination = Pagination(1, Integer.MAX_VALUE)

  private val pageKey     = "page"
  private val pageSizeKey = "page_size"

  implicit def bindable(implicit intBinder: QueryStringBindable[Int]): QueryStringBindable[Pagination] =
    new QueryStringBindable[Pagination] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Pagination]] = {
        def param(name: String): Option[Int] = intBinder.bind(name, params).filter(_.isRight).map(_.toOption.get)

        val page: Option[Int]     = param(pageKey).filter(_ > 0)
        val pageSize: Option[Int] = param(pageSizeKey)

        (page, pageSize) match {
          case (Some(p), Some(s)) => Some(Right(Pagination(page = p, pageSize = s)))
          case (Some(p), _)       => Some(Right(Pagination(page = p)))
          case (_, Some(s))       => Some(Right(Pagination(pageSize = s)))
          case _                  => None
        }
      }

      override def unbind(key: String, query: Pagination): String =
        Seq[String](
          intBinder.unbind(pageKey, query.page),
          intBinder.unbind(pageSizeKey, query.pageSize)
        ).mkString("&")

    }

}
