package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.UserManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.provider.Telephony
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.SharedPref
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

typealias Lambda = () -> Unit
typealias Lambda1<A> = (A) -> Unit
typealias Lambda2<A, B> = (A, B) -> Unit
typealias Lambda3<A, B, C> = (A, B, C) -> Unit
typealias Lambda4<A, B, C, D> = (A, B, C, D) -> Unit


fun String.resolveTimeTags(): String {
    val now = LocalDateTime.now()
    return this
        .replace("{year}", now.year.toString())
        .replace("{month}", now.monthValue.toString().padStart(2, '0'))
        .replace("{day}", now.dayOfMonth.toString().padStart(2, '0'))
        .replace("{hour}", now.hour.toString().padStart(2, '0'))
        .replace("{minute}", now.minute.toString().padStart(2, '0'))
        .replace("{second}", now.second.toString().padStart(2, '0'))

        .replace("{month_index}", now.monthValue.toString())
        .replace("{day_index}", now.dayOfMonth.toString())
        .replace("{hour_index}", now.hour.toString())
        .replace("{minute_index}", now.minute.toString())
        .replace("{second_index}", now.second.toString())
}

fun String.resolvePathTags(): String {
    return this
        .replace(
            "{Downloads}",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        )
}

// parse json -> map
private fun toValue(element: Any) = when (element) {
    JSONObject.NULL -> null
    is JSONObject -> element.toMap()
    is JSONArray -> element.toList()
    else -> element
}
fun JSONArray.toList(): List<Any?> =
    (0 until length()).map { index -> toValue(get(index)) }

fun JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key -> toValue(get(key)) }

fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> toValue(get(key)) as String }



object Util {

    fun fullDateString(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd\nHH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return dateFormat.format(date)
    }

    fun hourMin(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = Date(timestamp)
        return dateFormat.format(date)
    }

    fun isToday(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        val currentDate = calendar.get(Calendar.DAY_OF_MONTH)
        calendar.timeInMillis = timestamp
        val date = calendar.get(Calendar.DAY_OF_MONTH)
        return currentDate == date
    }

    // For history record time
    fun getDayOfWeek(ctx: Context, timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysArray = ctx.resources.getStringArray(R.array.short_weekdays).asList()
        return daysArray[dayOfWeek - 1]
    }

