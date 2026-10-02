package fr.neamar.kiss.broadcast;

import static fr.neamar.kiss.dataprovider.simpleprovider.PhoneProvider.PHONE_SCHEME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.dataprovider.simpleprovider.PhoneProvider;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.utils.Log;
import fr.neamar.kiss.utils.PackageManagerUtils;

public class IncomingCallHandler extends BroadcastReceiver {

    private static final String TAG = IncomingCallHandler.class.getSimpleName();

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Only handle calls received
        if (!"android.intent.action.PHONE_STATE".equals(intent.getAction())) {
            return;
        }

        try {
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE))) {
                String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                if (!TextUtils.isEmpty(phoneNumber)) {
                    DataHandler dataHandler = KissApplication.getApplication(context).getDataHandler();
                    PhoneProvider phoneProvider = dataHandler.getPhoneProvider();
                    if (phoneProvider != null) {
                        Pojo pojo = phoneProvider.findById(PHONE_SCHEME + phoneNumber);
                        if (pojo != null) {
                            dataHandler.addToHistory(pojo.getHistoryId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Phone Receive Error", e);
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PackageManagerUtils.enableComponent(context, IncomingCallHandler.class, false);
        } else {
            PackageManagerUtils.enableComponent(context, IncomingCallHandler.class, enabled);
        }

    }
}
