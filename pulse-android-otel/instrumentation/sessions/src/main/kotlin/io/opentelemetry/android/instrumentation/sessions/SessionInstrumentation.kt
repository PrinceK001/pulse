/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.sessions

import com.google.auto.service.AutoService
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.session.SessionPublisher

@AutoService(AndroidInstrumentation::class)
class SessionInstrumentation : AndroidInstrumentation {
    override val name: String = "session"

    override fun install(ctx: InstallationContext) {
        // Install OTEL session event sender
        val otelEventLogger =
            ctx.openTelemetry.logsBridge
                .loggerBuilder("otel.session")
                .build()
        val sessionProvider = ctx.sessionProvider
        if (sessionProvider is SessionPublisher) {
            sessionProvider.addObserver(SessionIdEventSender(otelEventLogger))
        }

        // Install metered session event sender (if metered session provider is available)
        val meteredSessionProvider = ctx.meteredSessionProvider as? SessionPublisher ?: return
        val meteredEventLogger =
            ctx.openTelemetry.logsBridge
                .loggerBuilder("pulse.metered.session")
                .build()
        meteredSessionProvider.addObserver(
            SessionIdEventSender(
                eventLogger = meteredEventLogger,
                eventStartName = RumConstants.Events.EVENT_METERED_SESSION_START,
                eventEndName = RumConstants.Events.EVENT_METERED_SESSION_END,
            ),
        )
    }
}
