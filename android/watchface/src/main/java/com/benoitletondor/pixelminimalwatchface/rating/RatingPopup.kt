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
package com.benoitletondor.pixelminimalwatchface.rating

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.widget.ConfirmationOverlay
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.helper.await
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Rating popup that ask user for feedback and redirect them to the PlayStore
 *
 * @author Benoit LETONDOR
 */
class RatingPopup(private val activity: AppCompatActivity) {

    /**
     * Show the rating popup to the user
     */
    fun show(finishListener: () -> Unit) {
        val dialog = buildStep1(finishListener)
        dialog.show()
    }

    /**
     * Build the first step of rating asking the user what he thinks of the app
     *
     * @return A ready to be shown [AlertDialog]
     */
    private fun buildStep1(finishListener: () -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.rating_popup_question_title)
            .setMessage(R.string.rating_popup_question_message)
            .setNegativeButton(R.string.rating_popup_question_cta_negative) { _, _ ->
                buildNegativeStep(finishListener).show()
            }
            .setPositiveButton(R.string.rating_popup_question_cta_positive) { _, _ ->
                buildPositiveStep(finishListener).show()
            }
            .setOnCancelListener {
                finishListener()
            }

        return builder.create()
    }

    /**
     * Build the step to shown when the user said he doesn't like the app
     *
     * @return A ready to be shown [AlertDialog]
     */
    private fun buildNegativeStep(finishListener: () -> Unit): AlertDialog {
        return AlertDialog.Builder(activity)
            .setTitle(R.string.rating_popup_negative_title)
            .setMessage(R.string.rating_popup_negative_message)
            .setNegativeButton(R.string.rating_popup_negative_cta_negative) { _, _ ->
                finishListener()
            }
            .setPositiveButton(R.string.rating_popup_negative_cta_positive) { _, _ ->
                val mail = activity.resources.getString(R.string.rating_feedback_email)
                val subject = activity.resources.getString(R.string.rating_feedback_send_subject)
                val body = activity.resources.getString(R.string.rating_feedback_send_text)

                val sendIntent = Intent()
                sendIntent.action = Intent.ACTION_VIEW
                sendIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                sendIntent.data = Uri.parse("mailto:$mail?subject=$subject&body=$body")
                sendIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(mail))
                sendIntent.putExtra(Intent.EXTRA_TEXT, body)
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject)

                activity.lifecycleScope.launch {
                    val phoneNodes = Wearable.getNodeClient(activity).connectedNodes.await()
                    val phoneNode = phoneNodes.firstOrNull { it.isNearby }
                    val phoneNodeId = phoneNode?.id

                    if (phoneNodeId != null) {
                        try {
                            RemoteActivityHelper(activity, Dispatchers.IO.asExecutor()).startRemoteActivity(
                                sendIntent,
                                phoneNodeId,
                            ).await()

                            ConfirmationOverlay()
                                .setOnAnimationFinishedListener {
                                    finishListener()
                                }
                                .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                .setDuration(3000)
                                .setMessage(activity.getString(R.string.open_phone_url_android_device) as CharSequence)
                                .showOn(activity)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e

                            Toast.makeText(activity, activity.resources.getString(R.string.rating_feedback_send_error), Toast.LENGTH_SHORT).show()
                            finishListener()
                        }
                    } else {
                        Toast.makeText(activity, activity.resources.getString(R.string.rating_feedback_send_error), Toast.LENGTH_SHORT).show()
                        finishListener()
                    }
                }
            }
            .setOnCancelListener {
                finishListener()
            }
            .create()
    }

    /**
     * Build the step to shown when the user said he likes the app
     *
     * @return A ready to be shown [AlertDialog]
     */
    private fun buildPositiveStep(finishListener: () -> Unit): AlertDialog {
        return AlertDialog.Builder(activity)
            .setTitle(R.string.rating_popup_positive_title)
            .setMessage(R.string.rating_popup_positive_message)
            .setNegativeButton(R.string.rating_popup_positive_cta_negative) { _, _ ->
                finishListener()
            }
            .setPositiveButton(R.string.rating_popup_positive_cta_positive) { _, _ ->
                val appPackageName = activity.packageName

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))

                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"))

                    activity.startActivity(intent)
                }

                finishListener()
            }
            .setOnCancelListener {
                finishListener()
            }
            .create()
    }
}