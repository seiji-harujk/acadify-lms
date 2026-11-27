package com.acadify.learning;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor();
    private boolean loadingVideo = false;
    private Dialog videoDialog;
    private VideoView videoView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!request.isForMainFrame()) return true;
                if ("https".equals(url.getScheme()) && "appassets.androidplatform.net".equals(url.getHost())) {
                    String path = url.getPath();
                    if ("/assets/classroom/cloud/demo.mp4".equals(path) || "/assets/classroom/ias/demo.mp4".equals(path)) {
                        playDemo(path.substring("/assets/".length()));
                        return true;
                    }
                    return false;
                }
                if ("https".equals(url.getScheme()) && "www.youtube.com".equals(url.getHost()) && "/watch".equals(url.getPath())) {
                    String id = url.getQueryParameter("v");
                    if ("PziYflu8cB8".equals(id) || "4ht22ReBjno".equals(id) || "BpTtxBnlGFg".equals(id)) {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))); }
                        catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this, "No video browser available.", Toast.LENGTH_LONG).show(); }
                    }
                }
                return true;
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack(); else finish();
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void playDemo(String assetPath) {
        if (loadingVideo || (videoDialog != null && videoDialog.isShowing())) return;
        loadingVideo = true;
        Toast.makeText(this, "Preparing offline video…", Toast.LENGTH_SHORT).show();
        mediaExecutor.execute(() -> {
            File file = new File(getCacheDir(), assetPath.contains("/cloud/") ? "cloud-demo.mp4" : "ias-demo.mp4");
            try (InputStream in = getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                runOnUiThread(() -> {
                    loadingVideo = false;
                    if (isFinishing() || isDestroyed()) { file.delete(); return; }
                    showVideo(file);
                });
            } catch (Exception e) {
                file.delete();
                runOnUiThread(() -> { loadingVideo = false; if (!isDestroyed()) Toast.makeText(this, "Video unavailable. Add demo.mp4 to the course assets and rebuild.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private void showVideo(File file) {
        videoDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(android.graphics.Color.BLACK);
        videoView = new VideoView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER);
        layout.addView(videoView, params);
        android.widget.Button close = new android.widget.Button(this);
        close.setText("Close video");
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.END);
        layout.addView(close, closeParams);
        close.setOnClickListener(v -> videoDialog.dismiss());
        videoDialog.setContentView(layout);
        videoDialog.setOnDismissListener(d -> { videoView.stopPlayback(); file.delete(); videoView = null; videoDialog = null; });
        videoDialog.show();
        if (videoDialog.getWindow() != null) videoDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setOnPreparedListener(mp -> { videoView.start(); controller.show(3000); });
        videoView.setOnErrorListener((mp, what, extra) -> { Toast.makeText(this, "Unable to play this video. Use H.264/AAC MP4.", Toast.LENGTH_LONG).show(); if(videoDialog!=null)videoDialog.dismiss(); return true; });
        videoView.setVideoPath(file.getAbsolutePath());
    }

    @Override protected void onPause() {
        if (videoView != null && videoView.isPlaying()) videoView.pause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (videoDialog != null) videoDialog.dismiss();
        mediaExecutor.shutdownNow();
        super.onDestroy();
    }
}
