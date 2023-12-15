package com.example.monsterclockv1

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Implementation of App Widget functionality.
 */
class ClockWidget : AppWidgetProvider() {

    companion object {
        var MONSTER_ID = 1

        //private var timeTickReceiver: BroadcastReceiver? = null
        const val ACTION_UPDATE_CLOCK = "com.example.monsterclockv1.UPDATE_CLOCK"
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, MONSTER_ID)
        }
        Log.d("ClockWidget", "onReceive time tick called in ClockWidget")
    }

    private fun setNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ClockWidget::class.java).apply {
            action = Companion.ACTION_UPDATE_CLOCK
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val nextUpdateTime = calendar.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, nextUpdateTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC, nextUpdateTime, pendingIntent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        updateAllWidgets(context)
        setNextAlarm(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmPermission = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            if (!alarmPermission) {
                // 引導用戶到設置頁面以授予權限
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intent)
            }
        }
        // register time tick receiver that will update the widget contents every minute
        updateAllWidgets(context)
        //timeTickReceiver = object : BroadcastReceiver() {
        //    override fun onReceive(context: Context, intent: Intent) {
        //        // 每分鐘接收到廣播時觸發小工具更新
        //        updateAllWidgets(context)
        //    }
        //}
        //context.applicationContext.registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

        // in case app closed, use alarm manager to update widget every minute
        setNextAlarm(context)
        //val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        //val intent = Intent(context, ClockWidget::class.java).apply {
        //    action = ACTION_UPDATE_CLOCK
        //}
        //val interval = 60000L  // 1 minute
        //val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //    PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        //} else {
        //    PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        //}
        //alarmManager.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis(), interval, pendingIntent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 取消註冊接收器
        //if (timeTickReceiver != null) {
        //    context.unregisterReceiver(timeTickReceiver)
        //    timeTickReceiver = null
        //}
    }


    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        println("check intent.action = ${intent.action}")
        if (intent.action == MainActivity.ACTION_CHANGE_MONSTER) {
            MONSTER_ID = intent.getIntExtra(MainActivity.CHANGE_MONSTER_ID, R.drawable.icons8_number_48)
            Log.d("ClockWidget", "Received ACTION_CHANGE_MONSTER, updating widgets.")
            updateAllWidgets(context)
            Log.d("ClockWidget", "Done with updateAllWidgets")
        } else if (intent.action == Companion.ACTION_UPDATE_CLOCK) {
            updateAllWidgets(context)
            setNextAlarm(context)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    monsterId: Int = ClockWidget.MONSTER_ID
) {
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.default_clock_style)

    // Set the images for each digit based on the current time
    val currentTime = Calendar.getInstance()

    println("currentTime.get(Calendar.HOUR_OF_DAY) = ${currentTime.get(Calendar.HOUR_OF_DAY)}")
    println("digit1 = ${currentTime.get(Calendar.HOUR_OF_DAY) / 10}")
    println("digit2 = ${currentTime.get(Calendar.HOUR_OF_DAY) % 10}")

    setDigitImage(views, R.id.hour_first_digit, currentTime.get(Calendar.HOUR_OF_DAY) / 10, monsterId)
    setDigitImage(views, R.id.hour_second_digit, currentTime.get(Calendar.HOUR_OF_DAY) % 10, monsterId)
    setDigitImage(views, R.id.minute_first_digit, currentTime.get(Calendar.MINUTE) / 10, monsterId)
    setDigitImage(views, R.id.minute_second_digit, currentTime.get(Calendar.MINUTE) % 10, monsterId)
    setDigitImage(views, R.id.colon, 999, monsterId)
    // Repeat for minutes and seconds

    // Intent to launch Alarm Clock on click
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.my_default_clock_style, pendingIntent)


    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
//
//internal fun updateAppWidget(
//    context: Context,
//    appWidgetManager: AppWidgetManager,
//    appWidgetId: Int,
//    monsterId: Int = ClockWidget.MONSTER_ID
//) {
//    // Construct the RemoteViews object
//    val views = RemoteViews(context.packageName, R.layout.experimental_clock_widget)
//
//    // Set the images for each digit based on the current time
//    val currentTime = Calendar.getInstance()
//
//    println("currentTime.get(Calendar.HOUR_OF_DAY) = ${currentTime.get(Calendar.HOUR_OF_DAY)}")
//    println("digit1 = ${currentTime.get(Calendar.HOUR_OF_DAY) / 10}")
//    println("digit2 = ${currentTime.get(Calendar.HOUR_OF_DAY) % 10}")
//
//    // set text as clock hh:mm with customized font
//    views.setTextViewText(R.id.expClockTextView, "${currentTime.get(Calendar.HOUR_OF_DAY)}:${currentTime.get(Calendar.MINUTE)}")
//    // set text font
//
//    //setDigitImage(views, R.id.hour_first_digit, currentTime.get(Calendar.HOUR_OF_DAY) / 10, monsterId)
//    //setDigitImage(views, R.id.hour_second_digit, currentTime.get(Calendar.HOUR_OF_DAY) % 10, monsterId)
//    //setDigitImage(views, R.id.minute_first_digit, currentTime.get(Calendar.MINUTE) / 10, monsterId)
//    //setDigitImage(views, R.id.minute_second_digit, currentTime.get(Calendar.MINUTE) % 10, monsterId)
//    // Repeat for minutes and seconds
//
//    // Intent to launch Alarm Clock on click
//    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
//    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
//    views.setOnClickPendingIntent(R.id.expClockTextView, pendingIntent)
//
//
//    // Instruct the widget manager to update the widget
//    appWidgetManager.updateAppWidget(appWidgetId, views)
//}


internal fun setDigitImage(views: RemoteViews, viewId: Int, digit: Int, monsterId: Int) {
    val monsterMap = MonsterToClockDrawables.getObjectsById(monsterId)
    val digitResId = monsterMap?.get(digit)
    if (digitResId != null) {
        views.setImageViewResource(viewId, digitResId)
    }
}