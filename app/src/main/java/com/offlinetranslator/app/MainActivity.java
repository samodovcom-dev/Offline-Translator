
package com.offlinetranslator.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.common.model.DownloadConditions;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;

    private Translator ruEnTranslator;
    private Translator enRuTranslator;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private boolean ttsReady = false;

    private String pendingLanguage = "ru-RU";

    private static final int MICROPHONE_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.loadUrl("file:///android_asset/index.html");

        initializeTranslators();
        initializeTextToSpeech();
    }

    // ПЕРЕВОД

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
                                    sendToJs(
                                            "onModelsReady",
                                            true,
                                            ""
                                    )
                            )
                            .addOnFailureListener(e ->
                                    sendToJs(
                                            "onModelsError",
                                            false,
                                            e.getMessage()
                                    )
                            );

                })
                .addOnFailureListener(e ->
                        sendToJs(
                                "onModelsError",
                                false,
                                e.getMessage()
                        )
                );
    }

    // ОЗВУЧИВАНИЕ

    private void initializeTextToSpeech() {

        textToSpeech = new TextToSpeech(
                this,
                status -> {
                    ttsReady = status == TextToSpeech.SUCCESS;
                }
        );
    }

    private void speak(String text, String language) {

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (!ttsReady || textToSpeech == null) {
            Toast.makeText(
                    this,
                    "Озвучивание ещё загружается",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Locale locale = Locale.forLanguageTag(language);

        int result = textToSpeech.setLanguage(locale);

        if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {

            Toast.makeText(
                    this,
                    "Голос для этого языка не установлен",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "translator_speech"
        );
    }

    // ГОЛОСОВОЙ ВВОД

    private void startListening(String language) {

        pendingLanguage = language;

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    MICROPHONE_PERMISSION
            );

            return;
        }

        startListeningInternal();
    }

    private void startListeningInternal() {

        Toast.makeText(
                this,
                "Проверка голосового ввода...",
                Toast.LENGTH_SHORT
        ).show();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Toast.makeText(
                    this,
                    "Служба распознавания речи недоступна",
                    Toast.LENGTH_LONG
            ).show();

            sendToJs(
                    "onSpeechResult",
                    false,
                    "Служба распознавания речи недоступна"
            );

            return;
        }

        try {

            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this);

            speechRecognizer.setRecognitionListener(
                    new RecognitionListener() {

                        @Override
                        public void onReadyForSpeech(Bundle params) {
                            runOnUiThread(() ->
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Микрофон готов. Говорите!",
                                            Toast.LENGTH_SHORT
                                    ).show()
                            );
                        }

                        @Override public void onBeginningOfSpeech() {}
                        @Override public void onRmsChanged(float rmsdB) {}
                        @Override public void onBufferReceived(byte[] buffer) {}
                        @Override public void onEndOfSpeech() {}

                        @Override
                        public void onError(int error) {
                            sendToJs(
                                    "onSpeechResult",
                                    false,
                                    "Ошибка распознавания: " + error
                            );
                        }

                        @Override
                        public void onResults(Bundle results) {

                            ArrayList<String> matches =
                                    results.getStringArrayList(
                                            SpeechRecognizer.RESULTS_RECOGNITION
                                    );

                            if (matches != null && !matches.isEmpty()) {

                                sendToJs(
                                        "onSpeechResult",
                                        true,
                                        matches.get(0)
                                );

                            } else {

                                sendToJs(
                                        "onSpeechResult",
                                        false,
                                        "Речь не распознана"
                                );
                            }
                        }

                        @Override
                        public void onPartialResults(Bundle results) {}
                        @Override
                        public void onEvent(int eventType, Bundle params) {}
                    }
            );

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    pendingLanguage
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    pendingLanguage
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            speechRecognizer.startListening(intent);

        } catch (Exception e) {

            String error = e.getMessage();

            Toast.makeText(
                    this,
                    "Ошибка микрофона: " + error,
                    Toast.LENGTH_LONG
            ).show();

            sendToJs(
                    "onSpeechResult",
                    false,
                    error
            );
        }
    }

    // РАЗРЕШЕНИЕ МИКРОФОНА

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == MICROPHONE_PERMISSION) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startListeningInternal();

            } else {

                sendToJs(
                        "onSpeechResult",
                        false,
                        "Разрешение на микрофон не предоставлено"
                );
            }
        }
    }

    // JAVASCRIPT BRIDGE

    private class AndroidBridge {

        @JavascriptInterface
        public void translateText(
                String text,
                String direction
        ) {

            if (text == null || text.trim().isEmpty()) {

                sendToJs(
                        "onTranslationError",
                        false,
                        "Введите текст для перевода"
                );

                return;
            }

            Translator translator;

            if ("ru-en".equals(direction)) {
                translator = ruEnTranslator;
            } else {
                translator = enRuTranslator;
            }

            if (translator == null) {

                sendToJs(
                        "onTranslationError",
                        false,
                        "Переводчик ещё не готов"
                );

                return;
            }

            translator.translate(text)
                    .addOnSuccessListener(result ->
                            sendToJs(
                                    "onTranslationSuccess",
                                    true,
                                    result
                            )
                    )
                    .addOnFailureListener(e ->
                            sendToJs(
                                    "onTranslationError",
                                    false,
                                    e.getMessage()
                            )
                    );
        }

        @JavascriptInterface
        public void startListening(String language) {

            runOnUiThread(() ->
                    MainActivity.this.startListening(language)
            );
        }

        @JavascriptInterface
        public void speakText(
                String text,
                String language
        ) {

            runOnUiThread(() ->
                    speak(text, language)
            );
        }
    }

    // ПЕРЕДАЧА РЕЗУЛЬТАТА В HTML

    private void sendToJs(
            String function,
            boolean success,
            String message
    ) {

        if (webView == null) return;

        String safeMessage = JSONObject.quote(
                message == null ? "" : message
        );

        runOnUiThread(() ->
                webView.evaluateJavascript(
                        "if(window." + function + "){" +
                                "window." + function + "(" +
                                success + "," +
                                safeMessage +
                                ");}",
                        null
                )
        );
    }

    // ЗАКРЫТИЕ РЕСУРСОВ

    @Override
    protected void onDestroy() {

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        if (ruEnTranslator != null) {
            ruEnTranslator.close();
        }

        if (enRuTranslator != null) {
            enRuTranslator.close();
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
