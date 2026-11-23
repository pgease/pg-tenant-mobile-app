package in.pgease.tenant.shell;

import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import in.pgease.tenant.MainActivity;

/**
 * Keeps gateway pages in the app and routes everything else through
 * {@link ShellNavigationPolicy}. Capacitor's default client opens every
 * other host in the system browser, which drops Easebuzz checkout.
 */
public class ShellWebViewClient extends BridgeWebViewClient {

    private final MainActivity activity;

    public ShellWebViewClient(Bridge bridge, MainActivity activity) {
        super(bridge);
        this.activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
            return false;
        }
        String url = request.getUrl().toString();
        if (!request.isForMainFrame()) {
            if (activity.navigationPolicy().decide(url) == ShellNavigationPolicy.Decision.EXTERNAL) {
                activity.openExternal(url);
                return true;
            }
            return false;
        }
        return activity.handleNavigation(url);
    }

    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        activity.onShellPageStarted(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        activity.onShellPageFinished(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request != null && request.isForMainFrame()) {
            String failed = request.getUrl() == null ? null : request.getUrl().toString();
            activity.onShellMainFrameError(failed);
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
        activity.onShellMainFrameError(error == null ? null : error.getUrl());
    }
}
