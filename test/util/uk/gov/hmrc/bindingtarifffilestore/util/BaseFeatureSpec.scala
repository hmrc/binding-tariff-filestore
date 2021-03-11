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

package uk.gov.hmrc.bindingtarifffilestore.util

import java.security.MessageDigest

import com.google.common.io.BaseEncoding
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class BaseFeatureSpec extends FeatureSpec with Matchers
  with GivenWhenThen with GuiceOneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  protected lazy val apiTokenKey = "X-Api-Token"
  protected lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  protected def hash: String => String = { s: String =>
    BaseEncoding.base64Url().encode(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))
  }

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
