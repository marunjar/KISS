package fr.neamar.kiss.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

public class PhoneUtils {

    // See https://github.com/Neamar/KISS/issues/1137
    private static final Pattern KISS_PHONE_PATTERN = Pattern.compile("^[*+0-9# ]{3,}$");

    public static String format(Context context, String phoneNumber) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String countryIso = getDefaultCountryIso(context);
            if (!TextUtils.isEmpty(countryIso)) {
                return PhoneNumberUtils.formatNumber(phoneNumber, countryIso);
            }
        }
        return phoneNumber;
    }

    public static boolean isPhoneNumber(String phoneNumber) {
        return KISS_PHONE_PATTERN.matcher(phoneNumber).find() || Patterns.PHONE.matcher(phoneNumber).find();
    }

    public static boolean areSamePhoneNumber(Context context, String number1, String number2) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String countryIso = getDefaultCountryIso(context);
            if (TextUtils.isEmpty(countryIso)) {
                return false;
            } else {
                return PhoneNumberUtils.areSamePhoneNumber(number1, number2, countryIso);
            }
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

    @NonNull
    private static String getDefaultCountryIso(Context context) {
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return manager.getNetworkCountryIso();
        } catch (Exception e) {
            return "";
        }
    }

    public static String normalizeNumber(String phoneNumber) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return PhoneNumberUtils.normalizeNumber(phoneNumber);
        } else {
            return convertKeypadLettersToDigits(phoneNumber);
        }
    }

    public static String convertKeypadLettersToDigits(String phoneNumber) {
        return PhoneNumberUtils.convertKeypadLettersToDigits(phoneNumber);
    }

}
