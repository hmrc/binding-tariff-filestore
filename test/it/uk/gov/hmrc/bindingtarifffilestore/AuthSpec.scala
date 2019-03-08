/*
 * Copyright 2018 HM Revenue & Customs
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

import java.security.MessageDigest

import com.google.common.io.BaseEncoding
import org.scalatest.BeforeAndAfterEach
import play.api.http.HttpVerbs
import play.api.http.Status._
import scalaj.http.Http
import uk.gov.hmrc.bindingtarifffilestore.util.{BaseFeatureSpec, ResourceFiles}

import scala.concurrent.duration.{FiniteDuration, _}

class AuthSpec extends BaseFeatureSpec with ResourceFiles with BeforeAndAfterEach {

  override lazy val port = 14681

  private val timeout: FiniteDuration = 2.seconds
  private val serviceUrl = s"http://localhost:$port"

  feature("Authentication") {

    scenario("Calling an endpoint with auth header present with correct value is allowed") {

      When("I call an endpoint with the correct auth token")
      val result =     Http(s"$serviceUrl/file")
        .header(apiTokenKey, appConfig.authorization)
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should not be forbidden")
      result.code should not be FORBIDDEN
    }

    scenario("Auth header present with incorrect value") {

      When("I call an endpoint with an incorrect auth token")
      val result = Http(s"$serviceUrl/file")
        .header(apiTokenKey, "WRONG_TOKEN")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

    scenario("Auth header not present") {

      When("I call an endpoint with the no auth token")
      val result = Http(s"$serviceUrl/file")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

    scenario("Correct hashed auth token value provided as query param to an endpoint is allowed") {

      val hashedTokenValue = BaseEncoding.base64Url().encode(
                                MessageDigest.getInstance("SHA-256")
                                  .digest(appConfig.authorization.getBytes("UTF-8")))

      When("I call the notify endpoint with a hash of auth token")
      val result = Http(s"$serviceUrl/file?X-Api-Token=$hashedTokenValue")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should not be forbidden")
      result.code should not be FORBIDDEN
    }

    scenario("Incorrect hashed auth token value provided as query param to an endpoint is forbidden") {

      When("I call the notify endpoint with a hash of auth token")
      val result = Http(s"$serviceUrl/file?X-Api-Token=WRONG_VALUE")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should not be forbidden")
      result.code shouldBe FORBIDDEN
    }

  }
}
