/*
 * Copyright 2018 HM Revenue & Customs
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

import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.indexes.{Index, IndexType}

object MongoIndexCreator {

  def createSingleFieldAscendingIndex(indexFieldKey: String,
                                      isUnique: Boolean): Index = {

    createCompoundIndex(
      indexFieldMappings = Seq(indexFieldKey -> Ascending),
      isUnique = isUnique
    )
  }

  def createCompoundIndex(indexFieldMappings: Seq[(String, IndexType)],
                          isUnique: Boolean,
                          isBackground: Boolean = true): Index = {

    Index(
      key = indexFieldMappings,
      name = Some(s"${indexFieldMappings.toMap.keys.mkString("-")}_Index"),
      unique = isUnique,
      background = isBackground
    )
  }

}
