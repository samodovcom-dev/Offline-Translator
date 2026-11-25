
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

    private boolean pageLoaded = false;
    private boolean modelsReady = false;
    private String modelError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                pageLoaded = true;
                notifyModelStatus();
            }
        });

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
                            .addOnSuccessListener(unused2 -> {

                                modelsReady = true;
                                modelError = "";

                                notifyModelStatus();
                            })
                            .addOnFailureListener(e -> {

                                modelsReady = false;
                                modelError = getErrorMessage(e);

                                notifyModelStatus();
                            });

                })
                .addOnFailureListener(e -> {

                    modelsReady = false;
                    modelError = getErrorMessage(e);

                    notifyModelStatus();
                });
    }

    private void notifyModelStatus() {

        if (!pageLoaded) {
            return;
        }

        if (modelsReady) {
            sendToJs("onModelsReady", true, "");
        } else if (!modelError.isEmpty()) {
            sendToJs("onModelsError", false, modelError);
        }
    }

    private String getErrorMessage(Exception e) {
        if (e == null || e.getMessage() == null) {
            return "Неизвестная ошибка загрузки моделей";
        }

        return e.getMessage();
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void translateText(String text, String direction) {

            if (text == null || text.trim().isEmpty()) {
                sendToJs(
                        "onTranslationError",
                        false,
                        "Введите текст для перевода"
                );
                return;
            }

            if (!modelsReady) {
                sendToJs(
                        "onTranslationError",
                        false,
                        "Модели перевода ещё не готовы"
                );
                return;
            }

            Translator translator;

            if ("ru-en".equals(direction)) {
                translator = ruEnTranslator;
            } else if ("en-ru".equals(direction)) {
                translator = enRuTranslator;
            } else {
                sendToJs(
                        "onTranslationError",
                        false,
                        "Неизвестное направление перевода"
                );
                return;
            }

            if (translator == null) {
                sendToJs(
                        "onTranslationError",
                        false,
                        "Переводчик недоступен"
                );
                return;
            }

            translator.translate(text)
                    .addOnSuccessListener(result -> {
                        sendToJs(
                                "onTranslationSuccess",
                                true,
                                result
                        );
                    })
                    .addOnFailureListener(e -> {
                        sendToJs(
                                "onTranslationError",
                                false,
                                getErrorMessage(e)
                        );
                    });
        }
    }

    private void sendToJs(
            String function,
            boolean success,
            String message
    ) {

        if (webView == null) {
            return;
        }

        String safeMessage = JSONObject.quote(
                message == null ? "" : message
        );

        runOnUiThread(() -> {

            if (webView == null || !pageLoaded) {
                return;
            }

            String javascript =
                    "if(window." + function + "){" +
                    "window." + function + "(" +
                    success + "," + safeMessage + ");}";

            webView.evaluateJavascript(javascript, null);
        });
    }

    @Override
    protected void onDestroy() {

        if (ruEnTranslator != null) {
            ruEnTranslator.close();
        }

        if (enRuTranslator != null) {
            enRuTranslator.close();
        }

        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
