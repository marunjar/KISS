package fr.neamar.kiss.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

public class ComponentUtils {

    /**
     * Search best matching app for given intent.
     *
     * @param context context
     * @param intent  intent
     * @return ResolveInfo for best matching app by intent
     */
    public static ResolveInfo getBestResolve(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> matches = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        final int size = matches.size();
        if (size == 0) {
            return null;
        } else if (size == 1) {
            return matches.get(0);
        }

        // Try finding preferred activity, otherwise detect disambig
        final ResolveInfo foundResolve = packageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        final boolean foundDisambig = (foundResolve.match &
                IntentFilter.MATCH_CATEGORY_MASK) == 0;

        if (!foundDisambig) {
            // Found concrete match, so return directly
            return foundResolve;
        }

        // Accept first system app
        ResolveInfo firstSystem = null;
        for (ResolveInfo info : matches) {
            final boolean isSystem = (info.activityInfo.applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0;

            if (isSystem && firstSystem == null) firstSystem = info;
        }

        // Return first system found, otherwise first from list
        return firstSystem != null ? firstSystem : matches.get(0);
    }

    /**
     * @param context context
     * @param intent  intent
     * @return label of app by intent
     */
    public static String getLabel(Context context, Intent intent) {
        ResolveInfo resolveInfo = ComponentUtils.getBestResolve(context, intent);
        if (resolveInfo != null) {
            return String.valueOf(resolveInfo.loadLabel(context.getPackageManager()));
        }
        return null;
    }

    /**
     * @param context context
     * @param intent  intent
     * @return component name of app by intent
     */
    public static ComponentName getComponentName(Context context, Intent intent) {
        ResolveInfo resolveInfo = ComponentUtils.getBestResolve(context, intent);
        if (resolveInfo != null) {
            return new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }
        return null;
    }

    /**
     * @param context       context
     * @param componentName componentName
     * @return launching component name for any component
     */
    public static ComponentName getLaunchingComponent(Context context, ComponentName componentName) {
        if (componentName == null) {
            return null;
        }
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(componentName.getPackageName());
        if (launchIntent != null) {
            return launchIntent.getComponent();
        }
        return componentName;
    }

}