    // For history record time.
    fun isWithinAWeek(timestamp: Long): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        val difference = currentTimeMillis - timestamp
        val millisecondsInWeek = 7 * 24 * 60 * 60 * 1000 // 7 days in milliseconds
        return difference <= millisecondsInWeek
    }

    fun formatTime(ctx: Context, timestamp: Long): String {
        return if (isToday(timestamp)) {
            hourMin(timestamp)
        } else if (isWithinAWeek(timestamp)) {
            getDayOfWeek(ctx, timestamp) + "\n" + hourMin(timestamp)
        } else {
            fullDateString(timestamp)
        }
    }

    // check if a string only contains:
    //   digit spaces + - ( )
    // Param:
    //   when `force`==true, numbers like "+123####" will also be cleared.
    val pattern = "^[0-9\\s+\\-()]*\$".toRegex()
    fun clearNumber(number: String, force: Boolean = false): String {
        // check if it contains alphabetical characters like "Microsoft"
        if (!force && !pattern.matches(number)) { // don't clear for enterprise string number
            return number
        }

        return number
            .trimStart('0') // remove leading "0"s
            .replace("-", "")
            .replace("+", "")
            .replace(" ", "")
            .replace("(", "")
            .replace(")", "")
    }

    fun extractString(regex: Regex, haystack: String): String? {
        /*
            lookbehind: has `value`, no `group(1)`
            capturing group: has both, should use group(1) only

            so the logic is:
                if has `value` && no `group(1)`
                    use `value`
                else if has both
                    use `group1`
         */
        val result = regex.find(haystack)
        val v = result?.value
        val g1 = result?.groupValues?.getOrNull(1)

        if (v != null && g1 == null) {
            return v
        } else if (v != null && g1 != null) {
            return g1
        }

        return null
    }

    fun truncate(str: String, limit: Int = 300, showEllipsis: Boolean = true): String {
        return if (str.length >= limit)
            str.substring(0, limit) + if (showEllipsis) "..." else ""
        else
            str
    }

    // for display on Util
    @SuppressLint("DefaultLocale")
    fun timeRangeStr(
        ctx: Context,
        stHour: Int, stMin: Int, etHour: Int, etMin: Int
    ): String {
        if (stHour == 0 && stMin == 0 && etHour == 0 && etMin == 0)
            return ctx.getString(R.string.entire_day)
        return String.format("%02d:%02d - %02d:%02d", stHour, stMin, etHour, etMin)
    }

    fun currentHourMin(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        val currHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currMinute = calendar.get(Calendar.MINUTE)
        return Pair(currHour, currMinute)
    }

    fun currentHourMinSec(): Triple<Int, Int, Int> {
        val calendar = Calendar.getInstance()
        val currHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currMinute = calendar.get(Calendar.MINUTE)
        val currSecond = calendar.get(Calendar.SECOND)
        return Triple(currHour, currMinute, currSecond)
    }

    fun isCurrentTimeWithinRange(stHour: Int, stMin: Int, etHour: Int, etMin: Int): Boolean {
        val (currHour, currMinute) = currentHourMin()
        val curr = currHour * 60 + currMinute

        val rangeStart = stHour * 60 + stMin
        val rangeEnd = etHour * 60 + etMin

        return if (rangeStart <= rangeEnd) {
            curr in rangeStart..rangeEnd
        } else {
            curr >= rangeStart || curr <= rangeEnd
        }
    }

    private fun isRegexValid(regex: String): Boolean {
        return try {
            Regex(regex)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun validateRegex(
        ctx: Context,
        regexStr: String,
        disableNumberOptimization: Boolean
    ): String? {
        var s = regexStr
        if (s.isNotEmpty() && s.trim() != s)
            return ctx.getString(R.string.pattern_contain_invisible_characters)

        if (!isRegexValid(s))
            return ctx.getString(R.string.invalid_regex_pattern)

        if (s.startsWith("^")) // may be it's `^0` or `^+1`
            s = s.substring(1)

        if ((s.startsWith("+") || s.startsWith("\\+")) && !disableNumberOptimization)
            return listOf(
                ctx.getString(R.string.pattern_contain_leading_plus),
                ctx.getString(R.string.disable_number_optimization),
                ctx.getString(R.string.check_balloon_for_explanation),
            ).joinToString(separator = "\n")

        if (s.startsWith("0") && !disableNumberOptimization) {
            return listOf(
                ctx.getString(R.string.pattern_contain_leading_zeroes),
                ctx.getString(R.string.disable_number_optimization),
                ctx.getString(R.string.check_balloon_for_explanation),
            ).joinToString(separator = "\n")
        }

        return null
    }

    fun flagsToRegexOptions(flags: Int): Set<RegexOption> {
        val opts = mutableSetOf<RegexOption>()
        if (flags.hasFlag(Def.FLAG_REGEX_IGNORE_CASE)) {
            opts.add(RegexOption.IGNORE_CASE)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_MULTILINE)) {
            opts.add(RegexOption.MULTILINE)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_DOT_MATCH_ALL)) {
            opts.add(RegexOption.DOT_MATCHES_ALL)
        }
        if (flags.hasFlag(Def.FLAG_REGEX_LITERAL)) {
            opts.add(RegexOption.LITERAL)
        }
        return opts
    }


    private var cacheAppList: List<AppInfo>? = null
    private val lock_1 = Any()

    @SuppressLint("UseCompatLoadingForDrawables")
    fun listApps(ctx: Context): List<AppInfo> {
        synchronized(lock_1) {
            if (cacheAppList == null) {
                val pm = ctx.packageManager

                val packageInfos = Permissions.getPackagesHoldingPermissions(
                    pm,
                    arrayOf(Manifest.permission.INTERNET)
                )

                cacheAppList = packageInfos.filter {
                    if (it.applicationInfo == null)
                        false
                    else
                        (it.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }.map {
                    val appInfo = it.applicationInfo

                    AppInfo().apply {
                        pkgName = it.packageName
                        label = appInfo!!.loadLabel(pm).toString()
                        icon = appInfo.loadIcon(pm)
                    }
                }
            }
        }

        return cacheAppList!!
    }

    // android<13 will always return `false`
    @RequiresApi(Def.ANDROID_13)
    fun isDefaultSmsAppNotificationEnabled(ctx: Context): Boolean {
        // It can throw exception, ref: https://github.com/aj3423/SpamBlocker/issues/122
        try {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)

            val pm = ctx.packageManager

            val result = pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, defSmsPkg)

            return result == PERMISSION_GRANTED
        } catch (e: Exception) {
            loge("exception in isDefaultSmsAppNotificationEnabled: $e")
            return false
        }
    }

    fun openSettingForDefaultSmsApp(ctx: Context) {
        try {
            val defSmsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", defSmsPkg, null)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            loge("exception in openSettingForDefaultSmsApp: $e")
        }
    }

    fun isPackageInstalled(ctx: Context, pkgName: String): Boolean {
        val pm = ctx.packageManager
        val flags = 0
        return try {
            pm.getPackageUid(pkgName, flags)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isRunningInWorkProfile(ctx: Context): Boolean {
        val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager

        return if (Build.VERSION.SDK_INT >= Def.ANDROID_11) { // android 10 ignored
            um.isManagedProfile
        } else
            false
    }

    fun writeDataToUri(ctx: Context, uri: Uri, dataToWrite: ByteArray): Boolean {
        return try {
            ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(dataToWrite)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    fun readDataFromUri(ctx: Context, uri: Uri): ByteArray? {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.buffered().readBytes()
            }
        } catch (_: IOException) {
            null
        }
    }

    fun getFilename(ctx: Context, uri: Uri): String? {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var filename: String? = null

        cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)?.let { nameIndex ->
            cursor.moveToFirst()

            filename = cursor.getString(nameIndex)
            cursor.close()
        }

        return filename
    }

    fun basename(fn: String): String {
        val lastDotIndex = fn.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fn.substring(0, lastDotIndex)
        } else {
            fn
        }
    }

    fun readFile(dir: String, filename: String): ByteArray {
        val file = File(dir, filename)
        val data = file.readBytes()
        return data
    }
    fun writeFile(dir: String, filename: String, data: ByteArray) {
        val file = File(dir, filename)
        file.writeBytes(data)
    }

    // returns `true` if it's the first time
    fun doOnce(ctx: Context, tag: String, doSomething: () -> Unit): Boolean {
        val spf = SharedPref(ctx)
        val alreadyExist = spf.readBoolean(tag, false)
        if (!alreadyExist) {
            spf.writeBoolean(tag, true)
            doSomething()
            return true
        }
        return false
    }
    fun isUUID(string: String): Boolean {
        return try {
            UUID.fromString(string)
            true
        } catch (exception: IllegalArgumentException) {
            false
        }
    }
}