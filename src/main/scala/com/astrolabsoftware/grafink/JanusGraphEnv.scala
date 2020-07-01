/*
 * Copyright 2020 AstroLab Software
 * Author: Yash Datta
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
package com.astrolabsoftware.grafink

import org.janusgraph.core.{ JanusGraph, JanusGraphFactory }
import org.janusgraph.diskstorage.util.time.TimestampProviders
import zio.{ ZIO, ZManaged }
import zio.logging.{ log, Logging }

import com.astrolabsoftware.grafink.models.JanusGraphConfig

object JanusGraphEnv extends Serializable {

  def releaseGraph: JanusGraph => zio.URIO[Any, ZIO[Logging, Nothing, Unit]] =
    (graph: JanusGraph) =>
      ZIO
        .effect(graph.close())
        .fold(_ => log.error(s"Error closing janusgraph instance"), _ => log.info(s"JanusGraph instance closed"))

  def hbaseBasic(config: JanusGraphConfig): ZManaged[Logging, Throwable, JanusGraph] =
    ZIO
      .effect(withHBaseStorage(config))
      .toManaged(releaseGraph)

  def hbase(config: JanusGraphConfig): ZManaged[Logging, Throwable, JanusGraph] =
    ZIO
      .effect(withHBaseStorageWithBulkLoad(config))
      .toManaged(releaseGraph)

  def inmemory(config: JanusGraphConfig): ZManaged[Logging, Throwable, JanusGraph] =
    ZIO
      .effect(inMemoryStorage(config))
      .toManaged(releaseGraph)

  def inMemoryStorage: JanusGraphConfig => JanusGraph =
    config =>
      JanusGraphFactory.build
      // Use hbase as storage backend
        .set("storage.backend", "inmemory")
        // Manual transactions
        .set("storage.transactions", false)
        // Allow setting vertex ids
        .set("graph.set-vertex-id", true)
        .open()

  def withHBaseStorage: JanusGraphConfig => JanusGraph =
    config =>
      JanusGraphFactory.build
      // Use hbase as storage backend
        .set("storage.backend", "hbase")
        .set("graph.timestamps", TimestampProviders.MILLI)
        // Configure hbase as storage backend
        .set("storage.hostname", config.storage.host)
        // Use the configured table name
        .set("storage.hbase.table", config.storage.tableName)
        // Manual transactions
        .set("storage.transactions", false)
        // Allow setting vertex ids
        .set("graph.set-vertex-id", true)
        .open()

  def withHBaseStorageWithBulkLoad: JanusGraphConfig => JanusGraph =
    config =>
      JanusGraphFactory.build
      // Use hbase as storage backend
        .set("storage.backend", "hbase")
        .set("graph.timestamps", TimestampProviders.MILLI)
        // Configure hbase as storage backend
        .set("storage.hostname", config.storage.host)
        // Use the configured table name
        .set("storage.hbase.table", config.storage.tableName)
        .set("schema.default", "none")
        // Manual transactions
        .set("storage.transactions", false)
        // Use batch loading
        .set("storage.batch-loading", true)
        // Allow setting vertex ids
        .set("graph.set-vertex-id", true)
        .open()

  def withGraph(config: JanusGraphConfig, use: JanusGraph => ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, Unit] =
    ZIO.effect(withHBaseStorageWithBulkLoad(config)).bracket(releaseGraph, use)
}