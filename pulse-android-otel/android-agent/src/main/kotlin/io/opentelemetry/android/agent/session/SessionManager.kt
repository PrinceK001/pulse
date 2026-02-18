/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.agent.session

import android.content.Context
import io.opentelemetry.android.Incubating
import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.android.session.Session
import io.opentelemetry.android.session.SessionObserver
import io.opentelemetry.android.session.SessionProvider
import io.opentelemetry.android.session.SessionPublisher
import io.opentelemetry.sdk.common.Clock
import java.util.Collections.synchronizedList
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

internal class SessionManager(
    private val clock: Clock = Clock.getDefault(),
    private val sessionStorage: SessionStorage,
    private val timeoutHandler: SessionIdTimeoutHandler?,
    private val idGenerator: SessionIdGenerator = DefaultSessionIdGenerator(Random.Default),
    private val maxSessionLifetime: Duration,
) : SessionProvider,
    SessionPublisher {
    private val session: AtomicReference<Session> = AtomicReference(Session.NONE)
    private val observers = synchronizedList(ArrayList<SessionObserver>())

    // Track expired restored session to emit session.end when creating new one
    private val expiredRestoredSession: AtomicReference<Session> = AtomicReference(Session.NONE)

    init {
        // Restore session from storage on init (works for both persistent and in-memory)
        // InMemorySessionStorage will return Session.NONE, PersistentSessionStorage will restore if available
        val restoredSession = sessionStorage.get()
        if (restoredSession.getId().isNotEmpty()) {
            // Check if restored session is expired
            if (!sessionHasExpired(restoredSession)) {
                // Session is still valid, restore it (no session.start event, reuse same session)
                session.set(restoredSession)
            } else {
                // Session is expired, track it to emit session.end when creating new session
                expiredRestoredSession.set(restoredSession)
                sessionStorage.save(Session.NONE)
            }
        } else {
            sessionStorage.save(Session.NONE)
        }
    }

    override fun addObserver(observer: SessionObserver) {
        observers.add(observer)
    }

    override fun getSessionId(): String {
        val currentSession = session.get()

        // Check if we need to create a new session
        // Only check timeout for non-NONE sessions
        val shouldCreateNew =
            sessionHasExpired(currentSession) ||
                (timeoutHandler?.hasTimedOut() == true && currentSession.getStartTimestamp() >= 0)

        return if (shouldCreateNew) {
            val newId = idGenerator.generateSessionId()
            val newSession = Session.DefaultSession(newId, clock.now())

            // Atomically update the session only if it hasn't been changed by another thread.
            if (session.compareAndSet(currentSession, newSession)) {
                sessionStorage.save(newSession)

                // Check if we have an expired restored session that needs session.end
                val expiredRestored = expiredRestoredSession.getAndSet(Session.NONE)
                val previousSession =
                    if (expiredRestored.getId().isNotEmpty()) {
                        expiredRestored
                    } else {
                        currentSession
                    }

                // Bump timeout handler first (extends timer if background timeout is enabled)
                timeoutHandler?.bump()

                // Notify observers: session.end for old, session.start for new
                notifyObserversOfSessionUpdate(previousSession, newSession)

                newSession.getId()
            } else {
                // Another thread accessed this function prior to creating a new session. Use the
                // current session.
                timeoutHandler?.bump()
                session.get().getId()
            }
        } else {
            // No new session needed, return current session ID
            // Bump timeout handler (extends timer if background timeout is enabled)
            timeoutHandler?.bump()
            currentSession.getId()
        }
    }

    private fun notifyObserversOfSessionUpdate(
        currentSession: Session,
        newSession: Session,
    ) {
        observers.forEach {
            it.onSessionEnded(currentSession)
            it.onSessionStarted(newSession, currentSession)
        }
    }

    private fun sessionHasExpired(session: Session): Boolean {
        // Session.NONE has startTimestamp = -1, so treat it as expired to create a new session
        if (session.getStartTimestamp() < 0) {
            return true
        }
        val elapsedTime = clock.now() - session.getStartTimestamp()
        return elapsedTime >= maxSessionLifetime.inWholeNanoseconds
    }

    companion object {
        @OptIn(Incubating::class)
        @JvmStatic
        fun create(
            application: Context,
            timeoutHandler: SessionIdTimeoutHandler?,
            sessionConfig: SessionConfig,
            storageKey: String = RumConstants.Session.OTEL_SESSION_STORAGE_KEY,
        ): SessionManager {
            // Choose storage based on persistence config
            val sessionStorage: SessionStorage =
                if (sessionConfig.shouldPersist) {
                    val sharedPreferences =
                        application.getSharedPreferences(
                            storageKey,
                            Context.MODE_PRIVATE,
                        )
                    PersistentSessionStorage(sharedPreferences)
                } else {
                    InMemorySessionStorage()
                }

            // Use max lifetime from config (default to 4 hours if null)
            val maxLifetime = sessionConfig.maxLifetime ?: 4.hours

            // Use provided handler or create one only if background timeout is enabled
            val handler =
                timeoutHandler ?: sessionConfig.backgroundInactivityTimeout?.let {
                    SessionIdTimeoutHandler(sessionConfig)
                }

            return SessionManager(
                sessionStorage = sessionStorage,
                timeoutHandler = handler,
                maxSessionLifetime = maxLifetime,
            )
        }
    }
}
