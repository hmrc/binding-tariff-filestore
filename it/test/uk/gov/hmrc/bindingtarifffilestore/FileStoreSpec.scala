/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore

import org.scalatest.concurrent.Eventually.eventually
import play.api.Application
import play.api.http.Status
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.{FAILED, READY}
import uk.gov.hmrc.bindingtarifffilestore.model._
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.v2.{FileStoreInitiateResponse, UpscanFormTemplate}
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.io.File
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class FileStoreSpec extends FileStoreHelpers {

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.deleteAll())
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    createBucket()
    await(deleteFiles())
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "s3.endpoint"                                -> "http://localhost:4572",
        "s3.bucket"                                  -> "digital-tariffs-local-test",
        "s3.region"                                  -> "eu-west-2",
        "microservice.services.upscan-initiate.port" -> s"$wirePort"
      )
      .overrides(bind[FileMetadataMongoRepository].to(repository))
      .build()

  val id1 = "doc_id_1"
  val id2 = "doc_id_2"

  val file1       = "some-file-1.txt"
  val file2       = "some-file-2.txt"
  val contentType = "text/plain"

  implicit val hc: HeaderCarrier =
    HeaderCarrier(extraHeaders = Seq(apiTokenKey -> appConfig.authorization))

  Feature("Delete") {

    Scenario("Delete the file") {

      Given("A file has been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))

      dbFileStoreSize shouldBe 1

      When("I request the file details")
      val deleteResult = Await.result(deleteFile(id1), 10.seconds)

      Then("The response code should be Ok")
      deleteResult.status shouldBe Status.NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      dbFileStoreSize shouldBe 0
    }
  }

  Feature("Delete All") {

    Scenario("Clear collections & files") {

      Given("There are some documents in the collection")

      await(upload(Some(id1), file1, contentType, publishable = true))
      await(upload(Some(id2), file2, contentType, publishable = true))

      dbFileStoreSize shouldBe 2

      When("I delete all documents")
      val deleteResult = Await.result(deleteFiles(), 10.seconds)

      Then("The response code should be 204")
      deleteResult.status shouldEqual Status.NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      dbFileStoreSize shouldBe 0

      And("the are no files")

      val fileResult = await(getFiles(Seq("id" -> id1, "id" -> id2)))

      fileResult.status shouldBe Status.OK
      fileResult.body   shouldBe "[]"
    }
  }

  Feature("Upload") {

    Scenario("Should persist") {

      Given("A Client of the FileStore has a file")

      When("It is uploaded")

      val response = upload(Some(id1), file1, contentType, publishable = true)

      Then("The response code should be Accepted")
      val result = await(response)
      result.status shouldBe Status.ACCEPTED
    }
  }

  Feature("Initiate") {

    Scenario("Should persist") {

      Given("A Client of the FileStore has a file")

      await(upload(Some(id1), file1, contentType, publishable = true))

      dbFileStoreSize shouldBe 1

      When("It is initiated")

      val initiateResponse = initiateV2(Some("fake_file_id"), publishable = true)

      Then("The response code should be Accepted")
      val result = await(initiateResponse)
      result.status shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")
      val fileStoreInitiateResponse = result.json.as[FileStoreInitiateResponse]

      fileStoreInitiateResponse.uploadRequest.href   shouldBe "http://localhost:20001/upscan/upload"
      fileStoreInitiateResponse.uploadRequest.fields shouldBe Map("key" -> "value")
    }
  }

  Feature("Initiate V2") {

    Scenario("Should accept initiate requests without ID") {

      Given("A Client of the FileStore needs an upload form")

      When("It is requested")

      val response: Future[HttpResponse] = initiateV2(publishable = true)

      Then("The response code should be Accepted")
      val result = await(response)
      result.status shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")

      val fileStoreInitiateResult = result.json.as[FileStoreInitiateResponse]

      fileStoreInitiateResult.uploadRequest.href   shouldBe "http://localhost:20001/upscan/upload"
      fileStoreInitiateResult.uploadRequest.fields shouldBe Map("key" -> "value")

    }

    Scenario("Should accept initiate requests with client generated ID") {

      Given("A Client of the FileStore needs an upload form")

      When("It is requested")

      val response = initiateV2(Some("slurm"), publishable = true)

      Then("The response code should be Accepted")

      val result = await(response)
      result.status shouldBe Status.ACCEPTED

      And("The response body contains the file upload template")

      result.json.as[FileStoreInitiateResponse].id shouldBe "slurm"

      result.json.as[FileStoreInitiateResponse].uploadRequest shouldBe
        UpscanFormTemplate("http://localhost:20001/upscan/upload", Map("key" -> "value"))
    }
  }

  Feature("Get") {

    Scenario("Should show the file is persisted") {

      Given("A file has been uploaded")

      val uploadResponse = upload(Some(id1), file1, contentType, publishable = true)
      val uploadResult   = await(uploadResponse)
      val id: String     = uploadResult.json.as[UploadTemplate].id

      When("I request the file details")
      val getFileResponse = getFile(id)

      Then("The response code should be Ok")
      val getFileResult = await(getFileResponse)
      getFileResult.status shouldBe Status.OK

      And("The response body contains the file details")

      getFileResult.json.as[FileMetadata].fileName shouldBe Some(file1)
      getFileResult.json.as[FileMetadata].mimeType shouldBe Some(contentType)
    }
  }

  Feature("Get files") {

    Scenario("Should return all files matching search") {

      Given("Files have been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))
      await(upload(Some(id2), file2, contentType, publishable = false))

      dbFileStoreSize shouldBe 2

      When("I request the file details")

      val getFilesResult = await(getFiles(Seq("id" -> id1, "id" -> id2)))

      Then("The response code should be Ok")

      getFilesResult.status shouldBe Status.OK

      And("The response body contains the file details")

      (getFilesResult.json \\ "fileName").map(_.as[String]).toSeq     shouldBe Seq(file1, file2)
      (getFilesResult.json \\ "mimeType").map(_.as[String]).toSeq     shouldBe Seq(contentType, contentType)
      (getFilesResult.json \\ "publishable").map(_.as[Boolean]).toSeq shouldBe Seq(true, false)
    }

    Scenario("Should return all files for empty search") {

      Given("Files have been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))
      await(upload(Some(id2), file2, contentType, publishable = false))

      dbFileStoreSize shouldBe 2

      When("I request the file details")

      val getFilesResult = await(getFiles(Seq()))

      Then("The response code should be Ok")
      getFilesResult.status shouldBe Status.OK

      And("The response body contains the file details")

      (getFilesResult.json \\ "fileName").map(_.as[String]).toSeq shouldBe Seq(file1, file2)
    }
  }

  Feature("Get files with pagination") {

    Scenario("Should return all files matching search") {

      Given("Files have been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))
      await(upload(Some(id2), file2, contentType, publishable = false))

      dbFileStoreSize shouldBe 2

      When("I request the file details")
      val getFilesResult = await(getFiles(Seq("id" -> id1, "id" -> id2)))

      Then("The response code should be Ok")
      getFilesResult.status shouldBe Status.OK

      And("The response body contains the file details")

      getFilesResult.json.asInstanceOf[JsArray].value.size        shouldBe 2
      (getFilesResult.json \\ "fileName").map(_.as[String]).toSeq shouldBe Seq(file1, file2)
    }

    Scenario("Should return all files for empty search") {

      Given("Files have been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))
      await(upload(Some(id2), file2, contentType, publishable = false))

      dbFileStoreSize shouldBe 2

      When("I request the file details")
      val getFilesResult = await(getFiles(Seq()))

      Then("The response code should be Ok")
      getFilesResult.status shouldBe Status.OK

      And("The response body contains the file details")

      getFilesResult.json.asInstanceOf[JsArray].value.size        shouldBe 2
      (getFilesResult.json \\ "fileName").map(_.as[String]).toSeq shouldBe Seq(file1, file2)
    }
  }

  Feature("Notify") {

    Scenario("Successful scan should update the status") {

      Given("A File has been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))

      dbFileStoreSize shouldBe 1

      When("Notify is Called")

      val uri = new File(filePath).toURI

      eventually(timeout(10.seconds), interval(1.second)) {
        val result =
          await(notifySuccess(id1, file1, uri))

        Then("The response code should be Created")
        result.status shouldBe Status.CREATED

        And("The response body contains the file details")

        (result.json \\ "fileName").map(_.as[String]).toSeq shouldBe Seq(file1)
        (result.json \\ "mimeType").map(_.as[String]).toSeq shouldBe Seq(contentType)

        And("The response shows the file is marked as safe")
        (result.json \\ "scanStatus").map(_.as[String]).toSeq shouldBe Seq(READY.toString)
      }
    }

    Scenario("Quarantined scan should update the status") {

      Given("A File has been uploaded")

      await(upload(Some(id1), file1, contentType, publishable = true))

      When("Notify is Called")

      val result = await(notifyFailure(id1))

      Then("The response code should be Created")

      result.status shouldBe Status.CREATED

      And("The response body contains the file details")

      (result.json \\ "fileName").map(_.as[String]).toSeq shouldBe Seq(file1)
      (result.json \\ "mimeType").map(_.as[String]).toSeq shouldBe Seq(contentType)
      (result.json \\ "url").map(_.as[String]).toSeq      shouldBe Seq()

      And("The response shows the file is marked as quarantined")
      (result.json \\ "scanStatus").map(_.as[String]).toSeq shouldBe Seq(FAILED.toString)
    }
  }

  Feature("Publish") {

    Scenario("Should persist the file to permanent storage") {

      Given("A File has been uploaded and marked as safe")

      await(upload(Some(id1), file1, contentType, publishable = true))

      notifySuccess(id1, file1)

      When("It is Published")

      eventually(timeout(10.seconds), interval(1.second)) {
        val result = await(publishSafeFile(id1))

        Then("The response code should be Accepted")
        result.status shouldBe Status.ACCEPTED

        And("The response body contains the file details")

        (result.json \\ "fileName").map(_.as[String]).toSeq     shouldBe Seq(file1)
        (result.json \\ "mimeType").map(_.as[String]).toSeq     shouldBe Seq(contentType)
        (result.json \\ "scanStatus").map(_.as[String]).toSeq   shouldBe Seq(READY.toString)
        (result.json \\ "publishable").map(_.as[Boolean]).toSeq shouldBe Seq(true)
        (result.json \\ "published").map(_.as[Boolean]).toSeq   shouldBe Seq(true)

        And("The response shows the file published")

        (result.json \\ "url").map(_.as[String]).headOption.getOrElse("") should include(s"$id1")
        (result.json \\ "url").map(_.as[String]).headOption.getOrElse("") should include(
          "X-Amz-Algorithm=AWS4-HMAC-SHA256"
        )
      }
    }

    Scenario("Should mark an un-safe file as publishable, but not persist") {

      Given("A File has been uploaded and marked as quarantined")

      await(upload(Some(id1), file1, contentType, publishable = true))

      await(notifyFailure(id1))

      When("It is Published")

      val publishResult: HttpResponse = await(publishUnsafeFile(id1))

      Then("The response code should be ACCEPTED")

      publishResult.status shouldBe Status.ACCEPTED

      And("The response body contains the file details")

      (publishResult.json \\ "fileName").map(_.as[String]).toSeq     shouldBe Seq(file1)
      (publishResult.json \\ "mimeType").map(_.as[String]).toSeq     shouldBe Seq(contentType)
      (publishResult.json \\ "scanStatus").map(_.as[String]).toSeq   shouldBe Seq(FAILED.toString)
      (publishResult.json \\ "publishable").map(_.as[Boolean]).toSeq shouldBe Seq(true)
      (publishResult.json \\ "published").map(_.as[Boolean]).toSeq   shouldBe Seq(false)

      And("I can call GET and see the file is unpublished")

      val getResult = await(getFile(id1))

      getResult.status shouldBe Status.OK

      (getResult.json \\ "fileName").map(_.as[String]).toSeq     shouldBe Seq(file1)
      (getResult.json \\ "mimeType").map(_.as[String]).toSeq     shouldBe Seq(contentType)
      (getResult.json \\ "scanStatus").map(_.as[String]).toSeq   shouldBe Seq(FAILED.toString)
      (getResult.json \\ "publishable").map(_.as[Boolean]).toSeq shouldBe Seq(true)
      (getResult.json \\ "published").map(_.as[Boolean]).toSeq   shouldBe Seq(false)
    }

    Scenario("Should remove publishable file which has expired") {

      Given("A File has been uploaded and marked as safe")

      await(upload(Some(id1), file1, contentType, publishable = true))

      val uri = new File(filePath).toURI

      await(
        notifySuccess(
          id1,
          file1,
          uri = new URI(uri.toString + "?X-Amz-Date=19700101T000000Z&X-Amz-Expires=0")
        )
      )

      When("It is Published")

      val publishResponse = publishSafeFile(id1)

      Then("The response code should be Not Found")

      await(publishResponse).status shouldBe Status.NOT_FOUND

      And("I can call GET and see the file does not exist")

      val getFileResult = getFile(id1)

      await(getFileResult).status shouldBe Status.NOT_FOUND
    }
  }

}
