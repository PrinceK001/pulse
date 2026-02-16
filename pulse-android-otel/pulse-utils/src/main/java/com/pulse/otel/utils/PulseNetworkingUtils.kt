package com.pulse.otel.utils

import okhttp3.OkHttpClient

public object PulseNetworkingUtils {
    public val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
}
