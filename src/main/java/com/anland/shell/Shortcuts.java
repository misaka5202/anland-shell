package com.anland.shell;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import com.anland.shell.ds.AppEntry;
import com.anland.shell.ui.IconLoader;

import java.util.Collections;

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
    private static final String ANLAND_PIN_ACTIVITY =
            "com.anlandnext.PinShortcutActivity";

    private static String shortcutId(AppEntry app) {
        return app.container + "/" + app.id;
    }

    private static ShortcutInfo buildInfo(Context ctx, AppEntry app, Bitmap icon) {
        return new ShortcutInfo.Builder(ctx, shortcutId(app))
                .setShortLabel(app.name)
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(launchIntent(ctx, app))
                .build();
    }

    /** Ask the host to create the shortcut so the icon is owned by
     *  com.anlandnext and survives uninstalling this launcher. Older hosts do
     *  not expose the entry point — false makes the caller fall back. */
    private static boolean requestHostPin(Context ctx, AppEntry app, Bitmap icon) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(ANLAND_PKG, ANLAND_PIN_ACTIVITY));
        i.putExtra("container", app.container);
        i.putExtra("user", Prefs.launchUser(ctx, app.container));
        i.putExtra("app_id", app.id);
        i.putExtra("app_name", app.name);
        i.putExtra("shortcut_id", shortcutId(app));
        i.putExtra("icon", icon);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** Ask the launcher to pin. Returns false when the launcher does not
     *  support pinning and the legacy broadcast was sent instead. */
    public static boolean pin(Context ctx, AppEntry app, Bitmap icon) {
        if (icon == null)
            icon = IconLoader.letterTile(app.name, 192);
        ShortcutInfo info = buildInfo(ctx, app, icon);
        String id = shortcutId(app);

        ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);

        /* Shortcuts pinned before v1.1.4 still target this app's trampoline.
         * Only the owner can update a pinned shortcut, so repair them here
         * rather than let the host create a duplicate icon. */
        if (sm != null) {
            for (ShortcutInfo old : sm.getPinnedShortcuts()) {
                if (id.equals(old.getId())) {
                    try {
                        if (sm.updateShortcuts(Collections.singletonList(info)))
                            return true;
                    } catch (RuntimeException ignored) {
                        /* fall through and try the host instead */
                    }
                    break;
                }
            }
        }

        /* New shortcut: let the host own it so removing this launcher keeps
         * the icon. Falls back when the host predates the pin entry. */
        if (requestHostPin(ctx, app, icon))
            return true;

        if (sm != null && sm.isRequestPinShortcutSupported())
            return sm.requestPinShortcut(info, null);

        /* legacy broadcast — the manifest holds INSTALL_SHORTCUT permission */
        Intent add = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        add.putExtra(Intent.EXTRA_SHORTCUT_NAME, app.name);
        add.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent(ctx, app));
        ctx.sendBroadcast(add);
        return false;
    }
}
