/*
 *   Copyright 2022 Benoit LETONDOR
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.benoitletondor.pixelminimalwatchface.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.wallpaper.WallpaperService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.benoitletondor.pixelminimalwatchface.MISC_NOTIFICATION_CHANNEL_ID
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.rating.FeedbackActivity

fun Context.showRatingNotification() {
    // Create notification channel if needed
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val mChannel = NotificationChannel(MISC_NOTIFICATION_CHANNEL_ID, getString(R.string.misc_notification_channel_name), importance)
    mChannel.description = getString(R.string.misc_notification_channel_description)

    val notificationManager = getSystemService(WallpaperService.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(mChannel)

    val activityIntent = Intent(this, FeedbackActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        activityIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
    )

    val notification = NotificationCompat.Builder(this, MISC_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.rating_notification_title))
        .setContentText(getString(R.string.rating_notification_message))
        .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.rating_notification_message)))
        .addAction(NotificationCompat.Action(R.drawable.ic_feedback, getString(R.string.rating_notification_cta), pendingIntent))
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(this).notify(193828, notification)
}