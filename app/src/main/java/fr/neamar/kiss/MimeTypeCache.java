package fr.neamar.kiss;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.provider.ContactsContract;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.neamar.kiss.utils.MimeTypeUtils;

public class MimeTypeCache {

    private static final String CONTACTS_DATA_KIND = "ContactsDataKind";
    private static final String CONTACT_ATTR_MIME_TYPE = "mimeType";
    private static final String CONTACT_ATTR_DETAIL_COLUMN = "detailColumn";

    private static final String[] METADATA_CONTACTS_NAMES = new String[]{
            "android.provider.ALTERNATE_CONTACTS_STRUCTURE",
            "android.provider.CONTACTS_STRUCTURE"
    };

    // Cached componentName
    private final Map<String, ComponentName> componentNames;
    // Cached label
    private final Map<String, String> labels;
    // Cached detail columns
    private Map<String, String> detailColumns;


    public MimeTypeCache() {
        this.componentNames = new HashMap<>();
        this.labels = new HashMap<>();
        this.detailColumns = null;
    }

    public void clearCache() {
        this.componentNames.clear();
        this.labels.clear();
        this.detailColumns = null;
    }

    /**
     * @param context
     * @param mimeType
     * @return label for best matching app by mimetype
     */
    public String getLabel(Context context, String mimeType) {
        if (labels.containsKey(mimeType)) {
            return labels.get(mimeType);
        }

        String label = null;
        ResolveInfo resolveInfo = getBestResolve(context, mimeType);
        if (resolveInfo != null) {
            label = String.valueOf(resolveInfo.loadLabel(context.getPackageManager()));
        }
        labels.put(mimeType, label);
        return label;
    }

    public ComponentName getComponentName(Context context, String mimeType) {
        if (componentNames.containsKey(mimeType)) {
            return componentNames.get(mimeType);
        }

        ComponentName componentName = null;
        ResolveInfo resolveInfo = getBestResolve(context, mimeType);
        if (resolveInfo != null) {
            componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }
        this.componentNames.put(mimeType, componentName);

        return componentName;
    }

    /**
     * @param context
     * @return all mime types and related data columns from contact sync adapters
     */
    public Map<String, String> fetchDetailColumns(Context context) {
        if (detailColumns == null) {
            long startDetail = System.nanoTime();

            detailColumns = new HashMap<>();

            final Set<String> contactSyncableTypes = new HashSet<>();

            SyncAdapterType[] syncAdapterTypes = ContentResolver.getSyncAdapterTypes();
            for (SyncAdapterType type : syncAdapterTypes) {
                if (type.authority.equals(ContactsContract.AUTHORITY)) {
                    contactSyncableTypes.add(type.accountType);
                }
            }

            AuthenticatorDescription[] authenticatorDescriptions = ((AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE)).getAuthenticatorTypes();
            for (AuthenticatorDescription auth : authenticatorDescriptions) {
                if (contactSyncableTypes.contains(auth.type)) {
                    XmlResourceParser parser = loadContactsXml(context, auth.packageName);
                    if (parser != null) {
                        try {
                            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                                if (CONTACTS_DATA_KIND.equals(parser.getName())) {
                                    String foundMimeType = null;
                                    String foundDetailColumn = null;
                                    int attributeCount = parser.getAttributeCount();
                                    for (int i = 0; i < attributeCount; i++) {
                                        String attr = parser.getAttributeName(i);
                                        String value = parser.getAttributeValue(i);
                                        if (CONTACT_ATTR_MIME_TYPE.equals(attr)) {
                                            foundMimeType = value;
                                        } else if (CONTACT_ATTR_DETAIL_COLUMN.equals(attr)) {
                                            foundDetailColumn = value;
                                        }
                                    }
                                    if (foundMimeType != null) {
                                        detailColumns.put(foundMimeType, foundDetailColumn);
                                    }
                                }
                            }
                        } catch (IOException | XmlPullParserException ignored) {
                        }
                    }
                }
            }

            // Add additional data columns for known mime types
            detailColumns.put(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.ADDRESS);
            detailColumns.put(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.NUMBER);

            long endDetail = System.nanoTime();
            Log.i("time", (endDetail - startDetail) / 1000000 + " milliseconds to fetch detail data columns");
        }
        return detailColumns;
    }

    /**
     * Loads contact description from other sync providers, search for ContactsAccountType or ContactsSource
     * detailed description can be found here https://developer.android.com/guide/topics/providers/contacts-provider
     *
     * @param context
     * @param packageName
     * @return XmlResourceParser for contacts.xml, null if nothing found
     */
    @SuppressLint("WrongConstant")
    public XmlResourceParser loadContactsXml(Context context, String packageName) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent("android.content.SyncAdapter").setPackage(packageName);
        final List<ResolveInfo> intentServices = pm.queryIntentServices(intent,
                PackageManager.GET_META_DATA | PackageManager.GET_SERVICES);

        if (intentServices != null) {
            for (final ResolveInfo resolveInfo : intentServices) {
                final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
                if (serviceInfo == null) {
                    continue;
                }
                for (String metadataName : METADATA_CONTACTS_NAMES) {
                    final XmlResourceParser parser = serviceInfo.loadXmlMetaData(
                            pm, metadataName);
                    if (parser != null) {
                        return parser;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param context
     * @param mimeType
     * @return related detail data column for mime type
     */
    public String getDetailColumn(Context context, String mimeType) {
        Map<String, String> detailColumns = fetchDetailColumns(context);
        return detailColumns.get(mimeType);
    }

    /**
     * Search best matching app for given mimeType.
     *
     * @param context
     * @param mimeType
     * @return ResolveInfo for best matching app
     */
    private static ResolveInfo getBestResolve(Context context, String mimeType) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent = MimeTypeUtils.getIntentByMimeType(mimeType, -1, "");
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
     * Generates unique labels for given mime types, appends mimeType itself if an app supports multiple mime types
     *
     * @param context
     * @param mimeTypes
     * @return labels for given mime types
     */
    public Map<String, String> getUniqueLabels(Context context, Set<String> mimeTypes) {
        Map<String, String> uniqueLabels = new HashMap<>(mimeTypes.size());

        // get labels for mime types
        Map<String, Set<String>> mappedMimeTypes = new HashMap<>();
        for (String mimeType : mimeTypes) {
            String label = getLabel(context, mimeType);
            Set<String> mimeTypesPerLabel = mappedMimeTypes.get(label);
            if (mimeTypesPerLabel == null) {
                mimeTypesPerLabel = new HashSet<>();
                mappedMimeTypes.put(label, mimeTypesPerLabel);
            }
            mimeTypesPerLabel.add(mimeType);
        }
        // check supported mime types and make labels unique
        for (String mimeType : mimeTypes) {
            String label = getLabel(context, mimeType);
            if (mappedMimeTypes.get(label).size() > 1) {
                label += " (" + MimeTypeUtils.getShortMimeType(mimeType) + ")";
            }
            uniqueLabels.put(mimeType, label);
        }

        return uniqueLabels;
    }
}
