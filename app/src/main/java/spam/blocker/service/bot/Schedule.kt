package spam.blocker.service.bot

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.rememberSaveableMutableStateListOf
import spam.blocker.ui.widgets.HourMinInput
import spam.blocker.ui.widgets.RowCenter
import spam.blocker.ui.widgets.WeekdayPicker2
import spam.blocker.util.LocalDateTimeMockk
import java.lang.Exception
import java.time.DayOfWeek

// When adding a new ISchedule type, follow all the steps:
//  - add to  `ScheduleType`
//  - implement it
//  - add to `defaultSchedules`
//  - add to  `botModule` in BotSerializersModule.kt

// The default values for all ISchedule
val defaultSchedules = listOf (
    Daily(),
    Weekly(),
)

val Workdays = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
val Weekend = listOf(SATURDAY, SUNDAY)

// Only append to this list, do not modify existing ones
enum class ScheduleType {
    Daily,
    Weekly
}

interface ISchedule {
    fun isValid(): Boolean
    fun nextOccurrence(): Duration

    // Different type id for each classes
    fun type(): ScheduleType

    // A displaying label for the selecting spinner
    fun label(ctx: Context): String

    // A brief summary for all the current configurations, like: "Everyday 12:00"
    fun summary(ctx: Context): String

    // Render configuration items on the editing dialog
    @Composable
    fun Options()
}

val scheduleSaver = Saver<MutableState<ISchedule?>, String>(
    save = { it.value?.serialize() },
    restore = { mutableStateOf(it.parseSchedule()) }
)

@Composable
fun rememberSaveableScheduleState(schedule: ISchedule?): MutableState<ISchedule?> {
    return rememberSaveable(saver = scheduleSaver) {
        mutableStateOf(schedule)
    }
}

fun ISchedule.clone(): ISchedule {
    return this.serialize().parseSchedule()!!
}

// Serialize self to json string
fun ISchedule.serialize(): String {
    return botJson.encodeToString(PolymorphicSerializer(ISchedule::class), this)
}

// Generate a *concrete* ISchedule from json string.
fun String.parseSchedule(): ISchedule? {
    if (isEmpty())
        return null

    return try {
        botJson.decodeFromString(PolymorphicSerializer(ISchedule::class), this)
    } catch (_: Exception) {
        null
    }
}

@Serializable
@SerialName("Time")
class Time(
    var hour: Int = 0,
    var min: Int = 0,
) {
    fun isValid(): Boolean {
        return (hour in 0..23) && (min in 0..59)
    }
    fun summary() : String { return "%02d:%02d".format(hour, min) }
}

@Serializable
@SerialName("Daily")
class Daily(
    var time: Time = Time()
) : ISchedule {
    override fun isValid(): Boolean {
        return time.isValid()
    }

    override fun nextOccurrence(): Duration {
        val now = LocalDateTimeMockk.now()
        val targetTime = now.withHour(time.hour).withMinute(time.min).withSecond(0).withNano(0)

        return if (now.isBefore(targetTime)) {
            Duration.between(now, targetTime)
        } else {
            Duration.between(now, targetTime.plusDays(1))
        }
    }

    override fun type(): ScheduleType {
        return ScheduleType.Daily
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.daily)
    }

    override fun summary(ctx: Context): String {
        return ctx.getString(R.string.daily) + " " + time.summary()
    }

    @Composable
    override fun Options() {
        RowCenter(modifier = M.fillMaxWidth()) {
            HourMinInput(time.hour, time.min) { h, m ->
                time.hour = h
                time.min = m
            }
        }
    }
}

@Serializable
@SerialName("Weekly")
class Weekly(
    var weekdays: List<DayOfWeek> = listOf(MONDAY), // 1 ~ 7 == Monday ~ Sunday
    var time: Time = Time()
) : ISchedule {
    override fun isValid(): Boolean {
        return time.isValid() &&
                (weekdays.size in 1..7) &&
                weekdays.all { it in MONDAY..SUNDAY }
    }

    override fun nextOccurrence(): Duration {
        val weekDays = weekdays.sorted()

        val now = LocalDateTimeMockk.now()
        // Find the target weekday within this week
        val nextDayInThisWeek = weekDays.firstOrNull {
            if (it == now.dayOfWeek) { // it's today, check if the current hour/min/sec exceeds the target
                val nowWithTargetTime = now.toLocalTime()
                    .withHour(time.hour).withMinute(time.min).withSecond(0).withNano(0)

                now.toLocalTime().isBefore(nowWithTargetTime)
            } else {
                it > now.dayOfWeek
            }
        }

        // Calculate the delay until the next weekday at the target time
        val targetLocalDateTime = LocalDateTime.of(
            now.year, now.month, now.dayOfMonth,
            time.hour, time.min, 0
        )
        val target = if (nextDayInThisWeek != null) {
            targetLocalDateTime.with(DayOfWeek.of(nextDayInThisWeek.value))
        } else {
            targetLocalDateTime.with(TemporalAdjusters.next(weekDays.first()))
        }

        val delay = Duration.between(now, target)
        return delay
    }

    override fun type(): ScheduleType {
        return ScheduleType.Weekly
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.weekly)
    }

    override fun summary(ctx: Context): String {
        val days = weekdays.sorted()
        if (days.size == 7) {
            return ctx.getString(R.string.everyday) + " " + time.summary()
        }
        if (days == Workdays)
            return ctx.getString(R.string.workdays) + " " + time.summary()
        if (days == Weekend)
            return ctx.getString(R.string.weekend) + " " + time.summary()

        val labels = ctx.resources.getStringArray(R.array.short_weekdays) // 0-6: Sun-Sat
        val daySummary = days.joinToString(", ") {
            if (it.value == 7) labels[0] else labels[it.value]
        }
        return daySummary + " " + time.summary()
    }

    @Composable
    override fun Options() {
        Column {
            Spacer(modifier = M.height(8.dp))

            WeekdayPicker2(initialDays = weekdays) {
                weekdays = it
            }
            Spacer(modifier = M.height(16.dp))

            RowCenter(modifier = M.fillMaxWidth()) {
                HourMinInput(time.hour, time.min) { h, m ->
                    time.hour = h
                    time.min = m
                }
            }
        }
    }
}
