package kr.heartblink.car.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Rows of the Supabase tables in supabase/migrations/20261106000000_init.sql.
// Column names are snake_case in the database, so every field carries @SerialName.

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val plate: String,
    @SerialName("created_at") val createdAt: Instant? = null,
)

@Serializable
data class Couple(
    val id: String,
    val requester: String,
    val partner: String,
    val status: String,
    // written by the partner, shown on the requester's plate
    @SerialName("requester_nickname") val requesterNickname: String? = null,
    // written by the requester, shown on the partner's plate
    @SerialName("partner_nickname") val partnerNickname: String? = null,
    @SerialName("first_met") val firstMet: LocalDate? = null,
    @SerialName("question_no") val questionNo: Int = 1,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("accepted_at") val acceptedAt: Instant? = null,
    @SerialName("ended_at") val endedAt: Instant? = null,
) {
    val isPending: Boolean get() = status == STATUS_PENDING
    val isActive: Boolean get() = status == STATUS_ACTIVE
    val isEnded: Boolean get() = status == STATUS_ENDED

    /** The other member's user id. */
    fun partnerOf(me: String): String = if (requester == me) partner else requester

    /** The nickname I gave my partner's plate. */
    fun nicknameIGave(me: String): String? = if (requester == me) partnerNickname else requesterNickname

    /** The nickname my partner gave my plate. */
    fun myNickname(me: String): String? = if (requester == me) requesterNickname else partnerNickname

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACTIVE = "active"
        const val STATUS_ENDED = "ended"
    }
}

@Serializable
data class Message(
    val id: String,
    @SerialName("couple_id") val coupleId: String,
    val sender: String,
    val receiver: String,
    @SerialName("condition_group") val conditionGroup: String,
    @SerialName("condition_value") val conditionValue: String,
    @SerialName("place_name") val placeName: String? = null,
    @SerialName("place_lat") val placeLat: Double? = null,
    @SerialName("place_lng") val placeLng: Double? = null,
    @SerialName("place_radius_m") val placeRadiusM: Int? = null,
    @SerialName("opens_from") val opensFrom: Instant? = null,
    val body: String = "",
    @SerialName("audio_path") val audioPath: String? = null,
    @SerialName("audio_ms") val audioMs: Int? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("opened_at") val openedAt: Instant? = null,
    @SerialName("heard_at") val heardAt: Instant? = null,
) {
    val group: ConditionGroup? get() = ConditionGroup.fromKey(conditionGroup)
    val place: Place?
        get() = if (placeLat != null && placeLng != null) {
            Place(placeName ?: "", placeLat, placeLng, placeRadiusM ?: Place.DEFAULT_RADIUS_M)
        } else null
    val isOpened: Boolean get() = openedAt != null
    val isHeard: Boolean get() = heardAt != null

    /** True once the earliest opening time has passed; the car still has to meet the condition. */
    fun canOpenAt(now: Instant): Boolean = opensFrom == null || opensFrom <= now
}

/** Body of a direct insert into messages. Null fields are left to the database defaults. */
@Serializable
internal data class NewMessage(
    @SerialName("couple_id") val coupleId: String,
    val sender: String,
    val receiver: String,
    @SerialName("condition_group") val conditionGroup: String,
    @SerialName("condition_value") val conditionValue: String,
    @SerialName("place_name") val placeName: String? = null,
    @SerialName("place_lat") val placeLat: Double? = null,
    @SerialName("place_lng") val placeLng: Double? = null,
    @SerialName("place_radius_m") val placeRadiusM: Int? = null,
    @SerialName("opens_from") val opensFrom: Instant? = null,
    val body: String = "",
    @SerialName("audio_path") val audioPath: String? = null,
    @SerialName("audio_ms") val audioMs: Int? = null,
)

@Serializable
data class Question(
    val no: Int,
    val body: String,
)

@Serializable
data class Answer(
    @SerialName("couple_id") val coupleId: String,
    @SerialName("question_no") val questionNo: Int,
    @SerialName("user_id") val userId: String,
    val body: String = "",
    @SerialName("audio_path") val audioPath: String? = null,
    @SerialName("audio_ms") val audioMs: Int? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
)

@Serializable
data class DriveStatus(
    @SerialName("user_id") val userId: String,
    val driving: Boolean = false,
    @SerialName("updated_at") val updatedAt: Instant? = null,
)

@Serializable
data class DriveDay(
    @SerialName("user_id") val userId: String,
    val day: LocalDate,
    val km: Double = 0.0,
)

// ---------------------------------------------------------------- shapes for the screen layer

/** Message opening conditions; [key] is the value stored in messages.condition_group. */
enum class ConditionGroup(val key: String) {
    Place("place"),
    Vehicle("vehicle"),
    FirstStart("first_start"),
    Arrival("arrival"),
    Time("time");

    companion object {
        fun fromKey(key: String): ConditionGroup? = entries.firstOrNull { it.key == key }
    }
}

/** Allowed condition_value strings per group. */
object ConditionValue {
    // vehicle
    const val WIPER = "wiper"
    const val HEADLIGHT = "headlight"
    const val SEAT_HEATER = "seat_heater"
    const val AIRCON = "aircon"
    const val WINDOW = "window"
    // time
    const val DAWN = "dawn"
    const val MORNING = "morning"
    const val NOON = "noon"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"
    // arrival
    const val DESTINATION = "destination"
    const val PARKED = "parked"
    // first_start, place
    const val FIRST_START = "first_start"
    const val PLACE = "place"
}

data class Place(
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = DEFAULT_RADIUS_M,
) {
    companion object {
        /** Wide enough to open just by passing by (300, 500 or 1000 can be picked). */
        const val DEFAULT_RADIUS_M = 500
    }
}

/** A connection request waiting for my answer, with the requester's profile when visible. */
data class PendingRequest(
    val couple: Couple,
    val requester: Profile?,
)

/**
 * One question for our couple. [partner] stays null until both have answered;
 * the database policy hides it before that.
 */
data class QuestionState(
    val question: Question,
    val mine: Answer?,
    val partner: Answer?,
) {
    val bothAnswered: Boolean get() = mine != null && partner != null
}

/** An unopened message for me, with its voice file already copied to the cache when it has one. */
data class PendingMessage(
    val message: Message,
    val localAudio: java.io.File?,
)
