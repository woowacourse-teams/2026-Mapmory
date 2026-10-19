package com.mapmory.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TeamDeviceTapCounterTest {
    @Test
    fun `제한_시간_안에_일곱번째_탭마다_전환하고_다시_센다`() {
        val counter = TeamDeviceTapCounter(timeSource = TestTimeSource())

        val results = List(14) { counter.tap() }

        assertEquals(listOf(6, 13), results.indices.filter { results[it] })
    }

    @Test
    fun `첫_탭부터_제한_시간이_지나면_처음부터_다시_센다`() {
        val time = TestTimeSource()
        val counter = TeamDeviceTapCounter(timeSource = time)

        repeat(6) {
            assertFalse(counter.tap())
            time += 500.milliseconds
        }
        time += 1.seconds
        assertFalse(counter.tap())

        repeat(5) { assertFalse(counter.tap()) }
        assertTrue(counter.tap())
    }
}
