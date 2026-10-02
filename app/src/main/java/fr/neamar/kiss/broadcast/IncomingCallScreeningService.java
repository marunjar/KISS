package fr.neamar.kiss.broadcast;

import static fr.neamar.kiss.dataprovider.simpleprovider.PhoneProvider.PHONE_SCHEME;

import android.content.SharedPreferences;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.dataprovider.simpleprovider.PhoneProvider;
import fr.neamar.kiss.pojo.Pojo;

@RequiresApi(api = Build.VERSION_CODES.N)
public class IncomingCallScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        respondToCall(callDetails, new CallResponse.Builder().build());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("enable-phone-history", false) && callDetails.getHandle() != null) {
            String phoneNumber = callDetails.getHandle().getSchemeSpecificPart();
            if (!TextUtils.isEmpty(phoneNumber)) {
                DataHandler dataHandler = KissApplication.getApplication(this).getDataHandler();
                PhoneProvider phoneProvider = dataHandler.getPhoneProvider();
                if (phoneProvider != null) {
                    Pojo pojo = phoneProvider.findById(PHONE_SCHEME + phoneNumber);
                    if (pojo != null) {
                        dataHandler.addToHistory(pojo.getHistoryId());
                    }
                }
            }
        }
    }
}
