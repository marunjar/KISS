package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;

// https://code.google.com/p/android/issues/detail?id=26194
// Can be removed once we drop support for KitKat
// Forced 10 max lines in summary (different Android versions have different values)
public class SwitchPreference extends androidx.preference.SwitchPreference {

    public SwitchPreference(Context context) {
        this(context, null);
    }

    public SwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
    }

    public SwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void syncSummaryView(@NonNull PreferenceViewHolder holder) {
        super.syncSummaryView(holder);

        View summary = holder.findViewById(android.R.id.summary);
        if (summary instanceof TextView)
            ((TextView) summary).setMaxLines(10);
    }
}
