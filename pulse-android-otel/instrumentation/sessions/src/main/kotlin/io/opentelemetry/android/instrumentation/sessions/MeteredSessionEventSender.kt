/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.sessions

import io.opentelemetry.android.common.RumConstants.Events.EVENT_METERED_SESSION_END
import io.opentelemetry.android.common.RumConstants.Events.EVENT_METERED_SESSION_START
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_PREVIOUS_ID

/**
 * Event sender for metered sessions (billing/metering).
 * Events are emitted as "metered.session.start" and "metered.session.end".
 * These events are NOT sent to backend by default (no observer attached to exporters).
 * Can be enabled later by attaching this observer to metered session manager.
 */
class MeteredSessionEventSender(
    private val eventLogger: Logger,
) : SessionObserver {
    override fun onSessionStarted(
        newSession: Session,
        previousSession: Session,
    ) {
        val eventBuilder =
            eventLogger
                .logRecordBuilder()
                .setEventName(EVENT_METERED_SESSION_START)
                .setAttribute(SESSION_ID, newSession.getId())
        val previousSessionId = previousSession.getId()
        if (previousSessionId.isNotEmpty()) {
            eventBuilder.setAttribute(SESSION_PREVIOUS_ID, previousSessionId)
        }
        eventBuilder.emit()
    }

    override fun onSessionEnded(session: Session) {
        if (session.getId().isBlank()) {
            return
        }
        eventLogger
            .logRecordBuilder()
            .setEventName(EVENT_METERED_SESSION_END)
            .setAttribute(SESSION_ID, session.getId())
            .emit()
    }
}
