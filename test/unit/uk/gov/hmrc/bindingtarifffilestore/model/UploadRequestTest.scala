package uk.gov.hmrc.bindingtarifffilestore.model

import uk.gov.hmrc.play.test.UnitSpec

class UploadRequestTest extends UnitSpec {

  "Upload Request" should {
    "map to metadata" in {
      val metadata = UploadRequest("file-name", "type", published = true).toMetaData
      metadata.fileName shouldBe "file-name"
      metadata.mimeType shouldBe "type"
      metadata.published shouldBe true
    }
  }

}
