package fr.neamar.kiss;

import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.neamar.kiss.broadcast.IncomingCallHandler;
import fr.neamar.kiss.dataprovider.simpleprovider.SearchProvider;
import fr.neamar.kiss.dataprovider.simpleprovider.TagsProvider;
import fr.neamar.kiss.forwarder.ExperienceTweaks;
import fr.neamar.kiss.forwarder.InterfaceTweaks;
import fr.neamar.kiss.forwarder.TagsMenu;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.NameComparator;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.TagDummyPojo;
import fr.neamar.kiss.preference.ColorPreference;
import fr.neamar.kiss.preference.ColorPreferenceDialogFragment;
import fr.neamar.kiss.preference.DefaultLauncherPreference;
import fr.neamar.kiss.preference.DialogShowingPreference;
import fr.neamar.kiss.preference.DialogShowingPreferenceDialogFragment;
import fr.neamar.kiss.preference.ExcludePreferenceScreen;
import fr.neamar.kiss.preference.ExportSettingsPreference;
import fr.neamar.kiss.preference.ImportSettingsPreference;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.utils.DrawableUtils;
import fr.neamar.kiss.utils.MimeTypeUtils;
import fr.neamar.kiss.utils.Permission;
import fr.neamar.kiss.utils.ShortcutUtil;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    private static final String TAG = SettingsFragment.class.getSimpleName();
    private static final int REQUEST_CALL_SCREENING_APP = 1;
    private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";

    private static final List<String> PREF_LISTS_WITH_DEPENDENCY = Arrays.asList(
            "gesture-up", "gesture-down",
            "gesture-left", "gesture-right",
            "gesture-long-press"
    );
    private static Pair<CharSequence[], CharSequence[]> itemToRunListContent = null;

    private SharedPreferences prefs;

    private Permission permissionManager;

    /**
     * Get tags that should be in the favorites bar
     *
     * @return what we find in DataHandler
     */
    @NonNull
    private Set<String> getFavTags() {
        List<Pojo> favoritesPojo = getDataHandler().getFavorites();
        Set<String> set = new HashSet<>();
        for (Pojo pojo : favoritesPojo) {
            if (pojo instanceof TagDummyPojo)
                set.add(pojo.getName());
        }
        return set;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        InterfaceTweaks.applySettingsTheme(getActivity(), prefs);
        InterfaceTweaks.applySystemBarInsets(getActivity().getWindow().getDecorView());

        // Lock launcher into portrait mode
        // Do it here to make the transition as smooth as possible
        ExperienceTweaks.setRequestedOrientation(getActivity(), prefs);

        setPreferencesFromResource(R.xml.preferences, rootKey);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference("gestures-holder", "double-tap");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            removePreference("colors-section", "black-notification-icons");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            removePreference("advanced", "enable-notifications");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            removePreference("icons-section", DrawableUtils.KEY_THEMED_ICONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            removePreference("colors-section", "notification-bar-color");
        }
        if (!ShortcutUtil.canDeviceShowShortcuts()) {
            removePreference("exclude_apps_category", "reset-excluded-app-shortcuts");
            removePreference("search-providers", "enable-shortcuts");
            removePreference("search-providers", "reset-search-providers");
        }

        final ListPreference iconsPack = findPreference("icons-pack");
        if (iconsPack != null) {
            iconsPack.setEnabled(false);
        }

        Runnable runnable = () -> {
            SettingsFragment.this.fixSummaries();

            if (iconsPack != null) {
                SettingsFragment.this.setListPreferenceIconsPacksData(iconsPack);
                SettingsFragment.this.getActivity().runOnUiThread(() -> iconsPack.setEnabled(true));
            }

            SettingsFragment.this.setAdditionalContactsData();
            SettingsFragment.this.addCustomSearchProvidersPreferences(prefs);

            SettingsFragment.this.addHiddenTagsTogglesInformation(prefs);
            SettingsFragment.this.addTagsFavInformation();
        };

        // This is reaaally slow, and always need to run asynchronously
        Runnable alwaysAsync = () -> {
            // TODO: Note that there is a bug here with all of these settings pages:
            //  These settings pages load the list of AppPojos from DataHandler only once.
            //  This means that the data shown in these settings pages will be stale if the AppPojo
            //  data stored in DataHandler is changed by elsewhere in the app.
            //  You can easily reproduce this bug by:
            //  1. Open the 'apps excluded from KISS' page
            //  2. Change some values from their defaults
            //  3. Go back and use the 'reset apps excluded from KISS' button
            //  4. Open the 'apps excluded from KISS' page again. The data shown will be incorrect,
            //   as it won't have refreshed for the user having reset the list.
            //   This list will refresh if the user closes and re-opens KISS settings.
            SettingsFragment.this.addExcludedAppSettings();
            SettingsFragment.this.addExcludedFromHistoryAppSettings();
            SettingsFragment.this.addExcludedShortcutAppSettings();
        };

        reorderPreferencesWithDisplayDependency();

        if (savedInstanceState == null) {
            // Run asynchronously to open settings fast
            AsyncTask.execute(runnable);
            asyncInitItemToRunList();
        } else {
            // Run synchronously to ensure preferences can be restored from state
            runnable.run();
            synchronized (SettingsFragment.class) {
                if (itemToRunListContent == null)
                    itemToRunListContent = generateItemToRunListContent();

                for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY) {
                    updateItemToRunList(gesturePref);
                }
            }
        }
        AsyncTask.execute(alwaysAsync);

        permissionManager = new Permission(getActivity());
    }

    private void setAdditionalContactsData() {
        // get all supported mime types
        Set<String> supportedMimeTypes = MimeTypeUtils.getSupportedMimeTypes(getContext());

        // get all labels
        MimeTypeCache mimeTypeCache = KissApplication.getMimeTypeCache(getContext());
        Map<String, String> uniqueLabels = mimeTypeCache.getUniqueLabels(getContext(), supportedMimeTypes);

        // get entries and values for sorted mime types
        List<String> sortedMimeTypes = new ArrayList<>(supportedMimeTypes);
        Collections.sort(sortedMimeTypes);

        String[] mimeTypeEntries = new String[supportedMimeTypes.size()];
        String[] mimeTypeEntryValues = new String[supportedMimeTypes.size()];
        int pos = 0;
        for (String mimeType : sortedMimeTypes) {
            mimeTypeEntries[pos] = uniqueLabels.get(mimeType);
            mimeTypeEntryValues[pos] = mimeType;
            pos++;
        }

        MultiSelectListPreference multiPreference = findPreference("selected-contact-mime-types");
        if (multiPreference != null) {
            if (supportedMimeTypes.isEmpty()) {
                multiPreference.setEnabled(false);
            }
            multiPreference.setEntries(mimeTypeEntries);
            multiPreference.setEntryValues(mimeTypeEntryValues);
        }
    }

    /**
     * Because we use the order to insert preferences we need to have gaps in the original order
     */
    private void reorderPreferencesWithDisplayDependency() {
        // get groups that need gaps
        HashSet<PreferenceGroup> groups = new HashSet<>();
        for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY) {
            Preference pref = findPreference(gesturePref);
            if (pref != null) {
                groups.add(getParent(pref));
            }
        }
        // set new order numbers
        for (PreferenceGroup group : groups) {
            int count = group.getPreferenceCount();
            for (int idx = 0; idx < count; idx += 1) {
                Preference pref = group.getPreference(idx);
                pref.setOrder(idx * 10);
            }
        }
    }

    private Pair<CharSequence[], CharSequence[]> generateItemToRunListContent() {
        List<AppPojo> appPojoList = getDataHandler().getApplications();
        if (appPojoList == null)
            appPojoList = Collections.emptyList();

        // appPojoList is a copy of the original list; we can sort it in place
        Collections.sort(appPojoList, new NameComparator());

        // generate entry names and entry values
        final int appCount = appPojoList.size();
        CharSequence[] entries = new CharSequence[appCount];
        CharSequence[] entryValues = new CharSequence[appCount];
        for (int idx = 0; idx < appCount; idx++) {
            AppPojo appEntry = appPojoList.get(idx);
            entries[idx] = appEntry.getName();
            entryValues[idx] = appEntry.id;
        }
        return new Pair<>(entries, entryValues);
    }

    private void asyncInitItemToRunList() {
        final Runnable updateLists = () -> {
            for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY)
                updateItemToRunList(gesturePref);
        };
        if (itemToRunListContent == null) {
            AsyncTask.execute(() -> {
                Pair<CharSequence[], CharSequence[]> content = generateItemToRunListContent();
                synchronized (SettingsFragment.class) {
                    if (itemToRunListContent == null)
                        itemToRunListContent = content;
                }
                getActivity().runOnUiThread(updateLists);
            });
        } else {
            updateLists.run();
        }
    }

    private void updateItemToRunList(String key) {
        synchronized (SettingsFragment.class) {
            if (itemToRunListContent != null)
                updateListPrefDependency(key, prefs.getString(key, null), "launch-pojo", key + "-launch-id", itemToRunListContent);
        }
    }

    private void updateListPrefDependency(@NonNull String dependOnKey, @Nullable String dependOnValue, @NonNull String enableValue, @NonNull String listKey, @Nullable Pair<CharSequence[], CharSequence[]> listContent) {
        Preference prefEntryToRun = findPreference(listKey);

        if (prefEntryToRun == null && enableValue.equals(dependOnValue)) {
            prefEntryToRun = new ListPreference(getContext());
            prefEntryToRun.setKey(listKey);
            prefEntryToRun.setTitle(R.string.gesture_launch_pojo);

            Preference pref = findPreference(dependOnKey);
            // set the list pref under the depended preference
            prefEntryToRun.setOrder(pref.getOrder() + 1);

            getParent(pref).addPreference(prefEntryToRun);
        }

        if (prefEntryToRun instanceof ListPreference) {
            if (enableValue.equals(dependOnValue)) {
                if (listContent != null) {
                    CharSequence[] entries = listContent.first;
                    CharSequence[] entryValues = listContent.second;
                    ((ListPreference) prefEntryToRun).setEntries(entries);
                    ((ListPreference) prefEntryToRun).setEntryValues(entryValues);
                }
            } else {
                getParent(prefEntryToRun).removePreference(prefEntryToRun);
            }
        } else if (prefEntryToRun != null) {
            throw new IllegalStateException("Preference `" + listKey + "` is " + prefEntryToRun.getClass() + "; should be " + ListPreference.class);
        }
    }

    private void removePreference(String parentKey, String key) {
        PreferenceGroup p = findPreference(parentKey);
        if (p != null) {
            Preference c = p.findPreference(key);
            if (c != null) {
                p.removePreference(c);
            } else {
                Log.d(TAG, "Preference to remove not found: " + parentKey + "/" + key);
            }
        }
    }

    private PreferenceGroup getParent(Preference preference) {
        return getParent(getPreferenceScreen(), preference);
    }

    private static PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
        for (int i = 0; i < root.getPreferenceCount(); i++) {
            Preference p = root.getPreference(i);
            if (p == preference)
                return root;
            if (p instanceof PreferenceGroup) {
                PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
                if (parent != null)
                    return parent;
            }
        }
        return null;
    }

    private void addExcludedAppSettings() {
        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreen.getInstance(
                this,
                R.string.ui_excluded_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreen.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcluded(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcluded(app);
                    }
                },
                AppPojo::isExcluded
        );

        PreferenceGroup category = findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addExcludedFromHistoryAppSettings() {
        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreen.getInstance(
                this,
                R.string.ui_excluded_from_history_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreen.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcludedFromHistory(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcludedFromHistory(app);
                    }
                },
                AppPojo::isExcludedFromHistory
        );

        PreferenceGroup category = findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addExcludedShortcutAppSettings() {
        if (!ShortcutUtil.canDeviceShowShortcuts()) {
            return;
        }

        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreen.getInstance(
                this,
                R.string.ui_excluded_from_shortcuts_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreen.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcludedShortcutApps(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcludedShortcutApps(app);
                    }
                },
                AppPojo::isExcludedShortcuts
        );

        PreferenceGroup category = findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addCustomSearchProvidersPreferences(SharedPreferences prefs) {
        if (prefs.getStringSet("selected-search-provider-names", null) == null) {
            // If null, it means this setting has never been accessed before
            // In this case, null != [] ([] happens when the user manually unselected every single option)
            // So, when null, we know it's the first time opening this setting and we can write the default value.
            // note: other preferences are initialized automatically in MainActivity.onCreate() from the preferences XML,
            // but this preference isn't defined in the XML so can't be initialized that easily.
            prefs.edit().putStringSet("selected-search-provider-names", SearchProvider.getSelectedSearchProviders(prefs)).apply();
        }

        removeSearchProviderSelect();
        removeSearchProviderDelete();
        removeSearchProviderDefault();
        addCustomSearchProvidersSelect(prefs);
        addCustomSearchProvidersDelete(prefs);
        addDefaultSearchProvider(prefs);
    }

    private void removeSearchProviderSelect() {
        removePreference("web-providers", "selected-search-provider-names");
    }

    private void removeSearchProviderDelete() {
        removePreference("web-providers", "deleting-search-providers-names");
    }

    private void removeSearchProviderDefault() {
        removePreference("web-providers", "default-search-provider");
    }

    private void addCustomSearchProvidersSelect(SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = createCustomSearchProvidersPreference("selected-search-provider-names", R.string.search_providers_title, 10);
        PreferenceGroup category = findPreference("web-providers");
        if (category != null) {
            category.addPreference(multiPreference);
        }
    }

    private void addCustomSearchProvidersDelete(final SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = createCustomSearchProvidersPreference("deleting-search-providers-names", R.string.search_providers_delete, 20);
        multiPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> searchProvidersToDelete = (Set<String>) newValue;

                Set<String> availableSearchProviders = SearchProvider.getAvailableSearchProviders(getContext(), prefs);
                Set<String> updatedProviders = SearchProvider.getAvailableSearchProviders(getContext(), prefs);

                for (String searchProvider : availableSearchProviders) {
                    for (String providerToDelete : searchProvidersToDelete) {
                        if (searchProvider.startsWith(providerToDelete + "|")) {
                            updatedProviders.remove(searchProvider);
                        }
                    }
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putStringSet("available-search-providers", updatedProviders);
                editor.putStringSet("deleting-search-providers-names", updatedProviders);
                editor.apply();

                if (!searchProvidersToDelete.isEmpty()) {
                    Toast.makeText(SettingsFragment.this.getContext(), R.string.search_provider_deleted, Toast.LENGTH_LONG).show();
                }
            }

            return true;
        });

        PreferenceGroup category = findPreference("web-providers");
        if (category != null) {
            category.addPreference(multiPreference);
        }
    }

    private MultiSelectListPreference createCustomSearchProvidersPreference(@NonNull String key, @StringRes int title, int order) {
        MultiSelectListPreference multiPreference = new MultiSelectListPreference(getContext());
        //get stored search providers or default hard-coded values
        Set<String> availableSearchProviders = SearchProvider.getAvailableSearchProviders(getContext(), prefs);
        String[] searchProvidersArray = new String[availableSearchProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : availableSearchProviders) {
            searchProvidersArray[pos++] = searchProvider.split("\\|")[0];
        }
        multiPreference.setEnabled(!availableSearchProviders.isEmpty());
        String search_providers_title = this.getString(title);
        multiPreference.setTitle(search_providers_title);
        multiPreference.setDialogTitle(search_providers_title);
        multiPreference.setKey(key);
        multiPreference.setEntries(searchProvidersArray);
        multiPreference.setEntryValues(searchProvidersArray);
        multiPreference.setOrder(order);
        return multiPreference;
    }

    private void addDefaultSearchProvider(final SharedPreferences prefs) {
        ListPreference standardPref = new ListPreference(getContext());

        // Get selected providers to choose from
        Set<String> selectedProviders = SearchProvider.getSelectedSearchProviders(prefs);
        String[] selectedProviderArray = new String[selectedProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : selectedProviders) {
            selectedProviderArray[pos++] = searchProvider.split("\\|")[0];
        }

        String searchProvidersTitle = this.getString(R.string.search_provider_default);
        standardPref.setTitle(searchProvidersTitle);
        standardPref.setDialogTitle(searchProvidersTitle);
        standardPref.setKey("default-search-provider");
        standardPref.setEntries(selectedProviderArray);
        standardPref.setEntryValues(selectedProviderArray);
        standardPref.setOrder(0);
        standardPref.setDefaultValue("Google"); // Google is standard on install

        PreferenceGroup category = findPreference("web-providers");
        if (category != null) {
            category.addPreference(standardPref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            KissApplication.getApplication(getContext()).getIconsHandler().onPrefChanged(sharedPreferences, key);

            if (PREF_LISTS_WITH_DEPENDENCY.contains(key)) {
                updateItemToRunList(key);
            }

            if (key.equalsIgnoreCase("available-search-providers")) {
                addCustomSearchProvidersPreferences(prefs);
                getDataHandler().reloadSearchProvider();
            } else if (key.equalsIgnoreCase("selected-search-provider-names")) {
                removeSearchProviderDefault(); // in order to refresh default search engine choices
                addDefaultSearchProvider(prefs);
                getDataHandler().reloadSearchProvider();
            } else if (key.equalsIgnoreCase("enable-phone-history")) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (enabled && !Permission.checkPermission(getContext(), Permission.PERMISSION_READ_PHONE_STATE)) {
                    Permission.askPermission(Permission.PERMISSION_READ_PHONE_STATE, new Permission.PermissionResultListener() {
                        @Override
                        public void onGranted() {
                            setPhoneHistoryEnabled(true);
                        }

                        @Override
                        public void onDenied() {
                            // You don't want to give us permission, that's fine. Revert the toggle.
                            SwitchPreference p = findPreference(key);
                            if (p != null) {
                                p.setChecked(false);
                            }
                            Toast.makeText(getContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    setPhoneHistoryEnabled(enabled);
                }
            } else if (key.equalsIgnoreCase("primary-color")) {
                UIColors.clearPrimaryColorCache();
            } else if (key.equalsIgnoreCase("number-of-display-elements")) {
                QuerySearcher.clearMaxResultCountCache();
            } else if (key.equalsIgnoreCase("default-search-provider")) {
                getDataHandler().reloadSearchProvider();
            } else if ("pref-fav-tags-list".equals(key)) {
                // after we edit the fav tags list update DataHandler
                Set<String> favTags = sharedPreferences.getStringSet(key, Collections.<String>emptySet());
                DataHandler dh = getDataHandler();
                List<Pojo> favoritesPojo = dh.getFavorites();
                for (Pojo pojo : favoritesPojo)
                    if (pojo instanceof TagDummyPojo && !favTags.contains(pojo.getName()))
                        dh.removeFromFavorites(pojo.id);
                for (String tagName : favTags)
                    dh.addToFavorites(TagsProvider.generateUniqueId(tagName));
            } else if ("exclude-favorites-apps".equals(key)) {
                getDataHandler().reloadApps();
            } else if ("enable-notification-history".equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
            } else if ("selected-contact-mime-types".equals(key)) {
                getDataHandler().reloadContactsProvider();
            }
        }
    }

    protected void setPhoneHistoryEnabled(boolean enabled) {
        IncomingCallHandler.setEnabled(getContext(), enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && enabled) {
            RoleManager roleManager = ContextCompat.getSystemService(getContext(), RoleManager.class);
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQUEST_CALL_SCREENING_APP);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void fixSummaries() {
        int historyLength = getDataHandler().getHistoryLength();
        if (historyLength > 5) {
            Preference resetHistory = findPreference("reset-history");
            if (resetHistory != null) {
                resetHistory.setSummary(String.format(getString(R.string.items_title), historyLength));
            }
        }

        // Only display "rate the app" preference if the user has been using KISS long enough to enjoy it ;)
        Preference rateApp = findPreference("rate-app");
        if (rateApp != null) {
            if (historyLength < 300) {
                getPreferenceScreen().removePreference(rateApp);
            } else {
                rateApp.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=" + getContext().getApplicationContext().getPackageName()));
                    startActivity(intent);

                    return true;
                });
            }
        }
    }

    private void setListPreferenceIconsPacksData(ListPreference lp) {
        IconsHandler iph = KissApplication.getApplication(getContext()).getIconsHandler();

        CharSequence[] entries;
        CharSequence[] entryValues;
        int i;

        {
            entries = new CharSequence[iph.getIconsPacks().size() + 1];
            entryValues = new CharSequence[iph.getIconsPacks().size() + 1];

            i = 0;
            entries[0] = this.getString(R.string.icons_pack_default_name);
            entryValues[0] = "default";
        }

        for (String packageIconsPack : iph.getIconsPacks().keySet()) {
            entries[++i] = iph.getIconsPacks().get(packageIconsPack);
            entryValues[i] = packageIconsPack;
        }

        lp.setEntries(entries);
        lp.setDefaultValue("default");
        lp.setEntryValues(entryValues);
    }

    private void addHiddenTagsTogglesInformation(SharedPreferences prefs) {
        MultiSelectListPreference selectListPreference = findPreference("pref-toggle-tags-list");
        if (selectListPreference != null) {
            Set<String> tagsSet = getDataHandler()
                    .getTagsHandler()
                    .getAllTagsAsSet();

            // append tags that are available to toggle now
            Set<String> menuTags = TagsMenu.getPrefTags(prefs, getContext());
            tagsSet.addAll(menuTags);

            String[] tagArray = tagsSet.toArray(new String[0]);
            Arrays.sort(tagArray);
            selectListPreference.setEntries(tagArray);
            selectListPreference.setEntryValues(tagArray);
            selectListPreference.setValues(menuTags);

            // Enable the preference
            getActivity().runOnUiThread(() -> {
                selectListPreference.setEnabled(true);
            });
        }
    }

    private void addTagsFavInformation() {
        final MultiSelectListPreference selectListPreference = findPreference("pref-fav-tags-list");
        if (selectListPreference != null) {
            Set<String> tagsSet = getDataHandler()
                    .getTagsHandler()
                    .getAllTagsAsSet();

            // make sure we can toggle off the tags that are in the favs now
            Set<String> favTags = getFavTags();
            tagsSet.addAll(favTags);

            String[] tagArray = tagsSet.toArray(new String[0]);
            Arrays.sort(tagArray);
            selectListPreference.setEntries(tagArray);
            selectListPreference.setEntryValues(tagArray);
            selectListPreference.setValues(favTags);

            // Enable the preference
            getActivity().runOnUiThread(() -> {
                selectListPreference.setEnabled(true);
            });
        }
    }

    /**
     * Override to catch an exception which can crash whole app.
     * This exception can occure when entries are added to/removed from preferences dynamically.
     *
     * @param key The key of the preference to retrieve.
     * @return The {@link Preference} with the key, or null.
     * @see PreferenceFragmentCompat#findPreference(CharSequence)
     */
    @Nullable
    @Override
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        try {
            return super.findPreference(key);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Unable to find preference for key:" + key);
            return null;
        }
    }

    private DataHandler getDataHandler() {
        return KissApplication.getApplication(getContext()).getDataHandler();
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        DialogFragment dialogFragment = null;
        if (pref instanceof DialogShowingPreference) {
            dialogFragment = DialogShowingPreferenceDialogFragment.newInstance(pref.getKey(), this::onDialogClosed);
        } else if (pref instanceof ColorPreference) {
            dialogFragment = ColorPreferenceDialogFragment.newInstance(pref.getKey());
        }

        if (dialogFragment != null) {
            // check if dialog is already showing
            if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
                return true;
            }
            dialogFragment.setTargetFragment(caller, 0);
            dialogFragment.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            return true;
        }

        return false;
    }

    private void onDialogClosed(Preference pref, boolean positiveResult) {
        switch (pref.getKey()) {
            case "reset-history":
                if (positiveResult) {
                    KissApplication.getApplication(getContext()).getDataHandler().clearHistory();
                    pref.setSummary(getContext().getString(R.string.history_erased));
                    Toast.makeText(getContext(), R.string.history_erased, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-search-providers":
                if (positiveResult) {
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .remove("available-search-providers").apply();
                    KissApplication.getApplication(getContext()).getDataHandler().reloadSearchProvider();
                    Toast.makeText(getContext(), R.string.search_provider_reset_done_desc, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-excluded-apps":
                if (positiveResult) {
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putStringSet("excluded-apps", null).apply();
                    KissApplication.getApplication(getContext()).getDataHandler().reloadApps();
                    Toast.makeText(getContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-excluded-from-history-apps":
                if (positiveResult) {
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putStringSet("excluded-apps-from-history", null).apply();
                    KissApplication.getApplication(getContext()).getDataHandler().reloadApps(); // reload because it's cached in AppPojo#excludedFromHistory
                    Toast.makeText(getContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-excluded-app-shortcuts":
                if (positiveResult) {
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putStringSet(DataHandler.PREF_KEY_EXCLUDED_SHORTCUT_APPS, null).apply();
                    DataHandler dataHandler = KissApplication.getApplication(getContext()).getDataHandler();
                    // Reload shortcuts to refresh the shortcuts shown in KISS
                    dataHandler.reloadShortcuts();
                    // Reload apps since the `AppPojo.isExcludedShortcuts` value also needs to be refreshed
                    dataHandler.reloadApps();
                    Toast.makeText(getContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-favorites":
                if (positiveResult) {
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                            .putString("favorite-apps-list", "").apply();

                    try {
                        KissApplication.getApplication(getContext()).getDataHandler().reloadApps();
                    } catch (NullPointerException e) {
                        Log.e(TAG, "Unable to reset favorites", e);
                    }

                    Toast.makeText(getContext(), R.string.favorites_erased, Toast.LENGTH_LONG).show();
                }
                break;
            case "reset-shortcuts":
                if (positiveResult && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Remove all shortcuts
                    ShortcutUtil.removeAllShortcuts(getContext());
                    // Build all shortcuts
                    ShortcutUtil.addAllShortcuts(getContext());
                    Toast.makeText(getContext(), R.string.regenerate_shortcuts_done, Toast.LENGTH_LONG).show();
                }
                break;
            case "enable-notifications":
                if (positiveResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
                break;
            case "default-launcher":
                new DefaultLauncherPreference().onDialogClosed(getContext(), positiveResult);
                break;
            case "export-settings":
                new ExportSettingsPreference().onDialogClosed(getContext(), positiveResult);
                break;
            case "import-settings":
                new ImportSettingsPreference().onDialogClosed(getContext(), positiveResult);
                break;
            case "restart":
                if (positiveResult) {
                    System.exit(0);
                }
                break;
        }
    }
}
