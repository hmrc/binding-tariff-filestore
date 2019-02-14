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

package uk.gov.hmrc.bindingtarifffilestore.config

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.test.UnitSpec

class AppConfigSpec extends UnitSpec with GuiceOneAppPerSuite {

  private def fileStoreSizeConfiguration(pairs: (String, Int)*): FileStoreSizeConfiguration = {
    var config = Map[String, Int](
      "upscan.minFileSize" -> 0,
      "upscan.maxFileSize" -> 0
    )
    pairs.foreach(e => config = config + e)
    new AppConfig(Configuration.from(config), Environment.simple()).fileStoreSizeConfiguration
  }

  private def s3ConfigWith(pairs: (String, String)*): S3Configuration = {
    var config = Map(
      "s3.secretKeyId" -> "",
      "s3.accessKeyId" -> "",
      "s3.region" -> "",
      "s3.bucket" -> "",
      "s3.endpoint" -> ""
    )
    pairs.foreach(e => config = config + e)
    new AppConfig(Configuration.from(config), Environment.simple()).s3Configuration
  }

  private def configWith(pairs: (String, String)*): AppConfig = {
    new AppConfig(Configuration.from(pairs.map(e => e._1 -> e._2).toMap), Environment.simple())
  }

  "Config" should {
    "decode AWS S3 Secret" in {
      s3ConfigWith("s3.secretKeyId" -> "dGVzdA==").secret shouldBe "test"
    }

    "return AWS S3 Access Key" in {
      s3ConfigWith("s3.accessKeyId" -> "key").key shouldBe "key"
    }

    "return AWS S3 region" in {
      s3ConfigWith("s3.region" -> "region").region shouldBe "region"
    }

    "return AWS S3 bucket" in {
      s3ConfigWith("s3.bucket" -> "bucket").bucket shouldBe "bucket"
    }

    "return AWS S3 endpoint" in {
      s3ConfigWith("s3.endpoint" -> "endpoint").endpoint shouldBe Some("endpoint")
    }

    "return AWS S3 blank endpoint as None" in {
      s3ConfigWith("s3.endpoint" -> "").endpoint shouldBe None
    }

    "return AWS S3 base URL" in {
      s3ConfigWith("s3.endpoint" -> "endpoint").baseUrl shouldBe "endpoint"
    }

    "return AWS S3 default base URL" in {
      s3ConfigWith(
        "s3.region" -> "region"
      ).baseUrl shouldBe "https://s3-region.amazonaws.com"
    }

    "return application Host" in {
      configWith("filestore.url" -> "url").filestoreUrl shouldBe "url"
    }

    "return application SSL" in {
      configWith("filestore.ssl" -> "true").filestoreSSL shouldBe true
    }

    "return upscan-initiate URL" in {
      configWith(
        "microservice.services.upscan-initiate.host" -> "host",
        "microservice.services.upscan-initiate.port" -> "123"
      ).upscanInitiateUrl shouldBe "http://host:123"
    }

    "return upscan min file size" in {
      fileStoreSizeConfiguration(
        "upscan.minFileSize" -> 12
      ).minFileSize shouldBe 12
    }

    "return upscan max file size" in {
      fileStoreSizeConfiguration(
        "upscan.maxFileSize" -> 123456
      ).maxFileSize shouldBe 123456
    }
  }

}
