package com.codein.ide.ai.net

import okio.BufferedSource

/** A single Server-Sent Event. */
data class SseEvent(val event: String?, val data: String)

/**
 * Minimal, spec-compliant Server-Sent Events reader (WHATWG event-stream format): accumulates
 * `event:` and multi-line `data:` fields, ignores comments and `id`/`retry`, and dispatches on a
 * blank line. Works on any [BufferedSource] so it is unit-testable without a network.
 */
class SseReader(private val source: BufferedSource) {

    private var eventName: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Returns the next event, or null at end of stream. */
    fun next(): SseEvent? {
        while (true) {
            val line = source.readUtf8Line()
            if (line == null) {
                return if (hasData) dispatch() else null
            }
            if (line.isEmpty()) {
                if (hasData) return dispatch()
                eventName = null
                continue
            }
            if (line.startsWith(':')) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            var value = if (colon < 0) "" else line.substring(colon + 1)
            if (value.startsWith(' ')) value = value.substring(1)
            when (field) {
                "event" -> eventName = value
                "data" -> {
                    if (hasData) data.append('\n')
                    data.append(value)
                    hasData = true
                }
                else -> Unit
            }
        }
    }

    private fun dispatch(): SseEvent {
        val event = SseEvent(eventName, data.toString())
        eventName = null
        data.setLength(0)
        hasData = false
        return event
    }
}
