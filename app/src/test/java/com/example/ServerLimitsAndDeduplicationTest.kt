package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ConfigFingerprint
import com.example.data.local.ServerConfigEntity
import com.example.data.local.ServerLimits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServerLimitsAndDeduplicationTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun maxActiveConfigs_isStrictlyTwelve() {
        assertEquals(12, ServerLimits.MAX_ACTIVE_CONFIGS)
    }

    @Test
    fun insertCapped_enforcesStrictMax12LimitEvenWith3000Candidates() = runBlocking {
        val largeCandidateList = (1..3000).map { i ->
            ServerConfigEntity(
                name = "Server $i",
                protocol = if (i % 2 == 0) "vless" else "trojan",
                host = "node-$i.example.com",
                port = 443,
                rawLink = "vless://uuid-$i@node-$i.example.com:443",
                uuidOrPassword = "uuid-$i",
                countryCode = "US"
            )
        }

        val inserted = db.serverDao().insertCapped(largeCandidateList, ServerLimits.MAX_ACTIVE_CONFIGS)
        assertEquals(12, inserted)

        val totalInDb = db.serverDao().getServerCountNow()
        assertEquals(12, totalInDb)

        // Attempting to insert another batch must not exceed 12
        val secondBatch = (3001..3500).map { i ->
            ServerConfigEntity(
                name = "Server $i",
                protocol = "vmess",
                host = "node-$i.example.com",
                port = 443,
                rawLink = "vmess://uuid-$i@node-$i.example.com:443",
                uuidOrPassword = "uuid-$i"
            )
        }
        val secondInserted = db.serverDao().insertCapped(secondBatch, ServerLimits.MAX_ACTIVE_CONFIGS)
        assertEquals(0, secondInserted)
        assertEquals(12, db.serverDao().getServerCountNow())
    }

    @Test
    fun configFingerprint_differentiatesSameHostPortWithDifferentCredentials() {
        val server1 = ServerConfigEntity(
            name = "S1",
            protocol = "vless",
            host = "proxy.example.com",
            port = 443,
            rawLink = "vless://user1@proxy.example.com:443",
            uuidOrPassword = "user1",
            securityType = "reality"
        )
        val server2 = ServerConfigEntity(
            name = "S2",
            protocol = "vless",
            host = "proxy.example.com",
            port = 443,
            rawLink = "vless://user2@proxy.example.com:443",
            uuidOrPassword = "user2",
            securityType = "reality"
        )
        val server1Duplicate = server1.copy(id = 99, name = "Different Name Label")

        val fp1 = ConfigFingerprint.of(server1)
        val fp2 = ConfigFingerprint.of(server2)
        val fp1Dup = ConfigFingerprint.of(server1Duplicate)

        assertNotEquals(fp1, fp2)
        assertEquals(fp1, fp1Dup)
    }

    @Test
    fun newCandidates_defaultToUnverifiedAndNegativePing() = runBlocking {
        val candidate = ServerConfigEntity(
            name = "Test Node",
            protocol = "vless",
            host = "node.test.com",
            port = 443,
            rawLink = "vless://uuid@node.test.com:443",
            uuidOrPassword = "uuid"
        )

        db.serverDao().insertCapped(listOf(candidate))
        val stored = db.serverDao().getAllServersOnce().first()

        assertFalse("New candidates must not be marked working", stored.isWorking)
        assertEquals("New candidates must have negative ping (-1)", -1L, stored.pingMs)
        assertEquals(0L, stored.lastTestedTime)
    }

    @Test
    fun recordFailure_marksWorkingFalseAndNegativePing() = runBlocking {
        val candidate = ServerConfigEntity(
            name = "Test Node",
            protocol = "trojan",
            host = "trojan.test.com",
            port = 443,
            rawLink = "trojan://pass@trojan.test.com:443",
            uuidOrPassword = "pass",
            isWorking = true,
            pingMs = 90L
        )
        val id = db.serverDao().insertServer(candidate)

        db.serverDao().recordFailure(id)
        val updated = db.serverDao().getServerById(id)
        assertNotNull(updated)
        assertFalse(updated!!.isWorking)
        assertEquals(-1L, updated.pingMs)
        assertEquals(1, updated.failureCount)
    }

    @Test
    fun verifiedReplacement_atomicallyReplacesBrokenVictimWhenFull() = runBlocking {
        // Fill pool with 11 healthy and 1 broken non-favorite server
        for (i in 1..11) {
            db.serverDao().insertServer(
                ServerConfigEntity(
                    name = "Healthy $i",
                    protocol = "vless",
                    host = "healthy-$i.com",
                    port = 443,
                    rawLink = "vless://h-$i@healthy-$i.com:443",
                    uuidOrPassword = "h-$i",
                    isWorking = true,
                    pingMs = 50L + i
                )
            )
        }
        val brokenId = db.serverDao().insertServer(
            ServerConfigEntity(
                name = "Broken Node",
                protocol = "vless",
                host = "broken.com",
                port = 443,
                rawLink = "vless://broken@broken.com:443",
                uuidOrPassword = "broken",
                isWorking = false,
                isFavorite = false,
                isManual = false,
                pingMs = -1L,
                failureCount = 3
            )
        )
        assertEquals(12, db.serverDao().getServerCountNow())

        // Insert verified new candidate replacing the broken one
        val newVerified = ServerConfigEntity(
            name = "Verified New Node",
            protocol = "trojan",
            host = "verified.com",
            port = 443,
            rawLink = "trojan://pass@verified.com:443",
            uuidOrPassword = "pass"
        )

        val replaced = db.serverDao().insertVerifiedReplacingBroken(newVerified, ServerLimits.MAX_ACTIVE_CONFIGS)
        assertTrue(replaced)

        assertEquals(12, db.serverDao().getServerCountNow())
        assertNull("Broken victim must have been evicted", db.serverDao().getServerById(brokenId))
        val verifiedStored = db.serverDao().getAllServersOnce().find { it.host == "verified.com" }
        assertNotNull(verifiedStored)
        assertTrue(verifiedStored!!.isWorking)
    }

    @Test
    fun manualImport_canReplaceAutoEntryWithoutExceedingTwelveOrRemovingFavorite() = runBlocking {
        // One favorite must survive; the other eleven are replaceable auto-discovered entries.
        db.serverDao().insertServer(
            ServerConfigEntity(
                name = "Favorite",
                protocol = "vless",
                host = "favorite.example.com",
                port = 443,
                rawLink = "vless://favorite@favorite.example.com:443",
                uuidOrPassword = "favorite",
                isFavorite = true,
                isManual = false,
                isWorking = true,
                pingMs = 30L
            )
        )
        for (i in 1..11) {
            db.serverDao().insertServer(
                ServerConfigEntity(
                    name = "Auto $i",
                    protocol = "vless",
                    host = "auto-$i.example.com",
                    port = 443,
                    rawLink = "vless://auto-$i@auto-$i.example.com:443",
                    uuidOrPassword = "auto-$i",
                    isFavorite = false,
                    isManual = false,
                    isWorking = i != 1,
                    pingMs = if (i == 1) -1L else 40L + i
                )
            )
        }
        assertEquals(12, db.serverDao().getServerCountNow())

        val imported = ServerConfigEntity(
            name = "Imported manual",
            protocol = "trojan",
            host = "manual.example.com",
            port = 443,
            rawLink = "trojan://secret@manual.example.com:443",
            uuidOrPassword = "secret",
            isManual = true,
            isWorking = true,
            pingMs = 10L
        )
        val inserted = db.serverDao().insertManualReplacingAuto(imported, ServerLimits.MAX_ACTIVE_CONFIGS)

        assertTrue(inserted)
        assertEquals(12, db.serverDao().getServerCountNow())
        assertNotNull(db.serverDao().getAllServersOnce().firstOrNull { it.host == "favorite.example.com" })
        val storedManual = db.serverDao().getAllServersOnce().firstOrNull { it.host == "manual.example.com" }
        assertNotNull(storedManual)
        assertTrue(storedManual!!.isManual)
        assertFalse("Imported configs must remain unverified until a real connection test", storedManual.isWorking)
        assertEquals(-1L, storedManual.pingMs)
        assertNull(db.serverDao().getAllServersOnce().firstOrNull { it.host == "auto-1.example.com" })
    }

    @Test
    fun preloadedConfigs_containsExactlyTwelveServers() {
        assertEquals(12, com.example.data.local.PreloadedConfigs.DEFAULT_SERVERS.size)
        assertEquals(12, ServerLimits.MAX_ACTIVE_CONFIGS)
    }

    @Test
    fun getBestCandidate_selectsCandidateEvenWhenUntested() = runBlocking {
        // Initially pool is empty, should return null
        assertNull(db.serverDao().getBestCandidate())

        // Insert unverified server (isWorking = false, ping = -1)
        val candidate = ServerConfigEntity(
            name = "Untested Node",
            protocol = "vless",
            host = "untested.com",
            port = 443,
            rawLink = "vless://u@untested.com:443",
            uuidOrPassword = "u",
            isWorking = false,
            pingMs = -1L
        )
        db.serverDao().insertCapped(listOf(candidate), ServerLimits.MAX_ACTIVE_CONFIGS)

        val best = db.serverDao().getBestCandidate()
        assertNotNull("getBestCandidate must not be null when candidate exists", best)
        assertEquals("untested.com", best!!.host)
    }

    @Test
    fun seedInitialDataIfNeeded_populatesSourcesAndServers() = runBlocking {
        assertEquals(0, db.sourceDao().getEnabledSources().size)
        assertEquals(0, db.serverDao().getServerCountNow())

        db.seedInitialDataIfNeeded()

        assertTrue(db.sourceDao().getEnabledSources().isNotEmpty())
        assertEquals(12, db.serverDao().getServerCountNow())
    }
}
