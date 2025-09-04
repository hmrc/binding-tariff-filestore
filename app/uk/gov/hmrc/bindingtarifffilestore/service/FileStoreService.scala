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

package uk.gov.hmrc.bindingtarifffilestore.service

import play.api.Logging
import uk.gov.hmrc.bindingtarifffilestore.audit.AuditService
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.connector.{ObjectStoreConnector, UpscanConnector}
import uk.gov.hmrc.bindingtarifffilestore.controllers.routes
import uk.gov.hmrc.bindingtarifffilestore.model.ScanStatus.READY
import uk.gov.hmrc.bindingtarifffilestore.model.*
import uk.gov.hmrc.bindingtarifffilestore.model.upscan.*
import uk.gov.hmrc.bindingtarifffilestore.repository.FileMetadataMongoRepository
import uk.gov.hmrc.bindingtarifffilestore.util.HashUtil
import uk.gov.hmrc.http.HeaderCarrier

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FileStoreService @Inject() (
  appConfig: AppConfig,
  fileStoreConnector: ObjectStoreConnector,
  repository: FileMetadataMongoRepository,
  upscanConnector: UpscanConnector,
  auditService: AuditService
)(implicit ec: ExecutionContext)
    extends Logging {

  private lazy val authToken = HashUtil.hash(appConfig.authorization)

  def encodeInBase64(text: String): String =
    Base64.getEncoder.encodeToString(text.getBytes(StandardCharsets.UTF_8))

  // Initiates an upload for a POST direct to Upscan
  def initiate(metadata: FileMetadata)(implicit hc: HeaderCarrier): Future[UploadTemplate] = {
    val fileId = metadata.id
    logger.info(s"[FileStoreService][initiate] File: $fileId")
    for {
      _                                        <- repository.insertFile(metadata)
      initiateResponse: UpscanInitiateResponse <- upscanInitiate(metadata)
      _                                         = logger.info(s"[FileStoreService][initiateV2] ${encodeInBase64(metadata.toString)}")
      template                                  = initiateResponse.uploadRequest
    } yield UploadTemplate(fileId, template.href, template.fields)
  }

  def initiateV2(
    request: v2.FileStoreInitiateRequest
  )(implicit hc: HeaderCarrier): Future[v2.FileStoreInitiateResponse] = {

    val fileId = request.id.getOrElse(UUID.randomUUID().toString)

    logger.info(s"[FileStoreService][initiateV2] File: $fileId")

    val callbackUrl = routes.FileStoreController
      .notification(fileId)
      .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl) + s"?X-Api-Token=$authToken"

    val fileMetadata: FileMetadata = FileMetadata.fromInitiateRequestV2(fileId, request)
    val upscanRequest              = v2.UpscanInitiateRequest.fromFileStoreRequest(callbackUrl, appConfig, request)

    logger.info(s"[FileStoreService][initiateV2] ${encodeInBase64(fileMetadata.toString)}")
    for {
      update           <- repository.insertFile(fileMetadata)
      initiateResponse <- upscanConnector.initiateV2(upscanRequest)
      _                 =
        logger.info(
          s"[FileStoreService][initiateV2] File: $fileId, Upscan Initiated with url [${initiateResponse.uploadRequest.href}] and Upscan reference [${initiateResponse.reference}]"
        )
      _                 = auditService.auditUpScanInitiated(
                            update.map(_.id).getOrElse(""),
                            update.map(_.fileName).getOrElse(Option.empty),
                            initiateResponse.reference
                          )
    } yield v2.FileStoreInitiateResponse.fromUpscanResponse(fileId, initiateResponse)
  }

  // Initiates an upload and Uploads the file direct
  def upload(fileWithMetadata: FileWithMetadata)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    val fileId = fileWithMetadata.metadata.id

    logger.info(s"[FileStoreService][upload] Uploading file $fileId")
    for {
      update           <- repository.insertFile(fileWithMetadata.metadata)
      initiateResponse <- upscanInitiate(fileWithMetadata.metadata)
      // This future (Upload) executes asynchronously intentionally
      _                 =
        logger.info(
          s"[FileStoreService][upload] File: $fileId, Uploading to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}]"
        )
      _                 = upscanConnector
                            .upload(initiateResponse.uploadRequest, fileWithMetadata)
                            .recover { case e =>
                              logger.error(
                                s"[FileStoreService][upload] File: $fileId, Upload failed to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}] $e"
                              )
                            }
                            .onComplete { _ =>
                              logger.info(
                                s"[FileStoreService][upload] File: $fileId, Uploaded to Upscan url [${initiateResponse.uploadRequest.href}] with Upscan reference [${initiateResponse.reference}]"
                              )
                            }
    } yield update
  }

  def find(id: String)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] =
    repository
      .get(id) map signingPermanentURL flatMap { a =>
      a.get.map(Some(_))
    }

  def find(search: Search, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileMetadata]] = {
    val pagedFuture: Future[Paged[Future[FileMetadata]]] =
      repository.get(search, pagination: Pagination) map signingPermanentURLs()
    pagedFuture.flatMap { paged =>
      Future.sequence(paged.results).map(resolvedItems => paged.copy(results = resolvedItems))
    }
  }

  // when UpScan comes back to us with the scan result
  def notify(attachment: FileMetadata, scanResult: ScanResult)(implicit
    hc: HeaderCarrier
  ): Future[Option[FileMetadata]] = {

    logger.info(
      s"[FileStoreService][notify] Attachment File: ${attachment.id}, Scan completed with status [${scanResult.fileStatus}] and Upscan reference [${scanResult.reference}]"
    )

    auditService
      .auditFileScanned(attachment.id, attachment.fileName, scanResult.reference, scanResult.fileStatus.toString)

    val updatedAttachment = attachment.withScanResult(scanResult)

    scanResult match {
      case FailedScanResult(_, _, details)        =>
        logger.info(
          s"[FileStoreService][notify] Attachment File: ${attachment.id}, Scan failed because it was [${details.failureReason}] with message [${details.message}]"
        )
        repository.update(updatedAttachment)
      case SuccessfulScanResult(_, _, _, details) =>
        logger.info(
          s"[FileStoreService][notify] Attachment File: ${attachment.id}, Scan succeeded with details [fileName: ${encodeInBase64(
            details.fileName
          )}, fileMimeType:${details.fileMimeType}, checksum:${encodeInBase64(details.checksum)}, ${details.uploadTimestamp}]"
        )
        if (updatedAttachment.publishable) {
          for {
            updated: Option[FileMetadata]   <- repository.update(updatedAttachment)
            published: Option[FileMetadata] <- updated match {
                                                 case Some(metadata) =>
                                                   publish(metadata)
                                                 case _              =>
                                                   logger.info(
                                                     s"[FileStoreService][notify] Attachment File: ${attachment.id}, Scan completed as READY but it couldn't be published as it no longer exists"
                                                   )
                                                   Future.successful(None)
                                               }
          } yield published
        } else {
          repository.update(updatedAttachment)
        }
    }
  }

  // when the file is uploaded to our S3 bucket
  def publish(att: FileMetadata)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {
    logger.info(s"[FileStoreService][publish] Publishing file: ${att.id}")
    (att.scanStatus, att.published) match {
      // File is Safe, unpublished and the download URL is still live
      case (Some(READY), false) if att.isLive =>
        logger.info(s"[FileStoreService][publish] File: ${att.id}, Publishing file to Permanent Storage")
        val metadata: FileMetadata = fileStoreConnector.upload(att)
        auditService.auditFilePublished(att.id, att.fileName.get)
        logger.info(s"[FileStoreService][publish] File: ${att.id}, Published file to Permanent Storage")
        repository
          .update(metadata.copy(publishable = true, published = true))
          .map(signingPermanentURL)
          .flatMap(a => a.get.map(Some(_)))

      // File is safe, unpublished but the download URL has expired. Clean Up.
      case (Some(READY), false) =>
        logger.info(
          s"[FileStoreService][publish] File: ${att.id}, Removing as it had an expired download URL [${att.url}]"
        )
        repository.delete(att.id).map(_ => None)

      // File not safe yet & is unpublished
      case (_, false) =>
        logger.info(s"[FileStoreService][publish] File: ${att.id}, Marking as publishable")
        repository.update(att.copy(publishable = true))

      // File is already published
      case (_, true) =>
        logger.info(
          s"[FileStoreService][publish] File: ${att.id}, Ignoring publish request as it was already published"
        )
        Future(Some(att))
    }
  }

  def deleteAll()(implicit hc: HeaderCarrier): Future[Unit] =
    repository.deleteAll() map (_ => fileStoreConnector.deleteAll())

  def delete(id: String, filename: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    logger.info(s"[FileStoreService][delete] Deleting file: $id")
    repository.delete(id) map (_ => fileStoreConnector.delete(filename))
  }

  private def upscanInitiate(fileMetadata: FileMetadata)(implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    val settings = UploadSettings(
      callbackUrl = routes.FileStoreController
        .notification(fileMetadata.id)
        .absoluteURL(appConfig.filestoreSSL, appConfig.filestoreUrl) + s"?X-Api-Token=$authToken",
      minimumFileSize = appConfig.fileStoreSizeConfiguration.minFileSize,
      maximumFileSize = appConfig.fileStoreSizeConfiguration.maxFileSize
    )
    for {
      initiateResponse <- upscanConnector.initiate(settings)
      _                 =
        logger.info(
          s"[FileStoreService][upscanInitiate] File: ${fileMetadata.id}, Upscan Initiated with url [${initiateResponse.uploadRequest.href}] and Upscan reference [${initiateResponse.reference}]"
        )
      _                 = auditService.auditUpScanInitiated(fileMetadata.id, fileMetadata.fileName, initiateResponse.reference)
    } yield initiateResponse
  }

  private def signingPermanentURL(implicit hc: HeaderCarrier): Option[FileMetadata] => Option[Future[FileMetadata]] = {
    asd =>
      asd.map(signingIfPublished)
  }

  private def signingPermanentURLs()(implicit hc: HeaderCarrier): Paged[FileMetadata] => Paged[Future[FileMetadata]] = {
    asd =>
      asd.map(signingIfPublished)
  }

  private def signingIfPublished(implicit hc: HeaderCarrier): FileMetadata => Future[FileMetadata] = {
    case file if file.published => fileStoreConnector.sign(file)
    case other                  => Future.successful(other)
  }
}
