/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import io.opentelemetry.android.Incubating
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for session management.
 * Configure each field based on your requirements.
 * Nullable fields allow disabling features or using defaults.
 */
@Incubating
class SessionConfig(
    /**
     * Maximum session lifetime. Default: 4 hours.
     * Set to null to use default (4 hours).
     */
    val maxLifetime: Duration? = 4.hours,

    /**
     * Background inactivity timeout. Default: 15 minutes.
     * Set to null to disable background/foreground checks.
     */
    val backgroundInactivityTimeout: Duration? = 15.minutes,

    /**
     * Enable persistent session storage. Default: false.
     * Set to true to persist sessions across app restarts.
     */
    val enablePersistence: Boolean = false,
) {
    companion object {
        /**
         * Creates default config (backward compatible - OTEL defaults).
         */
        @JvmStatic
        fun withDefaults(): SessionConfig = SessionConfig()
    }
}
