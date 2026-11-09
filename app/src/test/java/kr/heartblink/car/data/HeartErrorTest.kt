package kr.heartblink.car.data

import io.github.jan.supabase.exceptions.RestException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HeartErrorTest {

    @Test
    fun sqlFunctionCodesMapToKoreanErrors() {
        assertEquals(HeartError.AlreadyConnected, HeartError.fromMessage("already_connected"))
        assertEquals(HeartError.PlateNotFound, HeartError.fromMessage("plate_not_found"))
        assertEquals(HeartError.OwnPlate, HeartError.fromMessage("own_plate"))
        assertEquals(HeartError.PartnerBusy, HeartError.fromMessage("partner_busy"))
        assertEquals(HeartError.NoRequest, HeartError.fromMessage("no_request"))
        assertEquals(HeartError.NotConnected, HeartError.fromMessage("not_connected"))
        assertEquals("그 번호로 가입한 차가 없어요", HeartError.PlateNotFound.text)
    }

    @Test
    fun constraintMessagesMap() {
        assertEquals(
            HeartError.PlateTaken,
            HeartError.fromMessage("duplicate key value violates unique constraint \"profiles_plate_key\""),
        )
        assertEquals(
            HeartError.BadPlate,
            HeartError.fromMessage("new row for relation \"profiles\" violates check constraint \"profiles_plate_check\""),
        )
        assertEquals(
            HeartError.NicknameTooLong,
            HeartError.fromMessage("new row for relation \"couples\" violates check constraint \"couples_partner_nickname_check\""),
        )
        assertEquals(
            HeartError.NotAllowed,
            HeartError.fromMessage("new row violates row-level security policy for table \"messages\""),
        )
    }

    @Test
    fun restExceptionUsesItsErrorField() {
        val e = RestException("plate_not_found", null, 400, "plate_not_found\nURL: https://x")
        assertEquals(HeartError.PlateNotFound, HeartError.from(e))
    }

    @Test
    fun unknownAndNetwork() {
        assertTrue(HeartError.fromMessage("something else") is HeartError.Unknown)
        assertEquals(HeartError.Network, HeartError.from(IOException("timeout")))
        assertEquals(HeartError.Offline, HeartError.from(HeartError.Offline))
    }

    @Test
    fun textsHaveNoMiddleDot() {
        val all = listOf(
            HeartError.AlreadyConnected, HeartError.PlateNotFound, HeartError.OwnPlate, HeartError.PartnerBusy,
            HeartError.NoRequest, HeartError.NotConnected, HeartError.NoProfile, HeartError.BadPlate,
            HeartError.PlateTaken, HeartError.BadName, HeartError.NicknameTooLong, HeartError.TextTooLong,
            HeartError.NotAllowed, HeartError.Network, HeartError.Offline, HeartError.Unknown(null),
        )
        all.forEach { assertTrue(it.text, '·' !in it.text && it.text.isNotBlank()) }
    }
}
