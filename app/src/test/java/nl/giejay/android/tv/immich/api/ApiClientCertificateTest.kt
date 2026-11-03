package nl.giejay.android.tv.immich.api

import java.io.File
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Test

class ApiClientCertificateTest {
    @Test fun `no certificate keeps the existing TLS configuration`() {
        val client = OkHttpClient()
        val builder = client.newBuilder()
        val original = client.sslSocketFactory
        val missing = File.createTempFile("immix-missing-cert", ".p12")
        check(missing.delete())
        ApiClientFactory.configureClientCertificateP12(builder, missing, charArrayOf())
        assertSame(original, builder.build().sslSocketFactory)
    }
}
