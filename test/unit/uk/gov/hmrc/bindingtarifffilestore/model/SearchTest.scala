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

import java.net.URLDecoder

class SearchTest extends UnitSpec {

  private val search = Search(
    ids       = Some(Set("file-id1", "file-id2")),
    published = Some(true)
  )

  private val params = Map[String, Seq[String]](
    "id"        -> Seq("file-id1", "file-id2"),
    "published" -> Seq("true")
  )

  "Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      Search.bindable.unbind("", Search()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "id=file-id1&id=file-id2&published=true"
      URLDecoder.decode(Search.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      Search.bindable.bind("", Map()) shouldBe Some(Right(Search()))
    }

    "Bind query string with empty values" in {
      Search.bindable.bind("", params.mapValues(_.map(_ => ""))) shouldBe Some(Right(Search()))
    }

    "Bind populated query string" in {
      Search.bindable.bind("", params) shouldBe Some(Right(search))
    }
  }

}
