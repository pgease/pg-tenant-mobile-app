package in.pgease.tenant;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.getcapacitor.BridgeActivity;
import in.pgease.tenant.shell.ShellChromeClient;
import in.pgease.tenant.shell.ShellDownloader;
import in.pgease.tenant.shell.ShellNavigationPolicy;
import in.pgease.tenant.shell.ShellScripts;
import in.pgease.tenant.shell.ShellSwipeRefreshLayout;
import in.pgease.tenant.shell.ShellWebViewClient;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends BridgeActivity implements ShellChromeClient.Navigator {

    private static final String PREFS_NAME = "pgease_tenant_mobile_prefs";
    private static final String KEY_ONBOARDING_SEEN = "has_seen_onboarding_slides";
    private static final int BRAND = 0xFF008080;
    private static final long EXIT_WINDOW_MS = 2000L;

    private final AtomicBoolean pullAllowed = new AtomicBoolean(true);
    private final AtomicBoolean onAppHost = new AtomicBoolean(true);
    private final AtomicBoolean offline = new AtomicBoolean(false);

    private ShellNavigationPolicy policy;
    private ShellDownloader downloader;
    private ShellSwipeRefreshLayout swipeRefresh;
    private View offlineView;
    private String failedUrl;
    private boolean mainFrameFailed;
    private long lastExitPromptAt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getBridge() == null || getBridge().getWebView() == null) {
            return;
        }

        String serverUrl = getBridge().getConfig().getServerUrl();
        policy = new ShellNavigationPolicy(serverUrl);
        downloader = new ShellDownloader(this);
        installShell();
        applyBrandBars();
        installBackHandler();
        // Single source of truth: onboarding is handled exclusively by the web app (OnboardingCarousel.tsx)
        // showOnboardingIfNeeded();
        openDeepLink(getIntent());

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            WebView webView = web();
            if (webView != null) {
                webView.evaluateJavascript("if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) { window.Capacitor.Plugins.SplashScreen.hide(); }", null);
            }
        }, 3000);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyBrandBars();
    }

    @Override
    public void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openDeepLink(intent);
    }

    public ShellNavigationPolicy navigationPolicy() {
        return policy;
    }

    /**
     * @return true when the shell consumed the navigation
     */
    public boolean handleNavigation(String url) {
        if (policy == null || url == null) {
            return false;
        }
        if ("pgease".equals(ShellNavigationPolicy.schemeOf(url))) {
            String mapped = policy.toInAppUrl(url);
            WebView webView = web();
            if (mapped != null && webView != null) {
                webView.loadUrl(mapped);
            }
            return true;
        }
        switch (policy.decide(url)) {
            case EXTERNAL:
                openExternal(url);
                return true;
            case DOWNLOAD:
                WebView webView = web();
                String agent = webView == null ? null : webView.getSettings().getUserAgentString();
                downloader.enqueue(url, agent, null, "application/pdf");
                return true;
            case CANCEL:
                return true;
            case LOAD:
            default:
                return false;
        }
    }

    @Override
    public void dispatchNavigation(String url) {
        if (!handleNavigation(url)) {
            WebView webView = web();
            if (webView != null && url != null) {
                webView.loadUrl(url);
            }
        }
    }

    public void openExternal(String url) {
        try {
            Intent intent;
            if (url != null && url.startsWith("intent:")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            String fallback = intentFallback(url);
            if (fallback != null) {
                dispatchNavigation(fallback);
                return;
            }
            int message = ShellNavigationPolicy.isUpiScheme(url) ? R.string.shell_no_upi_app : R.string.shell_no_app;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, R.string.shell_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    public void onShellPageStarted(String url) {
        mainFrameFailed = false;
        boolean appHost = policy != null && policy.isAppHost(url);
        onAppHost.set(appHost);
        pullAllowed.set(appHost && !offline.get());
        hideOffline();
        injectShellScript();
    }

    public void onShellPageFinished(String url) {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        if (!mainFrameFailed) {
            hideOffline();
        }
        injectShellScript();
        WebView webView = web();
        if (webView != null) {
            webView.evaluateJavascript("if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) { window.Capacitor.Plugins.SplashScreen.hide(); }", null);
        }
    }

    public void onShellMainFrameError(String url) {
        mainFrameFailed = true;
        if (url != null && !url.startsWith("about:")) {
            failedUrl = url;
        }
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        showOffline();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void installShell() {
        WebView webView = web();
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setDomStorageEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new ShellChromeClient(this, this));
        getBridge().setWebViewClient(new ShellWebViewClient(getBridge(), this));
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
            downloader.enqueue(url, userAgent, contentDisposition, mimeType)
        );
        webView.addJavascriptInterface(new ShellJsBridge(pullAllowed, onAppHost, offline), "PGEaseShell");
        webView.setBackgroundColor(Color.WHITE);

        ViewGroup parent = (ViewGroup) webView.getParent();
        int index = parent.indexOfChild(webView);
        ViewGroup.LayoutParams params = webView.getLayoutParams();
        parent.removeView(webView);
        parent.setBackgroundColor(BRAND);

        swipeRefresh = new ShellSwipeRefreshLayout(this, pullAllowed);
        swipeRefresh.setLayoutParams(params);
        swipeRefresh.setBackgroundColor(BRAND);
        swipeRefresh.setColorSchemeColors(BRAND);
        swipeRefresh.addView(webView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        swipeRefresh.setOnRefreshListener(() -> {
            if (onAppHost.get()) {
                web().reload();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
        parent.addView(swipeRefresh, index);

        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh, (view, insets) -> {
            Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            boolean keyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottom = keyboard ? imeBottom : bars.bottom;
            view.setPadding(bars.left, bars.top, bars.right, bottom);
            swipeRefresh.setProgressViewOffset(false, bars.top + dp(8), bars.top + dp(72));
            return new WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .build();
        });
        ViewCompat.requestApplyInsets(swipeRefresh);
        webView.post(this::injectShellScript);
    }

    private void applyBrandBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setBackgroundDrawable(new ColorDrawable(BRAND));
        window.getDecorView().setBackgroundColor(BRAND);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onShellBack();
            }
        });
    }

    private void onShellBack() {
        if (offline.get()) {
            hideOffline();
            WebView webView = web();
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            }
            return;
        }
        WebView webView = web();
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(ShellScripts.CLOSE_OVERLAY, value -> runOnUiThread(() -> {
            if (isJavascriptTrue(value) || isJavascriptBlocked(value)) {
                return;
            }
            webView.evaluateJavascript("window.__pgeaseHandleBack ? window.__pgeaseHandleBack() : 'none'", backResult -> runOnUiThread(() -> {
                if (backResult != null && backResult.contains("handled")) {
                    return;
                }
                goBackOrExit();
            }));
        }));
    }

    private void goBackOrExit() {
        WebView webView = web();
        if (webView == null || policy == null) {
            finish();
            return;
        }
        String url = webView.getUrl();
        if (!policy.isInAppRoot(url)) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                webView.loadUrl(policy.getHomeUrl());
            }
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastExitPromptAt < EXIT_WINDOW_MS) {
            finish();
            return;
        }
        lastExitPromptAt = now;
        Toast.makeText(this, R.string.shell_exit, Toast.LENGTH_SHORT).show();
    }

    private void openDeepLink(Intent intent) {
        if (policy == null || intent == null || intent.getData() == null) {
            return;
        }
        String mapped = policy.toInAppUrl(intent.getData().toString());
        WebView webView = web();
        if (mapped != null && webView != null) {
            webView.loadUrl(mapped);
        }
    }

    private void injectShellScript() {
        WebView webView = web();
        if (webView != null) {
            webView.evaluateJavascript(ShellScripts.INSTALL, null);
        }
    }

    private void showOffline() {
        offline.set(true);
        pullAllowed.set(false);
        if (offlineView == null) {
            ViewGroup root = findViewById(android.R.id.content);
            if (root == null) {
                return;
            }
            offlineView = LayoutInflater.from(this).inflate(R.layout.shell_offline, root, false);
            offlineView.findViewById(R.id.shell_retry).setOnClickListener(v -> retry());
            root.addView(offlineView);
        }
        offlineView.setVisibility(View.VISIBLE);
        offlineView.bringToFront();
    }

    private void hideOffline() {
        offline.set(false);
        pullAllowed.set(onAppHost.get());
        if (offlineView != null) {
            offlineView.setVisibility(View.GONE);
        }
    }

    private void retry() {
        mainFrameFailed = false;
        hideOffline();
        WebView webView = web();
        if (webView == null) {
            return;
        }
        if (failedUrl != null && !failedUrl.isEmpty()) {
            webView.loadUrl(failedUrl);
        } else if (policy != null) {
            webView.loadUrl(policy.getHomeUrl());
        } else {
            webView.reload();
        }
    }

    private WebView web() {
        if (getBridge() == null) {
            return null;
        }
        return getBridge().getWebView();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean isJavascriptTrue(String value) {
        return "true".equals(value);
    }

    private static boolean isJavascriptBlocked(String value) {
        return value != null && value.contains("blocked");
    }

    private static String intentFallback(String url) {
        if (url == null || !url.startsWith("intent:")) {
            return null;
        }
        try {
            Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String fallback = parsed.getStringExtra("browser_fallback_url");
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                return fallback;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public static final class ShellJsBridge {
        private final AtomicBoolean pullAllowed;
        private final AtomicBoolean onAppHost;
        private final AtomicBoolean offline;

        ShellJsBridge(AtomicBoolean pullAllowed, AtomicBoolean onAppHost, AtomicBoolean offline) {
            this.pullAllowed = pullAllowed;
            this.onAppHost = onAppHost;
            this.offline = offline;
        }

        @android.webkit.JavascriptInterface
        public void setPullEnabled(boolean enabled) {
            pullAllowed.set(enabled && onAppHost.get() && !offline.get());
        }
    }
}
