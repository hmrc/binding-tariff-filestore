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
import org.apache.pekko.stream.Materializer
import play.mvc.Action

import javax.inject.Singleton
import uk.gov.hmrc.bindingtarifffilestore.config.AppConfig
import uk.gov.hmrc.bindingtarifffilestore.model.FileMetadata
import uk.gov.hmrc.bindingtarifffilestore.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.{ObjectSummary, ObjectSummaryWithMd5, Path}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ObjectStoreConnector @Inject() (client: PlayObjectStoreClient, config: AppConfig)(implicit ec: ExecutionContext, mat: Materializer) extends Logging {

  private val directory: Path.Directory =
    Path.Directory("digital-tariffs-local")

  def getAll()(implicit hc: HeaderCarrier): Future[List[ObjectSummary]] = {
    client.listObjects(directory).map(_.objectSummaries.map(o => ObjectSummary(o.location, o.contentLength, o.lastModified)))
  }

  def upload(fileMetadata: FileMetadata): Future[ObjectSummaryWithMd5] = {

   client.putObject(
      path = directory.file(fileMetadata.id),
      content = fileMetadata,
      contentType = fileMetadata.mimeType
    )
  }

  def delete(id: String)(implicit hc: HeaderCarrier): Unit =
    client.deleteObject(
      path = directory.file(id)
    )


  def deleteAll()(implicit  hc: HeaderCarrier): Unit = {
    getAll().map(
      files =>
       if(files.nonEmpty){
        log.info(s"Removing [${files.length}] files from S3")
        Future.traverse(files)(filename =>
          client.deleteObject(
            path = directory.file(filename.location.fileName)
          )
        )
      } else {
        log.info(s"No files to remove from S3")
      }
    )

  }

  def sign(fileMetaData: FileMetadata)(implicit hc: HeaderCarrier): FileMetadata =
    if (fileMetaData.url.isDefined) {
      val authenticatedUrl = client.presignedDownloadUrl(
        path = directory.file(fileMetaData.fileName.getOrElse("")),
        owner = "digital-tariffs-local"
      )
      fileMetaData.copy(url = Some(authenticatedUrl.toString))
    } else {
      fileMetaData
    }

}


