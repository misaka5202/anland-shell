package com.anland.shell;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import com.anland.shell.ds.AppEntry;
import com.anland.shell.ui.IconLoader;

/** Pin container apps to the Android home screen: ShortcutManager pin
 *  request (API 26+, always available at our minSdk 28) with the legacy
 *  INSTALL_SHORTCUT broadcast as fallback. */
public final class Shortcuts {

    private Shortcuts() {}

    /** Explicit launch intent shared by grid taps and pinned shortcuts.
     *
     *  The target is the anland host APK, not this app: the window a container
     *  application opens is hosted by com.anlandnext, so pointing the shortcut
     *  there makes the icon land in the application's own task instead of
     *  flashing this launcher's "launching…" screen first. Starting container
     *  software needs root — which that APK asks its daemon for — so only the
     *  container, the user and the .desktop id travel with the intent; the
     *  executable path is resolved inside the container from the id.
     *
     *  The launch user is snapshotted from Prefs at intent-creation time
     *  (pinned shortcuts keep the user chosen then). */
    public static Intent launchIntent(Context ctx, AppEntry app) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(ANLAND_PKG, ANLAND_LAUNCH_ACTIVITY));
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra("container", app.container);
        i.putExtra("app_id", app.id);
        i.putExtra("app_name", app.name);
        i.putExtra("user", Prefs.launchUser(ctx, app.container));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return i;
    }

    /** Host APK that hosts the windows and holds root via its daemon. */
    private static final String ANLAND_PKG = "com.anlandnext";
    private static final String ANLAND_LAUNCH_ACTIVITY =
            "com.anlandnext.AppLaunchActivity";

    /** Ask the launcher to pin. Returns false when the launcher does not
     *  support pinning and the legacy broadcast was sent instead. */
    public static boolean pin(Context ctx, AppEntry app, Bitmap icon) {
        if (icon == null)
            icon = IconLoader.letterTile(app.name, 192);
        Intent launch = launchIntent(ctx, app);

        ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
        if (sm != null && sm.isRequestPinShortcutSupported()) {
            ShortcutInfo info = new ShortcutInfo.Builder(ctx, app.container + "/" + app.id)
                    .setShortLabel(app.name)
                    .setIcon(Icon.createWithBitmap(icon))
                    .setIntent(launch)
                    .build();
            return sm.requestPinShortcut(info, null);
        }

        /* legacy broadcast — the manifest holds INSTALL_SHORTCUT permission */
        Intent add = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        add.putExtra(Intent.EXTRA_SHORTCUT_NAME, app.name);
        add.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch);
        ctx.sendBroadcast(add);
        return false;
    }
}
