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

package uk.gov.hmrc.bindingtarifffilestore.util

import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class BaseFeatureSpec extends FeatureSpec with Matchers
  with GivenWhenThen with GuiceOneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  private val timeout = 2.seconds

  private lazy val store: FileMetadataMongoRepository = app.injector.instanceOf[FileMetadataMongoRepository]

  private def ensureIndexes(): Unit = {
    result(store.ensureIndexes, timeout)
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

  private def drop(): Unit = {
    result(store.drop, timeout)
  }

}
