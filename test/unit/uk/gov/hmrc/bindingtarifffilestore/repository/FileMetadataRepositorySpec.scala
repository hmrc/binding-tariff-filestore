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

import java.util.UUID

import org.mockito.BDDMockito.given
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadataMongo.format
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class FileMetadataRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually
  with MockitoSugar {
  self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val att1 = generateAttachment
  private val att2 = generateAttachment
  private val config = mock[AppConfig]
  private val repository = createMongoRepo


  private def createMongoRepo = {
    new FileMetadataMongoRepository(config, mongoDbProvider)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    given(config.getInt("mongodb.timeToLiveInSeconds")).willReturn(10)
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  "deleteAll()" should {

    "clear the collection" in {
      val size = collectionSize

      await(repository.insert(att1))
      await(repository.insert(att2))
      collectionSize shouldBe 2 + size

      await(repository.deleteAll()) shouldBe ((): Unit)
      collectionSize shouldBe 0
    }

  }

  "insert" should {

    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insert(att1)) shouldBe att1
      collectionSize shouldBe 1 + size
      await(repository.collection.find(selectorById(att1)).one[FileMetadata]) shouldBe Some(att1)
    }

    "fail to insert an existing document in the collection" in {
      await(repository.insert(att1)) shouldBe att1
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(att1))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      await(repository.insert(att1)) shouldBe att1
      val size = collectionSize

      val updated = att1.copy(mimeType = generateString, fileName = generateString)
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

      await(repository.insert(att1))
      await(repository.insert(att2))
      collectionSize shouldBe 2

      await(repository.get(att1.id)) shouldBe Some(att1)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "delete" should {

    "delete the expected document from the collection" in {
      await(repository.insert(att1))
      await(repository.insert(att2))
      collectionSize shouldBe 2

      await(repository.delete(att1.id))
      collectionSize shouldBe 1
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "The collection" should {

    "have a unique index based on the field 'id' " in {
      await(repository.insert(att1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {

        await(repository.insert(att1.copy(url = Some(generateString))))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("id" -> Ascending), name = Some("id_Index"), unique = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_")),
        Index(
          key = Seq("lastUpdated" -> IndexType.Ascending),
          name = Some("expiry"),
          options = BSONDocument("expireAfterSeconds" -> 10)
        )
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }
  }

  "getAll by id list" should {

    "retrieve the expected documents from the collection" in {

      await(repository.insert(att1))
      await(repository.insert(att2))
      collectionSize shouldBe 2

      await(repository.get(Seq(att2.id))) should contain only att2
    }

    "retrieve all the files when no ids are passed" in {

      await(repository.insert(att1))
      await(repository.insert(att2))
      collectionSize shouldBe 2

      await(repository.get(Seq.empty)) should contain only (att1, att2)
    }

    "return None when there are no documents in the collection" in {
      await(repository.get(Seq(att1.id, att2.id))) shouldBe Seq.empty
    }

  }

  private def generateAttachment = FileMetadata(
    id = generateString,
    fileName = generateString,
    mimeType = generateString
  )

  private def generateString = UUID.randomUUID().toString

  private def selectorById(att: FileMetadata) = {
    BSONDocument("id" -> att.id)
  }

}
