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
import play.api.http.Status._
import uk.gov.hmrc.bindingtarifffilestore.util.ResourceFiles
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthSpec extends BaseFeatureSpec with ResourceFiles {

  private val serviceUrl = s"http://localhost:$port"

  private val hashedTokenValue = BaseEncoding
    .base64Url()
    .encode(
      MessageDigest
        .getInstance("SHA-256")
        .digest(appConfig.authorization.getBytes("UTF-8"))
    )

  Feature("Authentication to incoming requests") {

    Scenario("Allowing requests with expected auth header") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(apiTokenKey -> appConfig.authorization))

      val response: Future[HttpResponse] = httpClientV2.get(url"$serviceUrl/file")(hc).execute[HttpResponse]

      Then("The response code should not be 403")
      val result = await(response)
      result.status shouldBe OK
    }

    Scenario("Forbidding requests with incorrect value for the auth header") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(apiTokenKey -> "WRONG_TOKEN"))

      val response: Future[HttpResponse] = httpClientV2.get(url"$serviceUrl/file")(hc).execute[HttpResponse]

      Then("The response code should be 403")

      val result = await(response)
      result.status shouldBe FORBIDDEN
      result.body   shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Forbidding requests with incorrect value for the auth header and expected auth token query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(apiTokenKey -> "WRONG_TOKEN"))

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/file?X-Api-Token=$hashedTokenValue")(hc)
        .execute[HttpResponse]

      Then("The response code should be 403")
      val result = await(response)
      result.status shouldBe FORBIDDEN
      result.body   shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Forbidding requests with expected value for the auth header and incorrect auth token query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(apiTokenKey -> "WRONG_TOKEN"))

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/file?X-Api-Token=WRONG_TOKEN")(hc)
        .execute[HttpResponse]

      Then("The response code should be 403")

      val result = await(response)
      result.status shouldBe FORBIDDEN
      result.body   shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Allowing requests with both expected auth header and expected auth query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(apiTokenKey -> appConfig.authorization))

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/file?X-Api-Token=$hashedTokenValue")(hc)
        .execute[HttpResponse]

      Then("The response code should be 200")
      val result = await(response)
      result.status shouldBe OK
    }

    Scenario("Forbidding requests with no auth header and no auth query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier()

      val response: Future[HttpResponse] = httpClientV2.get(url"$serviceUrl/file")(hc).execute[HttpResponse]

      Then("The response code should be 403")
      val result = await(response)
      result.status shouldBe FORBIDDEN
      result.body   shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Allowing requests with no auth header and with expected auth query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier()

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/file?X-Api-Token=$hashedTokenValue")(hc)
        .execute[HttpResponse]

      Then("The response code should not be 403")

      val result = await(response)
      result.status should not be FORBIDDEN
    }

    Scenario("Forbidding requests with incorrect value for the auth token query param") {

      When("I call an endpoint")

      val hc: HeaderCarrier = HeaderCarrier()

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/file?X-Api-Token=WRONG_TOKEN")(hc)
        .execute[HttpResponse]

      Then("The response code should be 403")
      val result = await(response)
      result.status shouldBe FORBIDDEN
      result.body   shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Calls to the health endpoint do not require auth token") {

      val hc: HeaderCarrier = HeaderCarrier()

      val response: Future[HttpResponse] = httpClientV2
        .get(url"$serviceUrl/ping/ping")(hc)
        .execute[HttpResponse]

      Then("The response code should be 200")
      val result = await(response)
      result.status shouldBe OK
    }
  }
}
