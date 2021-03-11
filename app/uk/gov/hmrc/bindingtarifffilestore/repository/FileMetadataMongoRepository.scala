/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsBoolean, JsObject, JsValue, Json}
import reactivemongo.api.indexes.Index
import reactivemongo.api.{Cursor, QueryOpts}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataMongo.format
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.repository.MongoIndexCreator.createSingleFieldAscendingIndex
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileMetadataMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[FileMetadata, BSONObjectID](
    collectionName = "fileMetadata",
    mongo = mongoDbProvider.mongo,
    domainFormat = FileMetadataMongo.format) {

  override lazy val indexes: Seq[Index] = Seq(
    createSingleFieldAscendingIndex("id", isUnique = true)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = for {
    status <- Future.sequence(indexes.map(collection.indexesManager.ensure(_)))
    _ = collection.indexesManager.list().foreach { _.foreach { index =>
      Logger.info(s"Running with Index: [$index] with options [${Json.toJson(index.options)}]")
    }}
  } yield status

  def get(id: String)(implicit ec: ExecutionContext): Future[Option[FileMetadata]] = {
    collection.find(byId(id)).one[FileMetadata]
  }

  def get(search: Search, pagination: Pagination)(implicit ec: ExecutionContext): Future[Paged[FileMetadata]] = {
    val query = JsObject(
      Map[String, JsValue]()
        ++ search.ids.map(ids => "id" -> Json.obj("$in" -> ids))
        ++ search.published.map(pub => "published" -> JsBoolean(pub))
    )

    for {
      results <- collection.find(query)
        .options(QueryOpts(skipN = (pagination.page - 1) * pagination.pageSize, batchSizeN = pagination.pageSize))
        .cursor[FileMetadata]()
        .collect[Seq](pagination.pageSize, Cursor.FailOnError[Seq[FileMetadata]]())
      count <- collection.count(Some(query))
    } yield Paged(results, pagination, count)
  }

  def insertFile(att: FileMetadata)(implicit ec: ExecutionContext): Future[FileMetadata] = {
    collection.findAndUpdate(
      selector = byId(att.id),
      update = att,
      fetchNewObject = true,
      upsert = true
    ).map(_.value.map(_.as[FileMetadata](FileMetadataMongo.format)).get)
  }

  def update(att: FileMetadata)(implicit ec: ExecutionContext): Future[Option[FileMetadata]] = {
    collection.findAndUpdate(
      selector = byId(att.id),
      update = att.copy(lastUpdated = Instant.now()),
      fetchNewObject = true,
      upsert = false
    ).map(_.value.map(_.as[FileMetadata](FileMetadataMongo.format)))
  }

  def delete(id: String)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.findAndRemove(byId(id)).map(_ => ())
  }

  def deleteAll()(implicit ec: ExecutionContext): Future[Unit] = {
    removeAll().map(_ => ())
  }

  private def byId(id: String): JsObject = {
    Json.obj("id" -> id)
  }

}
