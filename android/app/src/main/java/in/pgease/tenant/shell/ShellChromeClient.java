package in.pgease.tenant.shell;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Message;
import android.provider.MediaStore;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Locale;

/**
 * File chooser that offers the camera and the gallery for image inputs, and
 * keeps target=_blank links (receipts, e-sign, WhatsApp) inside the shell's
 * navigation rules instead of dropping them.
 */
public class ShellChromeClient extends WebChromeClient {

    public interface Navigator {
        void dispatchNavigation(String url);
    }

    private static ValueCallback<Uri[]> pendingCallback;
    private static Uri pendingCameraUri;
    private static boolean pendingCaptureOnly;

    private final ComponentActivity activity;
    private final Navigator navigator;
    private final ActivityResultLauncher<Intent> chooserLauncher;
    private final ActivityResultLauncher<String> cameraPermissionLauncher;
    private boolean waitingForCameraPermission;
    private FileChooserParams pendingParams;

    public ShellChromeClient(ComponentActivity activity, Navigator navigator) {
        this.activity = activity;
        this.navigator = navigator;
        this.chooserLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> deliverChooserResult(result.getResultCode(), result.getData())
        );
        this.cameraPermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!waitingForCameraPermission) {
                    return;
                }
                waitingForCameraPermission = false;
                FileChooserParams params = pendingParams;
                pendingParams = null;
                if (params == null) {
                    finish(null);
                    return;
                }
                launchChooser(params, granted);
            }
        );
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        finish(null);
        pendingCallback = filePathCallback;
        pendingParams = fileChooserParams;
        if (acceptsImages(fileChooserParams) && !hasCameraPermission()) {
            waitingForCameraPermission = true;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return true;
        }
        launchChooser(fileChooserParams, hasCameraPermission());
        return true;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        WebView popup = new WebView(view.getContext());
        popup.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView popupView, android.webkit.WebResourceRequest request) {
                if (request.getUrl() != null) {
                    navigator.dispatchNavigation(request.getUrl().toString());
                }
                return true;
            }
        });
        if (resultMsg != null && resultMsg.obj instanceof WebView.WebViewTransport) {
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
        }
        return true;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        if (activity.isFinishing()) {
            result.cancel();
            return true;
        }
        new AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
            .setOnCancelListener(dialog -> result.cancel())
            .show();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        if (activity.isFinishing()) {
            result.cancel();
            return true;
        }
        new AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
            .setOnCancelListener(dialog -> result.cancel())
            .show();
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        if (activity.isFinishing()) {
            result.cancel();
            return true;
        }
        EditText input = new EditText(activity);
        input.setText(defaultValue == null ? "" : defaultValue);
        new AlertDialog.Builder(activity)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
            .setOnCancelListener(dialog -> result.cancel())
            .show();
        return true;
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        activity.runOnUiThread(() -> {
            boolean wantsCamera = false;
            if (request.getResources() != null) {
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                        wantsCamera = true;
                    }
                }
            }
            if (wantsCamera && !hasCameraPermission()) {
                request.deny();
                return;
            }
            request.grant(request.getResources());
        });
    }

    private void launchChooser(FileChooserParams params, boolean includeCamera) {
        boolean images = acceptsImages(params);
        boolean captureOnly = params != null && params.isCaptureEnabled() && images;
        pendingCaptureOnly = captureOnly;
        pendingCameraUri = null;

        Intent camera = null;
        if (includeCamera && images) {
            camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try {
                pendingCameraUri = createCaptureUri();
                camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                camera.setClipData(ClipData.newRawUri("capture", pendingCameraUri));
            } catch (Exception ex) {
                camera = null;
                pendingCameraUri = null;
            }
        }

        try {
            if (captureOnly && camera != null) {
                chooserLauncher.launch(camera);
                return;
            }
            Intent content = new Intent(Intent.ACTION_GET_CONTENT);
            content.addCategory(Intent.CATEGORY_OPENABLE);
            content.setType(images ? "image/*" : "*/*");
            if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            Intent chooser = Intent.createChooser(content, images ? "Upload photo" : "Upload file");
            if (camera != null) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { camera });
            }
            chooserLauncher.launch(chooser);
        } catch (ActivityNotFoundException ex) {
            finish(null);
        }
    }

    private void deliverChooserResult(int resultCode, Intent data) {
        if (pendingCallback == null) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            finish(null);
            return;
        }

        File capture = captureFile();
        if (capture != null && capture.exists() && capture.length() > 0 && pendingCameraUri != null) {
            finish(new Uri[] { pendingCameraUri });
            return;
        }

        Uri[] parsed = FileChooserParams.parseResult(resultCode, data);
        if ((parsed == null || parsed.length == 0) && data != null && data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            parsed = new Uri[count];
            for (int i = 0; i < count; i++) {
                parsed[i] = data.getClipData().getItemAt(i).getUri();
            }
        }
        if ((parsed == null || parsed.length == 0) && pendingCaptureOnly && pendingCameraUri != null) {
            parsed = new Uri[] { pendingCameraUri };
        }
        finish(parsed);
    }

    private Uri createCaptureUri() {
        File dir = new File(activity.getCacheDir(), "captures");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create capture directory");
        }
        File photo = new File(dir, "capture-" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photo);
    }

    private File captureFile() {
        if (pendingCameraUri == null) {
            return null;
        }
        File dir = new File(activity.getCacheDir(), "captures");
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        File newest = files[0];
        for (File file : files) {
            if (file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean acceptsImages(FileChooserParams params) {
        if (params == null || params.getAcceptTypes() == null) {
            return false;
        }
        for (String type : params.getAcceptTypes()) {
            if (type == null) {
                continue;
            }
            String value = type.toLowerCase(Locale.ROOT).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.startsWith("image/") || "image".equals(value) || value.endsWith(".jpg")
                || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp")
                || value.endsWith(".heic")) {
                return true;
            }
        }
        return false;
    }

    private static void finish(Uri[] value) {
        ValueCallback<Uri[]> callback = pendingCallback;
        pendingCallback = null;
        pendingCameraUri = null;
        pendingCaptureOnly = false;
        if (callback != null) {
            callback.onReceiveValue(value);
        }
    }
}
