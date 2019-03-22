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

package uk.gov.hmrc.bindingtarifffilestore.repository

import java.time.Instant

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsBoolean, JsObject, JsValue, Json}
import reactivemongo.api.indexes.Index
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataMongo.format
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.repository.MongoIndexCreator.{createSingleFieldAscendingIndex, createTTLIndex}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FileMetadataMongoRepository])
trait FileMetadataRepository {

  def get(id: String): Future[Option[FileMetadata]]

  def get(search: Search, pagination: Pagination): Future[Paged[FileMetadata]]

  def insert(att: FileMetadata): Future[FileMetadata]

  def update(att: FileMetadata): Future[Option[FileMetadata]]

  def delete(id: String): Future[Unit]

  def deleteAll(): Future[Unit]
}

@Singleton
class FileMetadataMongoRepository @Inject()(config: AppConfig,
                                            mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[FileMetadata, BSONObjectID](
    collectionName = "fileMetadata",
    mongo = mongoDbProvider.mongo,
    domainFormat = FileMetadataMongo.format) with FileMetadataRepository {

  collection.indexesManager.drop("expiry_Index")

  override lazy val indexes: Seq[Index] = Seq(
    createSingleFieldAscendingIndex("id", isUnique = true)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = for {
    status <- Future.sequence(indexes.map(collection.indexesManager.ensure(_)))
    _ = collection.indexesManager.list().foreach(_.foreach { index =>
      Logger.info(s"Running with Index: [$index] with options [${Json.toJson(index.options)}]")
    })
  } yield status

  override def get(id: String): Future[Option[FileMetadata]] = {
    collection.find(byId(id)).one[FileMetadata]
  }

  override def get(search: Search, pagination: Pagination): Future[Paged[FileMetadata]] = {
    val query = JsObject(
      Map[String, JsValue]()
        ++ search.ids.map(ids => "id" -> Json.obj("$in" -> ids))
        ++ search.published.map(published => "published" -> JsBoolean(published))
    )

    for {
      results <- collection.find(query)
        .options(QueryOpts(skipN = (pagination.page - 1) * pagination.pageSize, batchSizeN = pagination.pageSize))
        .cursor[FileMetadata]()
        .collect[Seq](pagination.pageSize, Cursor.FailOnError[Seq[FileMetadata]]())
      count <- collection.count(Some(query))
    } yield Paged(results, pagination, count)
  }

  override def insert(att: FileMetadata): Future[FileMetadata] = {
    collection.findAndUpdate(
      selector = byId(att.id),
      update = att,
      fetchNewObject = true,
      upsert = true
    ).map(_.value.map(_.as[FileMetadata]).get)
  }

  override def update(att: FileMetadata): Future[Option[FileMetadata]] = {
    collection.findAndUpdate(
      selector = byId(att.id),
      update = att.copy(lastUpdated = Instant.now()),
      fetchNewObject = true,
      upsert = false
    ).map(_.value.map(_.as[FileMetadata]))
  }

  override def delete(id: String): Future[Unit] = {
    collection.findAndRemove(byId(id)).map(_ => Unit)
  }

  override def deleteAll(): Future[Unit] = {
    removeAll().map(_ => ())
  }

  private def byId(id: String) = {
    Json.obj("id" -> id)
  }

}
