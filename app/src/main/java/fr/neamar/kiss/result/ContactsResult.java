package fr.neamar.kiss.result;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import fr.neamar.kiss.IconsHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;
import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.pojo.ContactsPojo;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.ui.ImprovedQuickContactBadge;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.ui.ShapedContactBadge;
import fr.neamar.kiss.utils.ComponentUtils;
import fr.neamar.kiss.utils.FuzzyScore;
import fr.neamar.kiss.utils.MimeTypeUtils;
import fr.neamar.kiss.utils.UserHandle;

public class ContactsResult extends CallResult {
    private final ContactsPojo contactPojo;
    private final QueryInterface queryInterface;
    private Drawable icon = null;
    private Drawable appDrawable = null;
    private final UserHandle userHandle;

    ContactsResult(QueryInterface queryInterface, ContactsPojo contactPojo) {
        super(contactPojo);
        this.contactPojo = contactPojo;
        this.queryInterface = queryInterface;
        this.userHandle = new UserHandle();
    }

    @NonNull
    @Override
    public View display(Context context, View view, @NonNull ViewGroup parent, FuzzyScore fuzzyScore) {
        if (view == null)
            view = inflateFromId(context, R.layout.item_contact, parent);

        // Contact name
        TextView contactName = view.findViewById(R.id.item_contact_name);
        if (!TextUtils.isEmpty(contactPojo.getName())) {
            displayHighlighted(contactPojo.normalizedName, contactPojo.getName(), fuzzyScore, contactName, context);
        }

        // Contact phone or IM identifier
        TextView contactPhone = view.findViewById(R.id.item_contact_phone);
        if (contactPojo.getImData() != null && !TextUtils.isEmpty(contactPojo.getImData().getIdentifier())) {
            contactPhone.setVisibility(View.VISIBLE);
            displayHighlighted(contactPojo.getImData().getNormalizedIdentifier(), contactPojo.getImData().getIdentifier(), fuzzyScore, contactPhone, context);
        } else if (!TextUtils.isEmpty(contactPojo.phone)) {
            contactPhone.setVisibility(View.VISIBLE);
            displayHighlighted(contactPojo.normalizedPhone, contactPojo.phone, fuzzyScore, contactPhone, context);
        } else {
            contactPhone.setVisibility(View.GONE);
        }

        // Contact nickname
        TextView contactNickname = view.findViewById(R.id.item_contact_nickname);
        if (TextUtils.isEmpty(contactPojo.getNickname())) {
            contactNickname.setVisibility(View.GONE);
        } else {
            contactNickname.setVisibility(View.VISIBLE);
            displayHighlighted(contactPojo.normalizedNickname, contactPojo.getNickname(), fuzzyScore, contactNickname, context);
        }

        // Contact photo
        ImprovedQuickContactBadge contactIcon = view
                .findViewById(R.id.item_contact_icon);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("icons-hide", false)) {
            if (contactIcon.getTag() instanceof ContactsPojo && contactPojo.equals(contactIcon.getTag())) {
                icon = contactIcon.getDrawable();
            }
            this.setAsyncDrawable(contactIcon);
        } else {
            contactIcon.setImageDrawable(null);
        }

