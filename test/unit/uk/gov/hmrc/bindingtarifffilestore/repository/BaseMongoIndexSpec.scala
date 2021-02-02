/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.repository

import reactivemongo.api.indexes.Index
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

trait BaseMongoIndexSpec extends UnitSpec {

  protected implicit val ordering: Ordering[Index] = Ordering.by { i: Index => i.name }

  protected def getIndexes(collection: JSONCollection): List[Index] = {
    await(collection.indexesManager.list())
  }

  protected def assertIndexes(expectedIndexes: List[Index], actualIndexes: List[Index]): Unit = {
    actualIndexes.size shouldBe expectedIndexes.size

    for (i <- actualIndexes.size) {
      val expectedIndex = expectedIndexes(i)
      val actualIndex = actualIndexes(i)

      assertIndex(expectedIndex, actualIndex)
    }
  }

  private def assertIndex(expectedIndex: Index, actualIndex: Index): Unit = {
    actualIndex.key shouldBe expectedIndex.key
    actualIndex.name shouldBe expectedIndex.name
    actualIndex.unique shouldBe expectedIndex.unique
    actualIndex.background shouldBe expectedIndex.background
    actualIndex.dropDups shouldBe expectedIndex.dropDups
    actualIndex.sparse shouldBe expectedIndex.sparse
    actualIndex.partialFilter shouldBe expectedIndex.partialFilter
    actualIndex.options shouldBe expectedIndex.options
    actualIndex.eventualName shouldBe expectedIndex.eventualName
  }

}
