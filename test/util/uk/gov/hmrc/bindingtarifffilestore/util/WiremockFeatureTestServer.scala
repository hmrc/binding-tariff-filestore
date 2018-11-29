package uk.gov.hmrc.bindingtarifffilestore.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtarifffilestore.BaseFeatureSpec

trait WiremockFeatureTestServer extends BaseFeatureSpec with BeforeAndAfterEach {

  private val wireHost = "localhost"
  protected val wirePort = 20001
  private val wireMockServer = new WireMockServer(wirePort)

  lazy val wireMockUrl: String = s"http://$wireHost:$wirePort"

  protected def stubFor(mappingBuilder: MappingBuilder): StubMapping = {
    wireMockServer.stubFor(mappingBuilder)
  }

  override protected def beforeEach(): Unit = {
    wireMockServer.start()
    WireMock.configureFor(wireHost, wirePort)
  }

  override protected def afterEach(): Unit = {
    wireMockServer.stop()
  }
}
