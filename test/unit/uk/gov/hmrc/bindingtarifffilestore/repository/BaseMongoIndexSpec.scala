/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.UnitSpec

import scala.collection.JavaConverters.asScalaSetConverter
import scala.concurrent.ExecutionContext.Implicits.global

trait BaseMongoIndexSpec extends UnitSpec {

  protected implicit val ordering: Ordering[IndexModel] = Ordering.by { i: IndexModel => i.toString }

  protected def getIndexes(collection: MongoCollection[FileMetadata]): Seq[IndexModel] =
    await(
      collection
        .listIndexes()
        .toFuture()
        .map(_.map { document =>
          val indexFields = document.get("key").map(_.asDocument().keySet().asScala).getOrElse(Set.empty[String]).toSeq
          val name        = document.getString("name")
          val isUnique    = document.getBoolean("unique", false)
          IndexModel(Indexes.ascending(indexFields: _*), IndexOptions().name(name).unique(isUnique))
        })
    )

  protected def assertIndexes(expectedIndexes: Iterable[IndexModel], actualIndexes: Iterable[IndexModel]): Unit = {
    actualIndexes.size shouldBe expectedIndexes.size

    expectedIndexes
      .zip(actualIndexes)
      .foreach { indexTuple =>
        val expectedIndex = indexTuple._1
        val actualIndex   = indexTuple._2

        assertIndex(expectedIndex, actualIndex)
      }
  }

  private def assertIndex(expectedIndex: IndexModel, actualIndex: IndexModel): Unit = {
    actualIndex.getKeys.toBsonDocument.keySet().asScala shouldBe expectedIndex.getKeys.toBsonDocument.keySet().asScala
    actualIndex.getKeys.toBsonDocument.toString         shouldBe expectedIndex.getKeys.toBsonDocument.toString

    actualIndex.getOptions.toString shouldBe expectedIndex.getOptions.toString
  }

}
