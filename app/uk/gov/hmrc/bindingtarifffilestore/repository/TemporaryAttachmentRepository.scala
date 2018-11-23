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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.bindingtarifffilestore.model.TemporaryAttachment
import uk.gov.hmrc.bindingtarifffilestore.repository.MongoIndexCreator.createSingleFieldAscendingIndex
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

@ImplementedBy(classOf[TemporaryAttachmentMongoRepository])
trait TemporaryAttachmentRepository {

  def get(id: String): Future[Option[TemporaryAttachment]]
  def insert(att: TemporaryAttachment): Future[TemporaryAttachment]
  def update(att: TemporaryAttachment): Future[TemporaryAttachment]
  // TODO: delete not needed - we will use 7 days TTL on mongo config
}

@Singleton
class TemporaryAttachmentMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[TemporaryAttachment, BSONObjectID](
    collectionName = "temporaryAttachment",
    mongo = mongoDbProvider.mongo,
    domainFormat = TemporaryAttachment.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) with TemporaryAttachmentRepository {

  lazy private val uniqueSingleFieldIndexes = Seq("id, url")

  override def indexes: Seq[Index] = {
    uniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique = true))
  }

  override def get(id: String): Future[Option[TemporaryAttachment]] = ???
  override def insert(att: TemporaryAttachment): Future[TemporaryAttachment] = ???
  override def update(att: TemporaryAttachment): Future[TemporaryAttachment] = ???
}
