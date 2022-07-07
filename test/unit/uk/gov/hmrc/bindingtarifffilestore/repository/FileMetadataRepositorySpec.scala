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

package uk.gov.hmrc.bindingtarifffilestore.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{DB, ReadConcern}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataMongo.format
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, Paged, Pagination, Search}
import uk.gov.hmrc.mongo.MongoSpecSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class FileMetadataRepositorySpec
  extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually
    with MockitoSugar {
  self =>

  val readConcern: ReadConcern = ReadConcern.Local

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val att1 = generateAttachment
  private val att2 = generateAttachment
  private val repository = createMongoRepo

  private def createMongoRepo =
    new FileMetadataMongoRepository(mongoDbProvider)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int =
    await(repository.collection.count(None, Some(0), 0, None, readConcern = readConcern)).toInt

  "deleteAll()" should {

    "clear the collection" in {
      val size = collectionSize

      await(repository.insertFile(att1))
      await(repository.insertFile(att2))
      collectionSize shouldBe 2 + size

      await(repository.deleteAll) shouldBe ((): Unit)
      collectionSize shouldBe 0
    }

  }

  "insert" should {

    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insertFile(att1)) shouldBe att1
      collectionSize shouldBe 1 + size
      await(repository.collection.find(selectorById(att1)).one[FileMetadata]) shouldBe Some(att1)
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      await(repository.insertFile(att1))
      val size = collectionSize

      val updated = att1.copy(mimeType = Some(generateString), fileName = Some(generateString))
      await(repository.update(updated))
      collectionSize shouldBe size

      val metadata = await(repository.collection.find(selectorById(updated)).one[FileMetadata])
      metadata.map(_.id) shouldBe Some(att1.id)
      metadata.map(_.mimeType) shouldBe Some(updated.mimeType)
      metadata.map(_.fileName) shouldBe Some(updated.fileName)
      metadata.map(_.lastUpdated).get.isAfter(updated.lastUpdated) shouldBe true
    }

    "do nothing when trying to update a non existing document in the collection" in {
      val size = collectionSize

      await(repository.update(att1)) shouldBe None
      collectionSize shouldBe size
    }
  }

  "get" should {

    "retrieve the expected document from the collection" in {

      await(repository.insertFile(att1))
      await(repository.insertFile(att2))
      collectionSize shouldBe 2

      await(repository.get(att1.id)) shouldBe Some(att1)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "delete" should {

    "delete the expected document from the collection" in {
      await(repository.insertFile(att1))
      await(repository.insertFile(att2))
      collectionSize shouldBe 2

      await(repository.delete(att1.id))
      collectionSize shouldBe 1
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "The collection" should {

    "have a unique index based on the field 'id' " in {
      await(repository.collection.insert(att1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {

        await(repository.collection.insert(att1.copy(url = Some(generateString))))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("id" -> Ascending), name = Some("id_Index"), unique = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }
  }

  "get many" should {

    "retrieve the expected documents by id" in {
      await(repository.insertFile(att1))
      await(repository.insertFile(att2))
      collectionSize shouldBe 2

      await(repository.get(Search(ids = Some(Set(att1.id))), Pagination())) shouldBe Paged(Seq(att1))
      await(repository.get(Search(ids = Some(Set(att2.id))), Pagination())) shouldBe Paged(Seq(att2))
    }

    "retrieve the expected documents by published" in {
      await(repository.insertFile(att1.copy(published = true)))
      await(repository.insertFile(att2.copy(published = false)))
      collectionSize shouldBe 2

      await(repository.get(Search(published = Some(true)), Pagination())) shouldBe Paged(
        Seq(att1.copy(published = true))
      )
      await(repository.get(Search(published = Some(false)), Pagination())) shouldBe Paged(
        Seq(att2.copy(published = false))
      )
    }

    "retrieve all the files for empty Search" in {
      await(repository.insertFile(att1))
      await(repository.insertFile(att2))
      collectionSize shouldBe 2

      await(repository.get(Search(), Pagination())) shouldBe Paged(Seq(att1, att2))
    }

    "return None when there are no documents matching" in {
      await(repository.get(Search(), Pagination())) shouldBe Paged.empty[FileMetadata]
    }

  }

  private def generateAttachment = FileMetadata(
    id = generateString,
    fileName = Some(generateString),
    mimeType = Some(generateString)
  )

  private def generateString = UUID.randomUUID().toString

  private def selectorById(att: FileMetadata) =
    BSONDocument("id" -> att.id)

}
