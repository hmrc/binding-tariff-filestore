/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtarifffilestore.repository

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB

@ImplementedBy(classOf[MongoDb])
trait MongoDbProvider {
  def mongo: () => DB
}

@Singleton
class MongoDb @Inject() (mongoDB: ReactiveMongoComponent) extends MongoDbProvider {
  override val mongo: () => DB = mongoDB.mongoConnector.db
}
