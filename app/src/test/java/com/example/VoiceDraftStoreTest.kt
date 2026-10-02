package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VoiceDraftStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceDraftStoreTest {
    @Test fun draftSurvivesRecreationAndIsIsolatedByAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = VoiceDraftStore(context, "a", "chat")
        val file = store.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        store.save(file, 7, listOf(.2f, .8f))
        val restored = VoiceDraftStore(context, "a", "chat").load()
        assertEquals(file, restored?.file)
        assertEquals(7, restored?.seconds)
        assertNull(VoiceDraftStore(context, "b", "chat").load())
        store.forget(file)
        assertNull(store.load())
        file.delete()
    }
    @Test fun completingAnOlderUploadDoesNotForgetANewerDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = VoiceDraftStore(context, "a", "second")
        val old = store.newFile().apply { writeText("old") }
        val current = store.newFile().apply { writeText("new") }
        store.save(current, 2, emptyList())
        store.forget(old)
        assertEquals(current, store.load()?.file)
        store.forget(current)
        old.delete(); current.delete()
    }
}
