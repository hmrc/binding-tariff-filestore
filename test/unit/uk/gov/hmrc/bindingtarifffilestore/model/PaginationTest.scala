/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec

class PaginationTest extends UnitSpec {

  private val pagination = Pagination(
    page = 2,
    pageSize = 11
  )

  private val params: Map[String, Seq[String]] = Map(
    "page"      -> Seq("2"),
    "page_size" -> Seq("11")
  )

  "Pagination Binder" should {

    "Unbind Populated Sort to Query String" in {
      val populatedQueryParam: String = "page=2&page_size=11"
      Pagination.bindable.unbind("", pagination) shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      Pagination.bindable.bind("", Map()) shouldBe None
    }

    "Bind populated query string" in {
      Pagination.bindable.bind("", params) shouldBe Some(Right(pagination))
    }

    "Bind page only" in {
      Pagination.bindable.bind("", Map("page" -> Seq("10"))) shouldBe Some(Right(Pagination(page = 10)))
    }

    "Bind page_size only" in {
      Pagination.bindable.bind("", Map("page_size" -> Seq("10"))) shouldBe Some(Right(Pagination(pageSize = 10)))
    }

    "Ignore page <1" in {
      Pagination.bindable.bind("", Map("page" -> Seq("0"))) shouldBe None
    }

  }

}
