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

package unit.uk.gov.hmrc.bindingtarifffilestore.controllers

import org.mockito.BDDMockito.given
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtarifffilestore.controllers.FileStoreController
import uk.gov.hmrc.bindingtarifffilestore.service.FileStoreService
import uk.gov.hmrc.play.test.UnitSpec

class FileStoreControllerSpec extends UnitSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  private val service = mock[FileStoreService]

  private val fakeRequest = FakeRequest("GET", "/")

  "GET /" should {
    "return 200" in {
      given(service.getAll).willReturn(Seq("val"))

      val result = new FileStoreController(service).listAllFiles()(fakeRequest)

      status(result) shouldBe Status.OK
    }
  }

}
