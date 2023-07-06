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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model._
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileMetadataMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[FileMetadata](
      collectionName = "fileMetadata",
      mongoComponent = mongoComponent,
      domainFormat = FileMetadataMongo.format,
      indexes = Seq(
        IndexModel(Indexes.ascending("id"), IndexOptions().name("id_Index").unique(true))
      )
    )
    with Logging {

  override lazy val requiresTtlIndex: Boolean = false

  def get(id: String): Future[Option[FileMetadata]] =
    collection.find[FileMetadata](byId(id)).first().toFutureOption()

  def get(search: Search, pagination: Pagination): Future[Paged[FileMetadata]] = {
    val optionalIdsFilter       = search.ids.map(p => in("id", p.toSeq: _*))
    val optionalPublishedFilter = search.published.map(p => equal("published", p))

    val filters = Seq(optionalIdsFilter, optionalPublishedFilter).flatten

    val query = if (filters.isEmpty) {
      empty()
    } else {
      and(filters: _*)
    }

    collection
      .find(query)
      .skip((pagination.page - 1) * pagination.pageSize)
      .limit(pagination.pageSize)
      .toFuture()
      .map(results => Paged(results, pagination, results.size))
  }

  def insertFile(att: FileMetadata): Future[Option[FileMetadata]] = {
    val insertedMetadata = collection
      .replaceOne(byId(att.id), att, ReplaceOptions().upsert(true))
      .toFuture()

    insertedMetadata.flatMap(_ => collection.find[FileMetadata](byId(att.id)).first().toFutureOption())
  }

  def update(att: FileMetadata): Future[Option[FileMetadata]] = {
    val updatedMetadata = collection
      .replaceOne(byId(att.id), att.copy(lastUpdated = Instant.now()), ReplaceOptions().upsert(false))
      .toFutureOption()

    updatedMetadata.flatMap(_ => collection.find[FileMetadata](byId(att.id)).first().toFutureOption())
  }

  def delete(id: String)(implicit ec: ExecutionContext): Future[Unit] =
    collection.deleteOne(byId(id)).toFuture().map(_ => ())

  def deleteAll()(implicit ec: ExecutionContext): Future[Unit] =
    collection.deleteMany(empty()).toFuture().map(_ -> Future.unit)

  private def byId(id: String): Bson                           =
    equal("id", id)

}
