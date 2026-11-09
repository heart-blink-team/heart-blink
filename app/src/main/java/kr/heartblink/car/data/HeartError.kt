package kr.heartblink.car.data

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Failures the screen layer can show as they are. [text] is the Korean sentence for the user.
 * Repository calls that can fail return Result<T> whose failure is one of these.
 */
sealed class HeartError(val text: String) : Exception(text) {
    data object AlreadyConnected : HeartError("이미 연결된 차가 있어요")
    data object PlateNotFound : HeartError("그 번호로 가입한 차가 없어요")
    data object OwnPlate : HeartError("내 차 번호예요. 상대 차 번호를 넣어 주세요")
    data object PartnerBusy : HeartError("상대가 이미 다른 차와 연결되어 있어요")
    data object NoRequest : HeartError("받은 연결 요청이 없어요")
    data object NotConnected : HeartError("아직 연결된 차가 없어요")
    data object NoProfile : HeartError("먼저 이름과 차 번호를 등록해 주세요")
    data object BadPlate : HeartError("차 번호를 12가3456처럼 넣어 주세요")
    data object PlateTaken : HeartError("이미 다른 차가 쓰고 있는 번호예요")
    data object BadName : HeartError("이름은 1자에서 20자까지 쓸 수 있어요")
    data object NicknameTooLong : HeartError("애칭은 5자까지 쓸 수 있어요")
    data object TextTooLong : HeartError("글은 200자까지 쓸 수 있어요")
    data object NotAllowed : HeartError("지금은 할 수 없는 일이에요")
    data object Network : HeartError("인터넷 연결을 확인해 주세요")
    data object Offline : HeartError("서버가 아직 연결되지 않았어요")
    data class Unknown(val detail: String?) : HeartError("잠시 후 다시 시도해 주세요")

    companion object {
        /** Maps any throwable from supabase-kt or Ktor to a HeartError. */
        fun from(t: Throwable): HeartError = when (t) {
            is HeartError -> t
            is RestException -> fromMessage(listOfNotNull(t.error, t.description).joinToString(" "))
                .let { if (it is Unknown) Unknown(t.error) else it }
            is HttpRequestException, is HttpRequestTimeoutException, is IOException -> Network
            else -> Unknown(t.message)
        }

        /**
         * Maps a database error message. SQL functions raise plain codes such as 'plate_not_found';
         * table constraints come back as Postgres sentences naming the constraint.
         */
        fun fromMessage(message: String?): HeartError {
            val m = message?.trim().orEmpty()
            return when {
                m.contains("already_connected") -> AlreadyConnected
                m.contains("plate_not_found") -> PlateNotFound
                m.contains("own_plate") -> OwnPlate
                m.contains("partner_busy") -> PartnerBusy
                m.contains("no_request") -> NoRequest
                m.contains("not_connected") -> NotConnected
                m.contains("profiles_plate_key") -> PlateTaken
                m.contains("profiles_plate_check") -> BadPlate
                m.contains("profiles_name_check") -> BadName
                m.contains("nickname_check") -> NicknameTooLong
                m.contains("body_check") -> TextTooLong
                m.contains("row-level security") -> NotAllowed
                else -> Unknown(m.ifEmpty { null })
            }
        }
    }
}
