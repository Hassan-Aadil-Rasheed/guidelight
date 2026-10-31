package com.guidelight.app.indoor

import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Uses an isolated cache file; never writes the user's real home-map.json. */
@RunWith(AndroidJUnit4::class)
class HomeStorageDeviceTest {
    @Test fun savedGraphSurvivesStoreRecreationAndRoutesOnAndroid() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val file = File(context.cacheDir, "home-store-test-${UUID.randomUUID()}.json")
            val map = HomeMap(nodes = listOf(HomeNode(0, HomePoint(0f, 0f, 0f)),
                HomeNode(1, HomePoint(0f, 0f, 2f)), HomeNode(2, HomePoint(2f, 0f, 2f))),
                edges = listOf(HomeEdge(0, 1), HomeEdge(1, 2)), rooms = listOf(HomeRoom("kitchen", 2)))
            val firstJob = SupervisorJob()
            val first = DataStoreFactory.create(HomeMapSerializer, scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file })
            try { first.updateData { map } } finally { firstJob.cancelAndJoin() }
            val secondJob = SupervisorJob()
            try {
                val second = DataStoreFactory.create(HomeMapSerializer, scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file })
                val restored = second.data.first()
                assertThat(restored).isEqualTo(map)
                val route = HomeRouting.plan(restored, HomePoint(0f, 0f, 0f), restored.rooms.first())
                assertThat(route.points).contains(HomePoint(0f, 0f, 2f))
                assertThat(route.points.zipWithNext().sumOf { (a, b) -> a.distance(b).toDouble() }).isWithin(.01).of(4.0)
            } finally {
                secondJob.cancelAndJoin()
                assertThat(file.delete()).isTrue()
            }
        }
    }
}
