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

package uk.gov.hmrc.bindingtarifffilestore.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtarifffilestore.connector.AmazonS3Connector
import uk.gov.hmrc.bindingtarifffilestore.model.Attachment
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.ScanResult

import scala.concurrent.Future

@Singleton()
class FileStoreService @Inject()(connector: AmazonS3Connector) {

  def getAll: Future[Seq[Attachment]] = {
    Future.successful(connector.getAll)
  }

  def getById(id: String): Future[Option[Attachment]] = {
    Future.successful(None)
  }

  def upload(attachment: Attachment): Future[Attachment] = {
    Future.successful(attachment)
  }

  def notify(attachment: Attachment, scanResult: ScanResult): Future[Attachment]  = {
    Future.successful(attachment)
  }

}


