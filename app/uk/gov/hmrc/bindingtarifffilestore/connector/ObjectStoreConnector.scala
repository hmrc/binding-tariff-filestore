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

package uk.gov.hmrc.bindingtarifffilestore.connector

import uk.gov.hmrc.objectstore.client.play.PlayObjectStoreClient
import com.google.inject.Inject

import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.{ObjectSummary, Path}

import java.net.URI
import scala.Console.println
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.io.Directory
import scala.util.{Failure, Success, Try}

@Singleton
class ObjectStoreConnector @Inject() (client: PlayObjectStoreClient, config: AppConfig)(implicit
  val ec: ExecutionContext
) extends Logging {

  private val directory: Path.Directory =
    Path.Directory("digital-tariffs-local")

  def getAll(path: Path.Directory)(implicit hc: HeaderCarrier): Future[List[ObjectSummary]] =
    client
      .listObjects(path)
      .map(_.objectSummaries.map(o => ObjectSummary(o.location, o.contentLength, o.lastModified)))

  def upload(fileMetaData: FileMetadata)(implicit hc: HeaderCarrier): FileMetadata =
    Try(
      client
        .uploadFromUrl(
          from = new URI(fileMetaData.url.getOrElse(throw new IllegalArgumentException("Missing URL"))).toURL,
          to = directory.file(fileMetaData.fileName.get)
        )
    ) match {
      case Success(_)            =>
        fileMetaData.copy(url = Some(s"${config.filestoreUrl}/${fileMetaData.id}"))
      case Failure(e: Throwable) =>
        log.error("Failed to upload to the object store.", e)
        throw e
    }

  def delete(id: String)(implicit hc: HeaderCarrier): Unit =
    client.deleteObject(
      path = directory.file(id)
    )

  def deleteAll()(implicit hc: HeaderCarrier): Unit =
    getAll(directory).map(files =>
      if (files.nonEmpty) {
        log.info(s"Removing [${files.length}] files from object store")
        Future.traverse(files)(filename =>
          client.deleteObject(
            path = directory.file(filename.location.fileName)
          )
        )
      } else {
        log.info(s"No files to remove from object store")
      }
    )

  def sign(fileMetaData: FileMetadata)(implicit hc: HeaderCarrier): Future[FileMetadata] =
    if (fileMetaData.url.isDefined) {
      client
        .presignedDownloadUrl(
          path = directory.file(fileMetaData.id),
          owner = "digital-tariffs"
        )
        .map { value =>
          val updatedMetaData = fileMetaData.copy(url = Some(value.toString))
          println(updatedMetaData)
          updatedMetaData
        }
    } else {
      Future.successful(fileMetaData)
    }
}
