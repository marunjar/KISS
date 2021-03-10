package fr.neamar.kiss.utils;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class MimeTypeUtils {

    // Cached componentName
    private static final Map<String, ComponentName> componentNameCache = new HashMap<>();
    // Cached label
    private static final Map<String, String> labelCache = new HashMap<>();
    // Cached detail columns
    private static Map<String, String> detailColumnCache = null;

    private static final String CONTACTS_DATA_KIND = "ContactsDataKind";
    private static final String CONTACT_ATTR_MIME_TYPE = "mimeType";
    private static final String CONTACT_ATTR_DETAIL_COLUMN = "detailColumn";

    private static final String[] METADATA_CONTACTS_NAMES = new String[]{
            "android.provider.ALTERNATE_CONTACTS_STRUCTURE",
            "android.provider.CONTACTS_STRUCTURE"
    };

    // Known android mime types that are not supported by KISS
    private static final Set<String> UNSUPPORTED_MIME_TYPES = new HashSet<>(Arrays.asList(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
    ));

    // Known android mime types that are supported by KISS
    private static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<>(Arrays.asList(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
    ));

    private MimeTypeUtils() {
    }

    /**
     * @param context
     * @return a list of all possible mime types from existing contacts
     */
    public static Set<String> getPossibleMimeTypes(Context context) {
        if (!Permission.checkPermission(context, Permission.PERMISSION_READ_CONTACTS)) {
            return emptySet();
        }

        Set<String> mimeTypes = new HashSet<>();

        Cursor cursor = context.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Data.MIMETYPE}, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                int mimeTypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE);
                while (cursor.moveToNext()) {
                    String mimeType = cursor.getString(mimeTypeIndex);
                    if (isPossibleMimeType(context, mimeType)) {
                        mimeTypes.add(mimeType);
                    }
                }
            }
        }
        cursor.close();

        return mimeTypes;
    }

    private static boolean isPossibleMimeType(Context context, String mimeType) {
        if (mimeType == null) {
            return false;
        }
        if (UNSUPPORTED_MIME_TYPES.contains(mimeType)) {
            return false;
        }
        if (SUPPORTED_MIME_TYPES.contains(mimeType)) {
            return true;
        }
        // check if intent for custom mime type is registered
        Intent intent = getRegisteredIntentByMimeType(context, mimeType, -1);
        return intent != null;
    }

    public static Set<String> getAllowedMimeTypes(Context context) {
        Set<String> mimeTypes = getPossibleMimeTypes(context);
        // TODO: load settings and remove mime types that shouldn't be shown
        return mimeTypes;
    }

    /**
     * Create a new intent to view given row of contact data.
     *
     * @param mimeType mimetype of contact data row
     * @param id       id of contact data row
     * @return intent to view contact by mime type and id, null if no activity is registered for intent
     */
    public static Intent getRegisteredIntentByMimeType(Context context, String mimeType, long id) {
        final Intent intent = getIntentByMimeType(mimeType, id);

        if (isIntentRegistered(context, intent)) {
            return intent;
        } else {
            return null;
        }
    }

    /**
     * create a new intent to view given row of contact data
     *
     * @param mimeType mime type of contact data row
     * @param id       id of contact data row
     * @return intent to view contact by mime type and id
     */
    private static Intent getIntentByMimeType(String mimeType, long id) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        final Uri uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
        intent.setDataAndType(uri, mimeType);
        return intent;
    }

    /**
     * @param context
     * @param intent
     * @return true if any activity is registered for given intent
     */
    private static boolean isIntentRegistered(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> receiverList = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receiverList.size() > 0;
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
        final Intent intent = getIntentByMimeType(mimeType, -1);
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

    public static ComponentName getComponentName(Context context, String mimeType) {
        if (componentNameCache.containsKey(mimeType)) {
            return componentNameCache.get(mimeType);
        }

        ComponentName componentName = null;
        ResolveInfo resolveInfo = getBestResolve(context, mimeType);
        if (resolveInfo != null) {
            componentName = new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }
        componentNameCache.put(mimeType, componentName);

        return componentName;
    }

    /**
     * @param context
     * @return all mime types and related data columns from contact sync adapters
     */
    public static Map<String, String> fetchDetailColumns(Context context) {
        if (detailColumnCache == null) {
            long startDetail = System.nanoTime();

            detailColumnCache = new HashMap<>();

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
                                        detailColumnCache.put(foundMimeType, foundDetailColumn);
                                    }
                                }
                            }
                        } catch (IOException | XmlPullParserException ignored) {
                        }
                    }
                }
            }

            // Add additional data columns for known mime types
            detailColumnCache.put(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.ADDRESS);
            detailColumnCache.put(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.NUMBER);

            long endDetail = System.nanoTime();
            Log.i("time", (endDetail - startDetail) / 1000000 + " milliseconds to fetch detail data columns");
        }
        return detailColumnCache;
    }

    /**
     * @param context
     * @param mimeType
     * @return related data column for mime type
     */
    public static String getDetailColumn(Context context, String mimeType) {
        Map<String, String> detailColumns = fetchDetailColumns(context);
        return detailColumns.get(mimeType);
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
    public static XmlResourceParser loadContactsXml(Context context, String packageName) {
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
     * @return label for best matching app by mimetype
     */
    public static String getLabel(Context context, String mimeType) {
        if (labelCache.containsKey(mimeType)) {
            return labelCache.get(mimeType);
        }

        String label = null;
        ResolveInfo resolveInfo = getBestResolve(context, mimeType);
        if (resolveInfo != null) {
            label = String.valueOf(resolveInfo.loadLabel(context.getPackageManager()));
        }
        labelCache.put(mimeType, label);
        return label;
    }

}
