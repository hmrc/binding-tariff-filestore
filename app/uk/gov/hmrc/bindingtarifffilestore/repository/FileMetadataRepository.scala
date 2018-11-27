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
import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.repository.MongoIndexCreator.{createSingleFieldAscendingIndex, createTTLIndex}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[FileMetadataMongoRepository])
trait FileMetadataRepository {

  def get(id: String): Future[Option[FileMetadata]]

  def insert(att: FileMetadata): Future[FileMetadata]

  def update(att: FileMetadata): Future[Option[FileMetadata]]

  // TODO: delete not needed - we will use 7 days TTL on mongo config
}

@Singleton
class FileMetadataMongoRepository @Inject()(config: AppConfig,
                                            mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[FileMetadata, BSONObjectID](
    collectionName = "fileMetadata",
    mongo = mongoDbProvider.mongo,
    domainFormat = FileMetadata.format,
    idFormat = ReactiveMongoFormats.objectIdFormats) with FileMetadataRepository {

  lazy private val uniqueSingleFieldIndexes = Seq("id")

  override def indexes: Seq[Index] = {
    val ttlIndex: Index = createTTLIndex(config.getInt("mongo.timeToLiveInSeconds"))
    uniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique = true)) :+ ttlIndex
  }

  override def get(id: String): Future[Option[FileMetadata]] = {
    collection.find(byId(id)).one[FileMetadata]
  }

  override def insert(att: FileMetadata): Future[FileMetadata] = {
    collection.insert(att).map(_ => att)
  }

  override def update(att: FileMetadata): Future[Option[FileMetadata]] = {
    collection.findAndUpdate(
      selector = byId(att.id),
      update = att,
      fetchNewObject = true,
      upsert = false
    ).map(_.value.map(_.as[FileMetadata]))
  }

  private def byId(id: String) = {
    Json.obj("id" -> id)
  }

}
