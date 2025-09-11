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

package uk.gov.hmrc.bindingtarifffilestore.connector

import org.apache.pekko.stream.Materializer
import org.mockito.Mockito.mock
import org.scalatest.BeforeAndAfterEach
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, inject}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Path.{Directory, File}
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.play.Implicits.{futureMonad, stringWrite}
import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClient
import uk.gov.hmrc.objectstore.client.{ObjectSummary, Path, RetentionPeriod}

import java.time.{Instant, LocalDate, LocalTime, ZoneId}
import java.util.UUID.randomUUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

class ObjectStoreConnectorSpec
    extends UnitSpec
    with WithFakeApplication
    with WiremockTestServer
    with BeforeAndAfterEach
    with ResourceFiles {

  implicit lazy val hc: HeaderCarrier    = mock(classOf[HeaderCarrier])
  private implicit val mat: Materializer = mock(classOf[Materializer])
  private val directory: Path.Directory  =
    Path.Directory("binding-tariff-filestore")

  private val file1 = FileMetadata("id1", Some("file1.txt"), Some("text/plain"), Some("http://foo.bar/test-123.txt"))
  private val file2 = FileMetadata("id2", Some("file2.txt"), Some("text/plain"), Some("http://foo.bar/test-456.txt"))

  lazy val objectStoreClientStub: StubPlayObjectStoreClient = {
    val baseUrl = s"baseUrl-${randomUUID().toString}"
    val owner   = "digital-tariffs-local"
    val token   = s"token-${randomUUID().toString}"
    val config  = ObjectStoreClientConfig(baseUrl, owner, token, RetentionPeriod.OneWeek)

    new StubPlayObjectStoreClient(config)
  }

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.object-store.port" -> s"$wirePort"
    )
    .bindings(inject.bind(classOf[PlayObjectStoreClient]).to(objectStoreClientStub))
    .build()

  private implicit val config: AppConfig = fakeApplication.injector.instanceOf[AppConfig]

  private val connector = new ObjectStoreConnector(objectStoreClientStub, config)

  val zonedDateTime: Instant =
    LocalDate.of(2025, 1, 1).atTime(LocalTime.of(12, 30)).atZone(ZoneId.of("Europe/Paris")).toInstant

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "Get All" should {
    "list files when no files exist" in {
      val all = await(connector.getAll(directory))

      all shouldBe List.empty[ObjectSummary]
    }

    "when files have been added, list files that have been stored" in {
      await(objectStoreClientStub.putObject(directory.file(file1.fileName.get), "text", RetentionPeriod.OneDay))
      val all = await(connector.getAll(directory))

      all.size shouldBe 1
    }
  }

  "Upload" should {

    "add file to the object store" in {

      await(connector.upload(file1))

      val file = await(connector.getAll(directory))

      file.length        shouldBe 1
      file.head.location shouldBe File(Directory("digital-tariffs-local/binding-tariff-filestore"), "file1.txt")
    }

    "Throw Exception on missing URL" in {
      val fileUploading = FileMetadata("id", Some("file.txt"), Some("text/plain"))
      val exception     = intercept[IllegalArgumentException] {
        connector.upload(fileUploading)
      }

      exception.getMessage shouldBe "Missing URL"
    }
  }

  "Sign" should {
    "create a presigned download URL" in {

      await(objectStoreClientStub.putObject(directory.file(file1.fileName.get), "text", RetentionPeriod.OneDay))

      val result = await(connector.sign(file1))

      result     shouldBe file1
      result.url shouldBe Some("http://foo.bar/test-123.txt")
    }

    "not create a presigned download url if there is an empty URL" in {
      val file   = FileMetadata("id", Some("file.txt"), Some("text/plain"), None)
      val result = Await.result(connector.sign(file), 5.seconds)

      result.url shouldBe None
    }
  }

  "Delete One" should {
    "delete file from object store" in {
      await(
        objectStoreClientStub.putObject(directory.file(file1.fileName.get), file1.mimeType.get, RetentionPeriod.OneDay)
      )

      println(directory.file(file1.fileName.get).asUri)

      val result: Unit = await(
        connector
          .delete(file1.fileName.get)
      )

      val files = await(connector.getAll(directory))

      result === ()
      files.size shouldBe 0
    }
  }

  "Delete All" should {
    "Do nothing for no files" in {
      await(connector.deleteAll(), 5.seconds)

      val files = await(connector.getAll(directory))

      files.size shouldBe 0

    }

    "delete all files from object store if present" in {
      await(
        objectStoreClientStub.putObject(directory.file(file1.fileName.get), file1.mimeType.get, RetentionPeriod.OneDay)
      )
      await(
        objectStoreClientStub.putObject(directory.file(file2.fileName.get), file2.mimeType.get, RetentionPeriod.OneDay)
      )
      await(connector.deleteAll())

      val all = await(connector.getAll(directory))

      all.size shouldBe 0

    }
  }

}
