package kr.heartblink.car.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelJsonTest {
    // same settings supabase-kt uses by default
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun coupleRowFromPostgrest() {
        val row = """
            {"id":"c1","requester":"u1","partner":"u2","status":"active",
             "requester_nickname":"곰돌이","partner_nickname":null,"first_met":"2024-05-01",
             "question_no":3,"created_at":"2026-11-06T03:00:00.123456+00:00",
             "accepted_at":"2026-11-06T03:05:00+00:00","ended_at":null}
        """.trimIndent()
        val c = json.decodeFromString<Couple>(row)
        assertTrue(c.isActive)
        assertEquals(3, c.questionNo)
        assertEquals(LocalDate(2024, 5, 1), c.firstMet)
        assertEquals("u2", c.partnerOf("u1"))
        assertEquals("곰돌이", c.myNickname("u1"))
        assertNull(c.nicknameIGave("u1"))
        assertEquals(Instant.parse("2026-11-06T03:00:00.123456Z"), c.createdAt)
        assertEquals(c, json.decodeFromString<Couple>(json.encodeToString(c)))
    }

    @Test
    fun messageRoundTrip() {
        val m = Message(
            id = "m1", coupleId = "c1", sender = "u1", receiver = "u2",
            conditionGroup = "place", conditionValue = "place",
            placeName = "우리 동네 공원", placeLat = 37.5, placeLng = 127.0, placeRadiusM = 300,
            opensFrom = Instant.parse("2026-11-07T00:00:00Z"),
            body = "도착하면 들어 봐", audioPath = "c1/a.m4a", audioMs = 5200,
        )
        val text = json.encodeToString(m)
        assertTrue(text.contains("\"condition_group\":\"place\""))
        assertTrue(text.contains("\"opens_from\":\"2026-11-07T00:00:00Z\""))
        val back = json.decodeFromString<Message>(text)
        assertEquals(m, back)
        assertEquals(ConditionGroup.Place, back.group)
        assertEquals(300, back.place?.radiusM)
        assertFalse(back.canOpenAt(Instant.parse("2026-11-06T23:59:59Z")))
        assertTrue(back.canOpenAt(Instant.parse("2026-11-07T00:00:00Z")))
    }

    @Test
    fun newMessageLeavesDefaultsToTheDatabase() {
        val row = NewMessage(
            coupleId = "c1", sender = "u1", receiver = "u2",
            conditionGroup = ConditionGroup.Vehicle.key, conditionValue = ConditionValue.WIPER,
            body = "비 조심해",
        )
        val obj = json.encodeToJsonElement(NewMessage.serializer(), row).jsonObject
        assertEquals(setOf("couple_id", "sender", "receiver", "condition_group", "condition_value", "body"), obj.keys)
    }

    @Test
    fun answerDriveAndProfileRows() {
        val a = json.decodeFromString<Answer>(
            """{"couple_id":"c1","question_no":1,"user_id":"u1","body":"좋았어","audio_path":null,"audio_ms":null,"created_at":"2026-11-06T03:00:00+00:00"}"""
        )
        assertEquals(1, a.questionNo)
        val d = json.decodeFromString<DriveDay>("""{"user_id":"u1","day":"2026-11-06","km":12.5}""")
        assertEquals(12.5, d.km, 0.0)
        val s = json.decodeFromString<DriveStatus>("""{"user_id":"u2","driving":true,"updated_at":"2026-11-06T03:00:00+00:00"}""")
        assertTrue(s.driving)
        val p = json.decodeFromString<Profile>("""{"id":"u1","name":"민지","plate":"12가3456","created_at":"2026-11-06T03:00:00+00:00"}""")
        assertEquals(p, json.decodeFromString<Profile>(json.encodeToString(p)))
    }
}
