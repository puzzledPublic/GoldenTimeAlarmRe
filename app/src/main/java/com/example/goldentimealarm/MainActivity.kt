package com.example.goldentimealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    companion object {
        val REQUEST_CODE = 101
        val GOLDEN_TIME_CHANNEL = "GOLDEN_TIME_CHANNEL"
    }

    private lateinit var alramManager: AlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alramManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alarmIntent = Intent(this, AlarmService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        findViewById<Button>(R.id.start_btn).setOnClickListener {
            startAlram(pendingIntent)
        }

        findViewById<Button>(R.id.stop_btn).setOnClickListener {
            stopAlram(pendingIntent)
        }
    }

    private fun startAlram(pendingIntent: PendingIntent) {
        Toast.makeText(applicationContext, R.string.start_golden_time_start, Toast.LENGTH_SHORT).show()
        alramManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,   //현재부터 얼마나(mills) 지난 후 trigger할 것인지.
            1000,
            AlarmManager.INTERVAL_HALF_HOUR,
            pendingIntent
        )
    }

    private fun stopAlram(pendingIntent: PendingIntent) {
        Toast.makeText(applicationContext, R.string.stop_golden_time_check, Toast.LENGTH_SHORT).show()
        alramManager.cancel(pendingIntent)
    }

}
