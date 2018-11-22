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

package uk.gov.hmrc.bindingtarifffilestore.config

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.play.test.UnitSpec

class AppConfigTest extends UnitSpec with GuiceOneAppPerSuite {

  private def configWith(pair: (String, String)): AppConfig = {
    new AppConfig(Configuration.from(Map(pair)), Environment.simple())
  }

  "Config" should {
    "decode AWS S3 Secret" in {
      configWith("s3.secretKeyId" -> "dGVzdA==").awsSecret shouldBe "test"
    }

    "return AWS S3 Access Key" in {
      configWith("s3.accessKeyId" -> "key").awsKey shouldBe "key"
    }

    "return AWS S3 region" in {
      configWith("s3.region" -> "region").awsRegion shouldBe "region"
    }

    "return AWS S3 bucket" in {
      configWith("s3.bucket" -> "bucket").awsBucket shouldBe "bucket"
    }

    "return AWS S3 endpoint" in {
      configWith("s3.endpoint" -> "endpoint").awsEndpoint shouldBe Some("endpoint")
    }

    "return AWS S3 blank endpoint as None" in {
      configWith("s3.endpoint" -> "").awsEndpoint shouldBe None
    }
  }

}
