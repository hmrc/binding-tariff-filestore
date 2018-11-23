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
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.{ScanResult, SuccessfulScanResult}
import uk.gov.hmrc.bindingtarifffilestore.model.{ScanStatus, TemporaryAttachment}
import uk.gov.hmrc.bindingtarifffilestore.repository.TemporaryAttachmentRepository

import scala.concurrent.Future

@Singleton()
class FileStoreService @Inject()(connector: AmazonS3Connector, repository: TemporaryAttachmentRepository) {

  //  def getAll: Future[Seq[TemporaryAttachment]] = {
  //    Future.successful(connector.getAll)
  //  }

  def getById(id: String): Future[Option[TemporaryAttachment]] = {
    repository.get(id)
  }

  def upload(attachment: TemporaryAttachment): Future[TemporaryAttachment] = {
    repository.insert(attachment)
  }

  def notify(attachment: TemporaryAttachment, scanResult: ScanResult): Future[Option[TemporaryAttachment]] = {
    val updated: TemporaryAttachment = scanResult.fileStatus match {
      case ScanStatus.READY =>
        val result = scanResult.asInstanceOf[SuccessfulScanResult]
        attachment.copy(url = Some(result.downloadUrl), scanStatus = Some(ScanStatus.READY))
      case ScanStatus.FAILED =>
        attachment.copy(scanStatus = Some(ScanStatus.FAILED))
    }
    repository.update(updated)
  }

}


