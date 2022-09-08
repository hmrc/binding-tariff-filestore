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

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtarifffilestore.model.{FileMetadata, Paged, Pagination, Search}
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.mongo.test.MongoSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class FileMetadataRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport
    with Eventually
    with MockitoSugar
    with Logging {
  self =>

  private lazy val att1  = generateAttachment
  private lazy val att2  = generateAttachment
  private val repository = createMongoRepo

  private def createMongoRepo =
    new FileMetadataMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.deleteAll())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.deleteAll())
  }

  private def currentCollectionSize: Int =
    await(repository.collection.countDocuments(Filters.notEqual("id", "")).toFuture()).toInt

  private def insertFileWithAssert(file: FileMetadata): Assertion =
    await(repository.insertFile(file)) shouldBe Some(file)

  private def insertFilesWithAssert(files: FileMetadata*): Assertion = {
    val beforeSize = currentCollectionSize
    files.foreach { file =>
      log.logger.info(s"Insert file in database => $file")
      insertFileWithAssert(file)
    }
    val afterSize  = currentCollectionSize

    eventually(timeout(5.second), interval(50.milliseconds)) {
      (afterSize - beforeSize) shouldBe files.size
    }
  }

  private def generateAttachment = FileMetadata(
    id = generateString,
    fileName = Some(generateString),
    mimeType = Some(generateString)
  )

  private def generateString = UUID.randomUUID().toString

  "deleteAll()" should {

    "clear the collection" in {
      insertFilesWithAssert(att1, att2)

      await(repository.deleteAll) shouldBe ((): Unit)
      currentCollectionSize       shouldBe 0
    }

  }

  "insert" should {

    "insert a new document in the collection" in {
      insertFilesWithAssert(att1)

      await(repository.get(att1.id)) shouldBe Some(att1)
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      insertFilesWithAssert(att1)
      val size = currentCollectionSize

      val updated = att1.copy(mimeType = Some(generateString), fileName = Some(generateString))
      await(repository.update(updated))
      currentCollectionSize shouldBe size

      val metadata = await(repository.get(att1.id))
      metadata.map(_.id)                                           shouldBe Some(att1.id)
      metadata.map(_.mimeType)                                     shouldBe Some(updated.mimeType)
      metadata.map(_.fileName)                                     shouldBe Some(updated.fileName)
      metadata.map(_.lastUpdated).get.isAfter(updated.lastUpdated) shouldBe true
    }

    "do nothing when trying to update a non existing document in the collection" in {
      val size = currentCollectionSize

      await(repository.update(att1)) shouldBe None
      currentCollectionSize          shouldBe size
    }
  }

  "get" should {

    "retrieve the expected document from the collection" in {
      insertFilesWithAssert(att1, att2)

      await(repository.get(att1.id)) shouldBe Some(att1)
      await(repository.get(att2.id)) shouldBe Some(att2)
    }

    "return None when there are no documents in the collection" in {
      await(repository.deleteAll())
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "delete" should {

    "delete the expected document from the collection" in {
      insertFilesWithAssert(att1, att2)

      await(repository.delete(att1.id))
      currentCollectionSize          shouldBe 1
      await(repository.get(att1.id)) shouldBe None
    }

  }

  "The collection" should {

    "have a unique index based on the field 'id' " in {
      insertFilesWithAssert(att1)
      val size = currentCollectionSize

      val caught = intercept[MongoWriteException] {
        await(repository.collection.insertOne(att1.copy(url = Some(generateString))).toFuture())
      }

      caught.getCode        shouldBe 11000
      currentCollectionSize shouldBe size
    }

    "have all expected indexes" in {
      val expectedIndexes = List(
        IndexModel(Indexes.ascending("id"), IndexOptions().name("id_Index").unique(true)),
        IndexModel(Indexes.ascending("_id"), IndexOptions().name("_id_"))
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.collection.drop)
    }
  }

  "get many" should {
    val pageOneSizeOne = Pagination(page = 1, pageSize = 1)
    val pageTwoSizeOne = Pagination(page = 2, pageSize = 1)

    "retrieve the expected documents by id" in {
      insertFilesWithAssert(att1, att2)

      await(repository.get(Search(ids = Some(Set(att1.id))), Pagination())) shouldBe Paged(Seq(att1))
      await(repository.get(Search(ids = Some(Set(att2.id))), Pagination())) shouldBe Paged(Seq(att2))
    }

    "retrieve the expected documents by published" in {
      insertFilesWithAssert(att1.copy(published = true), att2.copy(published = false))

      await(repository.get(Search(published = Some(true)), Pagination()))  shouldBe Paged(
        Seq(att1.copy(published = true))
      )
      await(repository.get(Search(published = Some(false)), Pagination())) shouldBe Paged(
        Seq(att2.copy(published = false))
      )
    }

    "retrieve the expected documents by page number and page size" in {
      insertFilesWithAssert(att1, att2)

      await(repository.get(Search(), pageOneSizeOne)) shouldBe Paged(Seq(att1), pageOneSizeOne, 1)
      await(repository.get(Search(), pageTwoSizeOne)) shouldBe Paged(Seq(att2), pageTwoSizeOne, 1)
    }

    "retrieve all the files for empty Search" in {
      insertFilesWithAssert(att1, att2)

      await(repository.get(Search(), Pagination())) shouldBe Paged(Seq(att1, att2))
    }

    "return None when there are no documents matching" in {
      await(repository.deleteAll())
      await(repository.get(Search(), Pagination())) shouldBe Paged.empty[FileMetadata]
    }

  }

}
