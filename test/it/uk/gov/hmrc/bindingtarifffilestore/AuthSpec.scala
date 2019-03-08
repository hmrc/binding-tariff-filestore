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
import play.api.http.HttpVerbs
import play.api.http.Status._
import scalaj.http.Http
import uk.gov.hmrc.bindingtarifffilestore.util.{BaseFeatureSpec, ResourceFiles}

class AuthSpec extends BaseFeatureSpec with ResourceFiles {

  override lazy val port = 14682

  private val serviceUrl = s"http://localhost:$port"

  feature("Authentication") {

    scenario("Allowing requests with expected auth header") {

      When("I call an endpoint with the expected auth header")
      val result = Http(s"$serviceUrl/file")
        .header(apiTokenKey, appConfig.authorization)
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should not be 403")
      result.code should not be FORBIDDEN
    }

    scenario("Forbidding requests with incorrect value for the auth header") {

      When("I call an endpoint with invalid auth header")
      val result = Http(s"$serviceUrl/file")
        .header(apiTokenKey, "WRONG_TOKEN")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

    scenario("Forbidding requests with incorrect value for the auth header and expected auth token query param") {

      val hashedTokenValue = BaseEncoding.base64Url().encode(
        MessageDigest.getInstance("SHA-256")
          .digest(appConfig.authorization.getBytes("UTF-8"))
      )

      When("I call the notify endpoint with the auth token query param")
      val result = Http(s"$serviceUrl/file?X-Api-Token=$hashedTokenValue")
        .header(apiTokenKey, "WRONG_TOKEN")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

    scenario("Forbidding requests with no auth header and no auth query param") {

      When("I call an endpoint with no auth token and not auth query param")
      val result = Http(s"$serviceUrl/file")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

    scenario("Allowing requests with no auth header and with expected auth token query param") {

      val hashedTokenValue = BaseEncoding.base64Url().encode(
        MessageDigest.getInstance("SHA-256")
          .digest(appConfig.authorization.getBytes("UTF-8"))
      )

      When("I call the notify endpoint with the auth token query param")
      val result = Http(s"$serviceUrl/file?X-Api-Token=$hashedTokenValue")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should not be 403")
      result.code should not be FORBIDDEN
    }

    scenario("Forbidding requests with incorrect value for the auth token query param") {

      When("I call the notify endpoint with the auth token query param")
      val result = Http(s"$serviceUrl/file?X-Api-Token=WRONG_VALUE")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldBe FORBIDDEN
    }

  }

}
