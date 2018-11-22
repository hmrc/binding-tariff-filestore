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

package unit.uk.gov.hmrc.bindingtarifffilestore.service

import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtarifffilestore.connector.AmazonS3Connector
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.bindingtarifffilestore.connector.AmazonS3Connector
import uk.gov.hmrc.play.test.UnitSpec

class FileStoreServiceTest extends UnitSpec with MockitoSugar {

  private val connector = mock[AmazonS3Connector]

  val service = new FileStoreService(connector)

  "Service" should {
    "Delegate to Connector" in {
      val response = Seq("Val")
      given(connector.getAll).willReturn(response)

      service.getAll shouldBe response
    }
  }

}
