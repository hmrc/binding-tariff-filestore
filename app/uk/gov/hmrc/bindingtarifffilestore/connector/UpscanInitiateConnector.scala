package uk.gov.hmrc.bindingtarifffilestore.connector

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.{UploadFileResponse, UploadSettings}
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanInitiateConnector @Inject()(appConfig: AppConfig, http: HttpPost)(
  implicit executionContext: ExecutionContext) {

  def initiateAttachmentUpload(uploadSettings: UploadSettings)(
    implicit headerCarrier: HeaderCarrier): Future[UploadFileResponse] =
    http.POST[UploadSettings, UploadFileResponse](s"${appConfig.upscanInitiateUrl}/upscan/initiate", uploadSettings)

}
