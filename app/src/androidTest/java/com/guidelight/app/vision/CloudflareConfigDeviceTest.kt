package com.guidelight.app.vision

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudflareConfigDeviceTest {
    @Test fun tokenUsesAndroidKeystoreAndNoBackupStorage() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val fixture=File(context.cacheDir,"cloudflare-test-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=fixture}
        val store=CloudflareConfigStore(isolated)
        val credentials=CloudflareCredentials("0123456789abcdef0123456789abcdef","test-token-not-a-real-secret-12345")
        try {
            assertThat(store.read()).isNull()
            store.save(credentials)
            val stored=File(fixture,"cloudflare.config").readText()
            assertThat(stored).doesNotContain(credentials.token)
            assertThat(stored).doesNotContain(credentials.accountId)
            assertThat(store.read()?.token).isEqualTo(credentials.token)
            assertThat(store.read()?.accountId).isEqualTo(credentials.accountId)
            store.clear();assertThat(store.read()).isNull()
        }finally {
            check(fixture.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator))
            fixture.deleteRecursively()
        }
    }
}
