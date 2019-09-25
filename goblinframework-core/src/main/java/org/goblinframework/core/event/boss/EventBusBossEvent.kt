package org.goblinframework.core.event.boss

import org.goblinframework.core.event.context.GoblinEventContextImpl
import java.util.concurrent.atomic.LongAdder

class EventBusBossEvent {

  var ctx: GoblinEventContextImpl? = null
  var receivedCount: LongAdder? = null

  internal fun clear() {
    ctx = null
    receivedCount = null
  }

}