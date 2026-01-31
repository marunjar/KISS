package fr.neamar.kiss.preference;

import android.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

public final class PreferenceScreenHelper {
	public static @Nullable Toolbar findToolbar(PreferenceScreen preference) {
//		final Dialog dialog = preference.getDialog();
//		ViewGroup root = (ViewGroup) dialog.getWindow().getDecorView();
//
//		ArrayDeque<ViewGroup> viewGroups = new ArrayDeque<>();
//		viewGroups.push(root);
//
//		while (!viewGroups.isEmpty()) {
//			ViewGroup e = viewGroups.removeFirst();
//
//			for (int i = 0; i < e.getChildCount(); i++) {
//				View child = e.getChildAt(i);
//
//				if (child instanceof Toolbar) {
//					return (Toolbar) child;
//				}
//
//				if (child instanceof ViewGroup) {
//					viewGroups.addFirst((ViewGroup) child);
//				}
//			}
//		}
//
		return null;
	}

}
