/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on kotlinx.coroutines FastServiceLoader implementation
 * https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/internal/FastServiceLoader.kt
 */

package io.opentelemetry.android.instrumentation.internal

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.ServiceLoader
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/**
 * A simplified version of [ServiceLoader] optimized for Android.
 * FastServiceLoader locates and instantiates all service providers named in configuration
 * files placed in the resource directory <tt>META-INF/services</tt>.
 *
 * The main difference between this class and classic service loader is in skipping
 * verification JARs. A verification requires reading the whole JAR (and it causes problems and ANRs on Android devices)
 * and prevents only trivial checksum issues.
 *
 * If any error occurs during loading, it fallbacks to [ServiceLoader].
 */
internal object PulseFastServiceLoader {
    private const val PREFIX: String = "META-INF/services/"

    /**
     * Loads service providers for the given service class using FastServiceLoader,
     * falling back to ServiceLoader if any error occurs.
     */
    fun <S> load(service: Class<S>, loader: ClassLoader): Collection<S> {
        return try {
            loadProviders(service, loader)
        } catch (e: Throwable) {
            Log.w("PulseFastServiceLoader", "FastServiceLoader failed, falling back to ServiceLoader", e)
            ServiceLoader.load(service, loader).toSet()
        }
    }

    /**
     * Loads service providers by directly reading META-INF/services files
     * without JAR verification (which is slow on Android).
     */
    private fun <S> loadProviders(service: Class<S>, loader: ClassLoader): Collection<S> {
        val fullServiceName = PREFIX + service.name
        val urls = loader.getResources(fullServiceName)
        val providers = urls.asSequence().flatMap { parse(it) }.toSet()
        require(providers.isNotEmpty()) { "No providers were loaded with FastServiceLoader" }
        return providers.map { getProviderInstance(it, loader, service) }.toSet()
    }

    private fun <S> getProviderInstance(name: String, loader: ClassLoader, service: Class<S>): S {
        val clazz = Class.forName(name, false, loader)
        require(service.isAssignableFrom(clazz)) { "Expected service of class $service, but found $clazz" }
        return service.cast(clazz.getDeclaredConstructor().newInstance())
    }

    private fun parse(url: URL): Collection<String> {
        val path = url.toString()
        // Fast-path for JARs (skip verification with verify = false)
        if (path.startsWith("jar")) {
            val pathToJar = path.substringAfter("jar:file:").substringBefore('!')
            val entry = path.substringAfter("!/")
            // mind the verify = false flag! This is the key optimization
            JarFile(pathToJar, false).use { file ->
                BufferedReader(InputStreamReader(file.getInputStream(ZipEntry(entry)), "UTF-8")).use { r ->
                    return parseFile(r)
                }
            }
        }
        return InputStreamReader(url.openStream()).buffered().use { reader ->
            parseFile(reader)
        }
    }

    private fun parseFile(r: BufferedReader): Set<String> {
        val names = mutableSetOf<String>()
        while (true) {
            val line = r.readLine() ?: break
            val serviceName = line.substringBefore("#").trim()
            require(serviceName.all { it == '.' || Character.isJavaIdentifierPart(it) }) {
                "Illegal service provider class name: $serviceName"
            }
            if (serviceName.isNotEmpty()) {
                names.add(serviceName)
            }
        }
        return names
    }
}