        contactIcon.assignContactUri(Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                String.valueOf(contactPojo.lookupKey)));
        contactIcon.setExtraOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                recordLaunch(v.getContext(), queryInterface);
            }
        });

        int primaryColor = UIColors.getPrimaryColor(context);
        PackageManager pm = context.getPackageManager();
        boolean hasPhone = contactPojo.phone != null && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        // Phone action
        ImageButton phoneButton = view.findViewById(R.id.item_contact_action_phone);
        phoneButton.setColorFilter(primaryColor);

        if (hasPhone) {
            phoneButton.setVisibility(View.VISIBLE);
            phoneButton.setOnClickListener(v -> {
                launchCall(v.getContext(), v, contactPojo.phone);
                recordLaunch(context, queryInterface);
            });
        } else {
            phoneButton.setVisibility(View.GONE);
        }

        // Message action
        ImageButton messageButton = view.findViewById(R.id.item_contact_action_message);
        messageButton.setColorFilter(primaryColor);

        if (contactPojo.getImData() != null) {
            messageButton.setVisibility(View.VISIBLE);
            messageButton.setOnClickListener(v -> {
                launchIm(v.getContext(), v);
                recordLaunch(context, queryInterface);
            });
        } else if (hasPhone) {
            messageButton.setVisibility(View.VISIBLE);
            messageButton.setOnClickListener(v -> {
                launchMessaging(v.getContext());
                recordLaunch(context, queryInterface);
            });

            if (contactPojo.isHomeNumber()) {
                messageButton.setVisibility(View.INVISIBLE);
            } else {
                messageButton.setVisibility(View.VISIBLE);
            }
        } else {
            messageButton.setVisibility(View.INVISIBLE);
        }

        // App icon
        final ImageView appIcon = view.findViewById(R.id.item_app_icon);
        if (contactPojo.getImData() != null) {
            appIcon.setVisibility(View.VISIBLE);

            IconsHandler iconsHandler = KissApplication.getApplication(context).getIconsHandler();
            if (appDrawable == null) {
                ComponentName componentName = KissApplication.getMimeTypeCache(context).getComponentName(context, contactPojo.getImData().getMimeType());
                if (componentName != null) {
                    appDrawable = iconsHandler.getDrawableIconForPackage(ComponentUtils.getLaunchingComponent(context, componentName), this.userHandle);
                } else {
                    // This should never happen, let's just return the generic activity icon
                    appDrawable = context.getPackageManager().getDefaultActivityIcon();
                }
            }
            appIcon.setImageDrawable(appDrawable);
        } else {
            appIcon.setVisibility(View.GONE);
        }


        return view;
    }

    @Override
    protected ListPopup buildPopupMenu(Context context, ArrayAdapter<ListPopup.Item> adapter, final RecordAdapter parent, View parentView) {
        adapter.add(new ListPopup.Item(context, R.string.menu_remove));
        adapter.add(new ListPopup.Item(context, R.string.menu_contact_copy_phone));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_add));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_remove));

        return inflatePopupMenu(adapter, context);
    }

    @Override
    protected boolean popupMenuClickHandler(Context context, RecordAdapter parent, int stringId, View parentView) {
        switch (stringId) {
            case R.string.menu_contact_copy_phone:
                copyPhone(context, contactPojo);
                return true;
        }

        return super.popupMenuClickHandler(context, parent, stringId, parentView);
    }

    @SuppressWarnings("deprecation")
    private void copyPhone(Context context, ContactsPojo contactPojo) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        assert clipboard != null;
        android.content.ClipData clip = android.content.ClipData.newPlainText(
                "Phone number for " + contactPojo.getName(),
                contactPojo.phone);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    boolean isDrawableCached() {
        return icon != null;
    }

    @Override
    void setDrawableCache(Drawable drawable) {
        icon = drawable;
    }

    @Override
    public Drawable getDrawable(Context context) {
        synchronized (this) {
            if (isDrawableCached())
                return icon;
            if (contactPojo.icon != null) {
                InputStream inputStream = null;
                try {
                    inputStream = context.getContentResolver()
                            .openInputStream(contactPojo.icon);
                    return icon = Drawable.createFromStream(inputStream, null);
                } catch (FileNotFoundException ignored) {
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }

            // Default icon
            return icon = context.getResources()
                    .getDrawable(R.drawable.ic_contact);
        }
    }

    @NonNull
    @Override
    public View inflateFavorite(@NonNull Context context, @NonNull ViewGroup parent) {
        Drawable drawable = getDrawable(context);
        if (drawable != null) {
            drawable = ShapedContactBadge.getShapedDrawable(context, drawable);
        }
        View favoriteView = super.inflateFavorite(context, parent);
        ImageView favoriteImage = favoriteView.findViewById(R.id.favorite);
        favoriteImage.setImageDrawable(drawable);
        return favoriteView;
    }

    private void launchContactView(Context context, View v) {
        Intent viewContact = new Intent(Intent.ACTION_VIEW);

        viewContact.setData(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                String.valueOf(contactPojo.lookupKey)));
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            viewContact.setSourceBounds(v.getClipBounds());
        }

        viewContact.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        viewContact.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(viewContact);

    }


    @Override
    public void doLaunch(Context context, View v) {
        SharedPreferences settingPrefs = PreferenceManager.getDefaultSharedPreferences(v.getContext());
        boolean callContactOnClick = settingPrefs.getBoolean("call-contact-on-click", false);

        if (callContactOnClick) {
            launchCall(context, v, contactPojo.phone);
        } else {
            launchContactView(context, v);
        }
    }

    private void launchMessaging(final Context context) {
        String url = "sms:" + Uri.encode(contactPojo.phone);
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    private void launchIm(final Context context, final View v) {
        Intent intent = MimeTypeUtils.getRegisteredIntentByMimeType(context, contactPojo.getImData().getMimeType(), contactPojo.getImData().getId(), contactPojo.getImData().getIdentifier());
        if (intent != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                intent.setSourceBounds(v.getClipBounds());
            }
            context.startActivity(intent);
        }
    }

}
