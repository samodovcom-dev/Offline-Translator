package com.offlinetranslator.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.DownloadConditions;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private Translator ruEnTranslator;
    private Translator enRuTranslator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.loadUrl("file:///android_asset/index.html");

        initializeTranslators();
    }

    private void initializeTranslators() {

        TranslatorOptions ruEnOptions =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.RUSSIAN)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build();

        TranslatorOptions enRuOptions =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.RUSSIAN)
                        .build();

        ruEnTranslator = Translation.getClient(ruEnOptions);
        enRuTranslator = Translation.getClient(enRuOptions);

        DownloadConditions conditions =
                new DownloadConditions.Builder().build();

        ruEnTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    enRuTranslator.downloadModelIfNeeded(conditions)
                            .addOnSuccessListener(unused2 ->
                                    runOnUiThread(() ->
                                            sendToJs("onModelsReady", true, "")
                                    )
                            )
                            .addOnFailureListener(e ->
                                    sendToJs("onModelsError", false,
                                            e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        sendToJs("onModelsError", false, e.getMessage())
                );
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void translateText(String text, String direction) {

            if (text == null || text.trim().isEmpty()) {
                sendToJs("onTranslationError", false,
                        "Введите текст для перевода");
                return;
            }

            Translator translator;

            if ("ru-en".equals(direction)) {
                translator = ruEnTranslator;
            } else {
                translator = enRuTranslator;
            }

            if (translator == null) {
                sendToJs("onTranslationError", false,
                        "Переводчик ещё не готов");
                return;
            }

            translator.translate(text)
                    .addOnSuccessListener(result ->
                            sendToJs("onTranslationSuccess", true, result)
                    )
                    .addOnFailureListener(e ->
                            sendToJs("onTranslationError", false,
                                    e.getMessage())
                    );
        }
    }

    private void sendToJs(String function, boolean success, String message) {
        if (webView == null) return;

        String safeMessage = JSONObject.quote(
                message == null ? "" : message
        );

        runOnUiThread(() -> webView.evaluateJavascript(
                "if(window." + function + "){" +
                        "window." + function + "(" +
                        success + "," + safeMessage + ");}",
                null
        ));
    }

    @Override
    protected void onDestroy() {
        if (ruEnTranslator != null) ruEnTranslator.close();
        if (enRuTranslator != null) enRuTranslator.close();

        if (webView != null) webView.destroy();

        super.onDestroy();
    }
}
