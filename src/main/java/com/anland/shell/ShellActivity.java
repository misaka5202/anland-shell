package com.anland.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.anland.shell.ds.AppEntry;
import com.anland.shell.ds.ContainerState;
import com.anland.shell.ds.DsCli;
import com.anland.shell.ds.EnvVars;
import com.anland.shell.ds.RootExec;
import com.anland.shell.ui.AppsGridAdapter;
import com.anland.shell.ui.AppsView;
import com.anland.shell.ui.ContainersView;
import com.anland.shell.ui.IconLoader;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Launcher entry: a Containers tab (list + lifecycle actions) and an Apps
 * tab (the active container's .desktop applications). All droidspaces work
 * happens on RootExec.POOL; the active container is remembered in Prefs and
 * auto-started (once per activity instance) when the shell is opened.
 */
public final class ShellActivity extends Activity
        implements ContainersView.Listener, AppsView.Listener, AppsGridAdapter.Listener {

    private static final int TAB_CONTAINERS = 0;
    private static final int TAB_APPS = 1;

    private final Handler main = new Handler(Looper.getMainLooper());

    private IconLoader icons;
    private ContainersView containersView;
    private AppsView appsView;
    /** Field, not a local: the shortcut creator re-reads the list after it
     *  writes an entry so it can pin the one just created. */
    private AppsGridAdapter adapter;
    private TextView status;
    private TextView tabContainers, tabApps;
    private Button userBtn;
    private List<String> containerUsers = Collections.emptyList();
    /** anlandx detection for the active container (null = not checked —
     *  container stopped, or the probe has not answered yet). */
    private Boolean anlandxInstalled;

    private List<ContainerState> containers = Collections.emptyList();
    private boolean refreshing;
    private boolean autoStarted;   /* auto-start check consumed once per instance */
    private int tab = TAB_CONTAINERS;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        icons = new IconLoader(this);
        adapter = new AppsGridAdapter(this, icons, this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg));

        /* header: title + refresh */
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(12), dp(4));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(18);
        title.setTextColor(getResources().getColor(R.color.text_primary));
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        userBtn = new Button(this);
        userBtn.setOnClickListener(v -> showUserMenu());
        header.addView(userBtn);
        Button envBtn = new Button(this);
        envBtn.setText(R.string.env_btn);
        envBtn.setOnClickListener(v -> showEnvEditor());
        header.addView(envBtn);
        Button refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setOnClickListener(v -> refresh());
        header.addView(refresh);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* tab row */
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabContainers = tabView(R.string.tab_containers);
        tabApps = tabView(R.string.tab_apps);
        tabContainers.setOnClickListener(v -> selectTab(TAB_CONTAINERS));
        tabApps.setOnClickListener(v -> selectTab(TAB_APPS));
        tabs.addView(tabContainers, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(tabApps, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /* content */
        FrameLayout content = new FrameLayout(this);
        containersView = new ContainersView(this, this);
        appsView = new AppsView(this, adapter, this);
        content.addView(containersView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(appsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        /* status line */
        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(getResources().getColor(R.color.text_secondary));
        status.setPadding(dp(16), dp(6), dp(16), dp(10));
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(TAB_CONTAINERS);
        updateUserButton();
    }

    private TextView tabView(int labelRes) {
        TextView t = new TextView(this);
        t.setText(labelRes);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(15);
        t.setPadding(0, dp(12), 0, dp(12));
        return t;
    }

    private void selectTab(int which) {
        tab = which;
        styleTab(tabContainers, which == TAB_CONTAINERS);
        styleTab(tabApps, which == TAB_APPS);
        containersView.setVisibility(which == TAB_CONTAINERS ? View.VISIBLE : View.GONE);
        appsView.setVisibility(which == TAB_APPS ? View.VISIBLE : View.GONE);
    }

    private void styleTab(TextView t, boolean selected) {
        t.setTextColor(getResources().getColor(selected ? R.color.accent : R.color.text_secondary));
        t.setBackgroundColor(getResources().getColor(selected ? R.color.accent_dim : R.color.bg));
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    // -------------------------------------------------------------- refreshing

    private void refresh() {
        if (refreshing)
            return;
        refreshing = true;
        status.setText(R.string.loading);
        RootExec.POOL.execute(() -> {
            final List<ContainerState> list = DsCli.listContainers();
            main.post(() -> {
                refreshing = false;
                onContainers(list);
            });
        });
    }

    private void onContainers(List<ContainerState> list) {
        containers = list;

        if (DsCli.ds() == null) {
            containersView.setContainers(list, "");
            appsView.setContainer("", false);
            containerUsers = Collections.emptyList();
            anlandxInstalled = null;
            updateAppsTab();
            status.setText(getString(R.string.ds_unavailable_fmt, DsCli.unavailableReason()));
            return;
        }
        if (list.isEmpty()) {
            containersView.setContainers(list, "");
            appsView.setContainer("", false);
            containerUsers = Collections.emptyList();
            anlandxInstalled = null;
            updateAppsTab();
            status.setText(R.string.status_no_containers);
            return;
        }

        /* active container: remembered, else first running, else first */
        String active = Prefs.activeContainer(this);
        if (find(active) == null) {
            ContainerState pick = null;
            for (ContainerState c : list)
                if (c.running()) {
                    pick = c;
                    break;
                }
            if (pick == null)
                pick = list.get(0);
            active = pick.name;
            Prefs.setActiveContainer(this, active);
        }

        ContainerState c = find(active);
        boolean running = c != null && c.running();
        containersView.setContainers(list, active);
        appsView.setContainer(active, running);
        updateUserButton();
        anlandxInstalled = null;
        updateAppsTab();
        if (running) {
            loadUsers(active);
            detectAnlandx(active);
        } else {
            containerUsers = Collections.emptyList();
        }

        /* 启动时自动拉起容器 — the check fires once per activity instance */
        boolean needAutoStart = c != null && !running && !autoStarted;
        autoStarted = true;
        if (needAutoStart) {
            startContainer(active);
            return;
        }
        if (c == null)
            status.setText(R.string.no_container_selected);
        else if (c.running())
            status.setText(getString(R.string.status_active_running_fmt, c.name, c.pid));
        else
            status.setText(getString(R.string.status_active_stopped_fmt, c.name));
    }

    private void startContainer(final String name) {
        status.setText(getString(R.string.starting_fmt, name));
        RootExec.POOL.execute(() -> {
            RootExec.Result r = DsCli.start(name);
            boolean up;
            try {
                up = r.ok && DsCli.awaitRunning(name, 90_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                up = false;
            }
            final boolean ok = up;
            final RootExec.Result res = r;
            main.post(() -> {
                if (!ok)
                    Toast.makeText(this, getString(
                            res.ok ? R.string.start_timeout_fmt : R.string.start_failed_fmt,
                            name, res.ok ? "" : errText(res)), Toast.LENGTH_LONG).show();
                refresh();
            });
        });
    }

    // ---------------------------------------------------------- launch user

    private void updateUserButton() {
        String active = Prefs.activeContainer(this);
        String u = Prefs.launchUser(this, active);
        userBtn.setText(getString(R.string.user_btn_fmt,
                u.isEmpty() ? getString(R.string.user_auto) : u));
    }

    private void showUserMenu() {
        final String active = Prefs.activeContainer(this);
        PopupMenu menu = new PopupMenu(this, userBtn);
        menu.getMenu().add(0, 0, 0, R.string.user_auto);
        for (int i = 0; i < containerUsers.size(); i++)
            menu.getMenu().add(0, i + 1, i + 1, containerUsers.get(i));
        menu.setOnMenuItemClickListener(item -> {
            String u = item.getItemId() == 0 ? "" : containerUsers.get(item.getItemId() - 1);
            Prefs.setLaunchUser(this, active, u);
            updateUserButton();
            anlandxInstalled = null;   /* anlandx is per-user — re-detect */
            updateAppsTab();
            if (find(active) != null && find(active).running())
                detectAnlandx(active);
            return true;
        });
        menu.show();
    }

    private void loadUsers(final String container) {
        RootExec.POOL.execute(() -> {
            final List<String> users = DsCli.listUsers(container);
            main.post(() -> {
                if (container.equals(Prefs.activeContainer(this)))
                    containerUsers = users;
            });
        });
    }

    // -------------------------------------------------------------- anlandx

    /** Probe whether anlandx (X support) is installed for the container's
     *  launch user and reflect it in the Apps tab title. */
    private void detectAnlandx(final String container) {
        final String user = Prefs.launchUser(this, container);
        RootExec.POOL.execute(() -> {
            final boolean installed = DsCli.anlandxInstalled(container, user);
            main.post(() -> {
                if (container.equals(Prefs.activeContainer(this))) {
                    anlandxInstalled = installed;
                    updateAppsTab();
                }
            });
        });
    }

    /** Apps tab title: plain "Apps" while anlandx is present or unchecked,
     *  "Apps - Anlandx not installed" once known missing. */
    private void updateAppsTab() {
        tabApps.setText(anlandxInstalled == null || anlandxInstalled
                ? R.string.tab_apps : R.string.tab_apps_no_anlandx);
    }

    // ------------------------------------------------------------ launch env

    /** Per-container custom launch environment: KEY=VALUE lines merged over
     *  the built-ins (same name wins, empty value removes the built-in). */
    private void showEnvEditor() {
        final String active = Prefs.activeContainer(this);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), 0);

        TextView defaults = new TextView(this);
        defaults.setTextSize(11);
        defaults.setTextColor(getResources().getColor(R.color.text_secondary));
        defaults.setText(getString(R.string.env_defaults_fmt,
                EnvVars.format(DsCli.defaultEnvPairs()).replace("\n", " ")));
        body.addView(defaults);

        final EditText edit = new EditText(this);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        edit.setHint(R.string.env_editor_hint);
        edit.setMinLines(6);
        edit.setGravity(Gravity.TOP);
        edit.setText(Prefs.launchEnv(this, active));
        body.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.env_editor_title_fmt, active))
                .setView(body)
                .setNeutralButton(R.string.env_clear, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.env_save, null)
                .create();
        dlg.show();
        /* button listeners are taken over after show() so an invalid line
         * (save) or a clear does NOT dismiss the editor and lose the edit */
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> edit.setText(""));
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = edit.getText().toString();
            String bad = EnvVars.invalidLine(text);
            if (bad != null) {
                Toast.makeText(this, getString(R.string.env_invalid_line_fmt, bad),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Prefs.setLaunchEnv(this, active, text);
            Toast.makeText(this, R.string.env_saved, Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });
    }

    private void stopContainer(final String name) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.stop_confirm_fmt, name))
                .setPositiveButton(R.string.stop, (d, w) -> {
                    status.setText(getString(R.string.stopping_fmt, name));
                    RootExec.POOL.execute(() -> {
                        DsCli.stop(name);
                        main.post(this::refresh);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------ ContainersView

    @Override public void onSelectContainer(String name) {
        Prefs.setActiveContainer(this, name);
        ContainerState c = find(name);
        boolean running = c != null && c.running();
        containersView.setContainers(containers, name);
        appsView.setContainer(name, running);
        updateUserButton();
        anlandxInstalled = null;
        updateAppsTab();
        if (running) {
            loadUsers(name);
            detectAnlandx(name);
        } else {
            containerUsers = Collections.emptyList();
        }
        if (c != null && running)
            status.setText(getString(R.string.status_active_running_fmt, name, c.pid));
        else
            status.setText(getString(R.string.status_active_stopped_fmt, name));
    }

    @Override public void onEnterContainer(String name) {
        startActivity(new Intent(this, ConsoleActivity.class).putExtra("container", name));
    }

    @Override public void onStartContainer(String name) {
        startContainer(name);
    }

    @Override public void onStopContainer(String name) {
        stopContainer(name);
    }

    @Override public void onShowApps(String name) {
        Prefs.setActiveContainer(this, name);
        ContainerState c = find(name);
        appsView.setContainer(name, c != null && c.running());
        selectTab(TAB_APPS);
    }

    // ----------------------------------------------------------- AppsView

    @Override public void onRequestStart(String name) {
        startContainer(name);
    }

    /** Shortcut creator: point at any in-container command, including software
     *  that ships no .desktop file, then pin it to the Android home screen. The
     *  entry is written inside the container so the grid, other launchers and
     *  a desktop session all see the same definition. */
    @Override public void onRequestCreate(final String name) {
        final EditText fName = new EditText(this);
        fName.setHint(R.string.create_app_name);
        final EditText fExec = new EditText(this);
        fExec.setHint(R.string.create_app_exec);
        final EditText fIcon = new EditText(this);
        fIcon.setHint(R.string.create_app_icon);
        final CheckBox fTerm = new CheckBox(this);
        fTerm.setText(R.string.create_app_terminal);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(fName);
        box.addView(fExec);
        box.addView(fIcon);
        box.addView(fTerm);

        new AlertDialog.Builder(this)
                .setTitle(R.string.create_app_title)
                .setView(box)
                .setNegativeButton(R.string.create_app_cancel, null)
                .setPositiveButton(R.string.create_app_ok, (d, w) -> {
                    String exec = fExec.getText().toString().trim();
                    if (exec.isEmpty()) {
                        Toast.makeText(this, R.string.create_app_empty_exec,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String label = fName.getText().toString().trim();
                    if (label.isEmpty())
                        label = exec.substring(exec.lastIndexOf('/') + 1);
                    createShortcutEntry(name, label, exec,
                            fIcon.getText().toString().trim(),
                            fTerm.isChecked());
                })
                .show();
    }

    private void createShortcutEntry(final String container, final String label,
                                     final String exec, final String icon,
                                     final boolean terminal) {
        final String id = slug(label);
        Toast.makeText(this, R.string.create_app_working, Toast.LENGTH_SHORT).show();
        RootExec.POOL.execute(() -> {
            final RootExec.Result r =
                    DsCli.createDesktop(container, id, label, exec, icon, terminal);
            runOnUiThread(() -> {
                if (!r.ok) {
                    Toast.makeText(this,
                            getString(R.string.create_app_failed, errText(r)),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                /* reload, then pin the entry the listing just produced */
                appsView.refresh(() -> pinCreated(container, id));
            });
        });
    }

    private void pinCreated(String container, String id) {
        List<AppEntry> apps = adapter.apps();
        if (apps != null) {
            for (AppEntry a : apps) {
                if (container.equals(a.container) && id.equals(a.id)) {
                    pinShortcut(a);
                    return;
                }
            }
        }
        Toast.makeText(this, R.string.create_app_done, Toast.LENGTH_SHORT).show();
    }

    /** .desktop ids are derived from the display name; keep them to the
     *  characters a file name and the parser both accept. */
    private static String slug(String label) {
        String s = label.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty())
            s = "app-" + Long.toHexString(System.currentTimeMillis());
        return "anland-" + s;
    }

    // ---------------------------------------------------- AppsGridAdapter

    @Override public void onAppClick(AppEntry app) {
        startActivity(Shortcuts.launchIntent(this, app));
    }

    @Override public void onAppLongClick(final AppEntry app, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.pin_shortcut);
        menu.getMenu().add(0, 2, 1, R.string.app_info);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1)
                pinShortcut(app);
            else if (item.getItemId() == 2)
                showAppInfo(app);
            return true;
        });
        menu.show();
    }

    private void pinShortcut(final AppEntry app) {
        Toast.makeText(this, R.string.pinned_request, Toast.LENGTH_SHORT).show();
        Bitmap cached = icons.peek(app);
        if (cached != null) {
            doPin(app, cached);
            return;
        }
        icons.load(app, (key, bmp) -> doPin(app, bmp));
    }

    private void doPin(AppEntry app, Bitmap icon) {
        if (!Shortcuts.pin(this, app, icon))
            Toast.makeText(this, R.string.pinned_fallback, Toast.LENGTH_LONG).show();
    }

    private void showAppInfo(AppEntry app) {
        new AlertDialog.Builder(this)
                .setTitle(app.name)
                .setMessage("container: " + app.container +
                        "\nid: " + app.id +
                        "\nexec: " + app.exec +
                        "\nicon: " + (app.icon.isEmpty() ? "(none)" : app.icon) +
                        "\ndesktop: " + app.desktopPath)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    // ----------------------------------------------------------------- misc

    private ContainerState find(String name) {
        for (ContainerState c : containers)
            if (c.name.equals(name))
                return c;
        return null;
    }

    private static String errText(RootExec.Result r) {
        if (r.error != null)
            return r.error;
        String e = r.stderr.trim();
        return e.isEmpty() ? "exit " + r.exit : e.split("\n", 2)[0];
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
