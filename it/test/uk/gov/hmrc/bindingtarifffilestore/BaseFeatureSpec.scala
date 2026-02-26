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

import com.google.common.io.BaseEncoding
import org.scalatest.*
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import org.mongodb.scala.SingleObservableFuture

import java.security.MessageDigest
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

abstract class BaseFeatureSpec
    extends AnyFeatureSpec
    with HttpClientV2Support
    with Matchers
    with GivenWhenThen
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with DefaultPlayMongoRepositorySupport[FileMetadata] {

  protected lazy val apiTokenKey          = "X-Api-Token"
  protected lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  override val repository: FileMetadataMongoRepository = new FileMetadataMongoRepository(mongoComponent)

  val timeoutDuration: Int = 5

  protected def hash: String => String = (s: String) =>
    BaseEncoding.base64Url().encode(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))

  private val timeout = 2.seconds

  def await[A](future: Future[A]): A = Await.result(future, timeout)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    drop()
    ensureIndexes()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    drop()
    ensureIndexes()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    drop()
  }

  private def drop(): Unit =
    await(repository.collection.drop().toFuture())

}
