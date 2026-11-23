package in.pgease.tenant.shell;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;
import java.util.Locale;

/**
 * Saves receipt and agreement PDFs into the public Downloads collection.
 * DownloadManager posts its own notification, so the app does not request
 * the Android 13 notification permission.
 */
public final class ShellDownloader {

    private final Context context;

    public ShellDownloader(Context context) {
        this.context = context.getApplicationContext();
    }

    public void enqueue(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url == null || url.isEmpty() || url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(context, "This file can't be downloaded", Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = mimeType == null || mimeType.isEmpty() ? "application/pdf" : mimeType;
        String fileName = URLUtil.guessFileName(url, contentDisposition, mime);
        if (fileName == null || fileName.isEmpty()) {
            fileName = "pgease-download.pdf";
        }
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (mime.toLowerCase(Locale.ROOT).contains("pdf") && !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            fileName = fileName + ".pdf";
        }

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(context, "Downloads aren't available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mime);
        request.setTitle(fileName);
        request.setDescription("PG Ease Tenant");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName);
        }
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.isEmpty()) {
            request.addRequestHeader("Cookie", cookies);
        }
        try {
            manager.enqueue(request);
            Toast.makeText(context, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show();
        }
    }
}
