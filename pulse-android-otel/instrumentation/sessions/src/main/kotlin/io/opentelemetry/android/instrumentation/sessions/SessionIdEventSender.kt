/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.sessions

import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_END
import io.opentelemetry.android.common.RumConstants.Events.EVENT_SESSION_START
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_ID
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes.SESSION_PREVIOUS_ID

/**
 * This class is responsible for generating the session related events as
 * specified in the OpenTelemetry semantic conventions.
 * Can be configured with custom event names for different session types (OTEL, metered, etc.)
 */
internal class SessionIdEventSender(
    private val eventLogger: Logger,
    private val eventStartName: String = EVENT_SESSION_START,
    private val eventEndName: String = EVENT_SESSION_END,
) : SessionObserver {
    override fun onSessionStarted(
        newSession: Session,
        previousSession: Session,
    ) {
        val eventBuilder =
            eventLogger
                .logRecordBuilder()
                .setEventName(eventStartName)
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
            .setEventName(eventEndName)
            .setAttribute(SESSION_ID, session.getId())
            .emit()
    }
}
