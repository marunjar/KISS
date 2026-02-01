package fr.neamar.kiss.preference;

import android.content.Context;
import android.content.DialogInterface;
import androidx.preference.DialogPreference;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

public class ResetExcludedFromHistoryAppsPreference extends DialogShowingPreference {

    public ResetExcludedFromHistoryAppsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

//    @Override
//    public void onClick(DialogInterface dialog, int which) {
//        super.onClick(dialog, which);
//        if (which == DialogInterface.BUTTON_POSITIVE) {
//            PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
//                    .putStringSet("excluded-apps-from-history", null).apply();
//            KissApplication.getApplication(getContext()).getDataHandler().reloadApps(); // reload because it's cached in AppPojo#excludedFromHistory
//            Toast.makeText(getContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
//        }
//    }
}
