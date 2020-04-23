package com.example.goldentimealarm

import android.app.*
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

class AlarmService : IntentService("AlarmService") {

    private val GOLDEN_TIME_RIGHT_NOW = "GOLDEN_TIME_RIGHT_NOW"
    private val GOLDEN_TIME = "GOLDEN_TIME"
    private val NOT_GOLDEN_TIME = "NOT_GOLDEN_TIME"

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    MainActivity.GOLDEN_TIME_CHANNEL,
                    "Golden Time",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        println("on handled intent")
        goldenTimeCheck()
        println("request finished")
    }

    private fun goldenTimeCheck() {
        try {
            val conn =
                URL(getString(R.string.crawl_site)).openConnection() as HttpURLConnection

            val goldenTime = conn.run {
                setRequestMethod("GET")
                when {
                    isStartWith(responseCode, 2) -> readGoldenTime(inputStream)
                    else -> throw GoldenTimeException(GoldenTimeException.GOLDEN_TIME_REQUEST_FAIL)
                }
            }

            when (goldenTime.first) {
                GOLDEN_TIME_RIGHT_NOW, GOLDEN_TIME -> {
                    val leftTimeStr = leftTime(goldenTime.second)
                    cancelAlarm(
                        MainActivity.REQUEST_CODE,
                        Intent(applicationContext, AlarmService::class.java)
                    )
                    notifyNotification(leftTimeStr)
                }
            }

        } catch (ex: Exception) {
            cancelAlarm(
                MainActivity.REQUEST_CODE,
                Intent(applicationContext, AlarmService::class.java)
            )
            if (ex is GoldenTimeException) {
                showErrorcode(ex.errorCode)
            } else {
                showErrorcode(-1)
            }
            throw ex
        }
    }

    // 단순 크롤링에 의존하므로 바뀔 수 있음
    fun readGoldenTime(inputStream: InputStream): Pair<String, Date> {
        var dateLine = ""
        var isGoldenTime = true
        BufferedReader(InputStreamReader(inputStream)).use {
            var inputLine: String
            var isAfterGoldenTimeLine = false
            while (true) {
                inputLine = it.readLine() ?: break
                if (isAfterGoldenTimeLine or isGoldenTimeLineStart(inputLine)) {
                    isAfterGoldenTimeLine = true
                    if (isScriptOver(inputLine)) {
                        break
                    }
                    if (isDateLine(inputLine)) {    //"var date =" 뒤의 시간 문자열. 해당 시간에 골든타임 시작.
                        dateLine = inputLine
                    }
                    if (hasQuestionMark(inputLine)) {   //"??:??"가 있으면 골든타임이 아님
                        isGoldenTime = false
                    }
                }
                if (isGoldenTimeRightNow(inputLine)) {   //div class가 goldentime on인 경우 지금이 골든타임.
                    return Pair(GOLDEN_TIME_RIGHT_NOW, Date())
                }
            }
            if (isGoldenTime == true && dateLine == "") {    //QuestionMark도 없고 DateLine도 없는 경우. html 변동 예상. 종료.
                throw GoldenTimeException(GoldenTimeException.MAY_BE_HTML_CHANGED)
            }
        }

        return if (isGoldenTime) Pair(GOLDEN_TIME, getParsedDate(dateLine)) else Pair(
            NOT_GOLDEN_TIME,
            dummyTime()
        )
    }

    private fun getParsedDate(dateLine: String): Date {
        val trimedDateLine = dateLine.trim()
        if ("" === trimedDateLine) throw GoldenTimeException(GoldenTimeException.DATE_LINE_PARSE_ERROR)

        val startIndex = trimedDateLine.indexOf("=") + 1
        val endIndex = trimedDateLine.indexOf(";")

        if (startIndex >= endIndex) throw GoldenTimeException(GoldenTimeException.DATE_LINE_PARSE_ERROR)

        val date = trimedDateLine.substring(startIndex, endIndex).split("+").map {
            it.trim().replace("\"", "")
        }.reduce { acc, str ->
            acc + str
        }

        val format = SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.KOREA)
        val goldenTime = format.parse(date)!!

        return goldenTime
    }

    private fun leftTime(goldenDate: Date): String {
        val currDate = Date()

        val goldenTimeMills = goldenDate.getTime()
        val currTimeMills = currDate.getTime()
        val diffSeconds = (goldenTimeMills - currTimeMills) / 1000

        if (diffSeconds <= 0) throw GoldenTimeException(GoldenTimeException.OLD_GOLDEN_TIME)

        val leftTime = diffSeconds / (3600)
        val leftMinute = (diffSeconds - (leftTime * 3600)) / 60
        val leftSecond = (diffSeconds - (leftTime * 3600)) % 60

        return "${leftTime} 시간 ${leftMinute} 분 ${leftSecond} 초"
    }

    private fun dummyTime() = Date(0)

    private fun isGoldenTimeLineStart(inputLine: String) = inputLine.contains("goldentime off")

    private fun isGoldenTimeRightNow(inputLine: String) = inputLine.contains("goldentime on")

    private fun isScriptOver(inputLine: String) = inputLine.contains("</script>")

    private fun isDateLine(inputLine: String) = inputLine.contains("var date")

    private fun hasQuestionMark(inputLine: String) = inputLine.contains("??:??")

    private fun isStartWith(code: Int, startWith: Int) = code / 100 == startWith

    private fun cancelAlarm(requestCode: Int, intent: Intent) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            PendingIntent.getService(
                applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    private fun showErrorcode(errorCode: Int) {
        handler.post {
            Toast.makeText(
                applicationContext,
                "골든타임에러 ErrorCode = ${errorCode}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun notifyNotification(leftGoldenTime: String) {
        println("notify notification")
        notificationManager.notify(
            777,
            NotificationCompat.Builder(applicationContext, MainActivity.GOLDEN_TIME_CHANNEL).apply {
                setSmallIcon(R.mipmap.ic_launcher)
                setContentTitle("골든타임 알람")
                setContentText("${leftGoldenTime} 남음")
                setPriority(NotificationCompat.PRIORITY_DEFAULT)
                setDefaults(Notification.DEFAULT_VIBRATE)
            }.build()
        )
    }
}
