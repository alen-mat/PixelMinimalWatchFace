package com.benoitletondor.pixelminimalwatchface.helper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.widget.ConfirmationOverlay
import com.benoitletondor.pixelminimalwatchface.BuildConfig
import com.benoitletondor.pixelminimalwatchface.R
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.guava.await

suspend fun Activity.openCompanionAppOnPhone(
    deeplinkPath: String,
    capabilityClient: CapabilityClient = Wearable.getCapabilityClient(this),
    remoteActivityHelper: RemoteActivityHelper = RemoteActivityHelper(this, Dispatchers.IO.asExecutor()),
): Boolean {
    if ( PhoneTypeHelper.getPhoneDeviceType(applicationContext) == PhoneTypeHelper.DEVICE_TYPE_ANDROID ) {
        val intentAndroid = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("pixelminimalwatchface://$deeplinkPath"))
            .setPackage(BuildConfig.APPLICATION_ID)

        val companionNode = capabilityClient.findBestCompanionNode()
        if (companionNode != null) {
            try {
                remoteActivityHelper.startRemoteActivity(
                    intentAndroid,
                    companionNode.id,
                ).await()

                ConfirmationOverlay()
                    .setOnAnimationFinishedListener {
                        finish()
                    }
                    .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                    .setDuration(3000)
                    .setMessage(getString(R.string.open_phone_url_android_device) as CharSequence)
                    .showOn(this)

                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return false
            }
        } else {
            return false
        }
    } else {
        return false
    }
}