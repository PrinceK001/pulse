/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import android.content.SharedPreferences
import io.opentelemetry.android.session.Session

/**
 * Handles persistence of OpenTelemetry sessions to SharedPreferences.
 * Provides methods for saving and loading session data.
 * Aligned with iOS SessionStore for parity.
 */
internal class PersistentSessionStorage(
    private val sharedPreferences: SharedPreferences,
) : SessionStorage {
    companion object {
        // Keys aligned with iOS SessionStore naming
        private const val KEY_SESSION_ID = "otel-session-id"
        private const val KEY_SESSION_START_TIMESTAMP = "otel-session-start-timestamp"
    }

    /**
     * Loads a previously saved session from SharedPreferences.
     * @return The saved session if ID and startTimestamp exist. Session.NONE otherwise.
     */
    override fun get(): Session {
        val id = sharedPreferences.getString(KEY_SESSION_ID, null) ?: return Session.NONE
        val startTimestamp = sharedPreferences.getLong(KEY_SESSION_START_TIMESTAMP, -1)
        
        if (startTimestamp == -1L) {
            return Session.NONE
        }
        
        return Session.DefaultSession(id, startTimestamp)
    }

    /**
     * Saves a session to SharedPreferences immediately.
     * Aligned with iOS SessionStore.saveImmediately().
     * @param newSession The session to save
     */
    override fun save(newSession: Session) {
        if (newSession.getId().isEmpty()) {
            // Clear stored session if it's NONE
            sharedPreferences.edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_SESSION_START_TIMESTAMP)
                .apply()
            return
        }
        
        sharedPreferences.edit()
            .putString(KEY_SESSION_ID, newSession.getId())
            .putLong(KEY_SESSION_START_TIMESTAMP, newSession.getStartTimestamp())
            .apply()
    }

    /**
     * Cleans up stored session data.
     * Aligned with iOS SessionStore.teardown().
     */
    fun teardown() {
        sharedPreferences.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_START_TIMESTAMP)
            .apply()
    }
}
