package io.github.haeyeonyou.familytrip.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 수면 모드에서는 지연 알림이 시스템 팝업(흰 헤즈업)·소리 없는 채널로 간다. */
class DelayAlertChannelTest {
    @Test
    fun sleepUsesQuietChannel() {
        assertEquals("trip_delay", DelayAlertController.channelFor(sleep = false))
        assertEquals("trip_delay_quiet", DelayAlertController.channelFor(sleep = true))
        assertNotEquals(DelayAlertController.channelFor(true), DelayAlertController.channelFor(false))
    }
}
