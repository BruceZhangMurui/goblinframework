package org.goblinframework.cache.redis.module.config

import org.goblinframework.cache.redis.module.RedisServerMode
import org.goblinframework.core.compression.CompressionThreshold
import org.goblinframework.core.compression.CompressorMode
import org.goblinframework.core.serialization.SerializerMode
import java.io.Serializable

class RedisConfigMapper : Serializable {

  var name: String? = null
  var servers: String? = null
  var password: String? = null
  var mode: RedisServerMode? = null
  var serializer: SerializerMode? = null
  var compressor: CompressorMode? = null
  var compressionThreshold: CompressionThreshold? = null

  companion object {
    private const val serialVersionUID = -3756108596956143695L
  }
}