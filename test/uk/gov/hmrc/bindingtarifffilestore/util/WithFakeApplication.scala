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

package uk.gov.hmrc.bindingtarifffilestore.util

import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.Helpers._
import play.api.{Application, Play}

/** Use this instead of play.test.WithApplication
  *
  * WithApplication will bring in specs2 lifecyles and changes the test run behaviour
  */
trait WithFakeApplication extends BeforeAndAfterAll {
  this: Suite =>

  lazy val fakeApplication: Application = new GuiceApplicationBuilder().bindings(bindModules: _*).build()

  def bindModules: Seq[GuiceableModule] = Seq()

  override def beforeAll(): Unit = {
    super.beforeAll()
    Play.start(fakeApplication)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Play.stop(fakeApplication)
  }

  def evaluateUsingPlay[T](block: => T): T =
    running(fakeApplication) {
      block
    }

}
