/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import android.graphics.Color;
import android.app.Activity;
import android.view.View;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import android.util.Log;
import android.view.WindowManager;

public abstract class PocketSphinxActivity extends Activity implements
        RecognitionListener {
		
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "COLORS";
    private static final String GRAM_SEARCH = "COLORS";

    private SpeechRecognizer recognizer = null;
    public static boolean appInFront = false;
    public static boolean randWithVad = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override
    protected void onResume()  {
        super.onResume();
        appInFront = true;
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    makeText(getApplicationContext(), "Failed to init recognizer " + result, Toast.LENGTH_SHORT).show();
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    protected void onPause() {
        DEBUG("onPause");
        appInFront = false;
        destroyRecognizer();
        super.onPause();
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text =  "'"+hypothesis.getHypstr().trim()+"' "+hypothesis.getProb()+"/"+hypothesis.getBestScore();
        DEBUG("onPartialResult: " + text);
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {

        if (hypothesis != null) {
            String text = hypothesis.getHypstr().trim();

            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            try
            {
                text = text.split(" ")[2];
                int color = parseColor(text);
                if (color != 0 ) drawLights(color);
                DEBUG("onResult: " + text + " " + hypothesis.getBestScore());
            }catch (Exception err)
            {
                ERROR(err.getMessage());
            }
        }
    }
    public void enableVad(boolean enable){
        DEBUG("enableVad:" + enable);
        randWithVad = enable;
    }
    public void drawLights(int color){
        DEBUG("randomLights:"+color);
    }
    @Override
    public void onBeginningOfSpeech() {
        DEBUG("onBeginningOfSpeech:");
        if (randWithVad)
            drawLights(0);
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        DEBUG("onEndOfSpeech:");
        if (randWithVad)
            drawLights(0);
        if (recognizer!=null)
            switchSearch(KWS_SEARCH);
    }

    private void switchSearch(String searchName) {
        DEBUG("switchSearch:" + searchName);
        if (recognizer!=null) {
            if (appInFront == false) {
                //App is in background stop recognizer();
                destroyRecognizer();
            }else {
                //App is in forground restart search
                restartRecognizer(searchName);
            }
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them
        DEBUG("setupRecognizer:" + recognizer);
        if (recognizer == null) {
            recognizer = defaultSetup()
                    .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                    .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                            //.setDictionary(new File(assetsDir, "cmudict-colors-en-us.dict"))

                            // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                            //.setRawLogDir(assetsDir)

                            // Threshold to tune for keyphrase to balance between false alarms and misses
                    .setKeywordThreshold(1e-20f)

                            // Use context-independent phonetic search, context-dependent is too slow for mobile
                    .setBoolean("-allphone_ci", true)

                    .getRecognizer();
            recognizer.addListener(this);

            //http://cmusphinx.sourceforge.net/wiki/tutorialandroid#sample_application

            /** In your application you might not need to add all those searches.
             * They are added here for demonstration. You can leave just one.
             */

//            File colorDic = new File(assetsDir, "colors-kws.gram");
//            recognizer.addKeywordSearch(KWS_SEARCH, colorDic);

            // Create grammar-based search for colors recognition
            File digitsGrammar = new File(assetsDir, "colors.gram");
            recognizer.addGrammarSearch(GRAM_SEARCH, digitsGrammar);
        }
    }

    private void restartRecognizer(String searchName)
    {
        if (recognizer != null) {
            recognizer.stop();
            recognizer.startListening(searchName);
        }
    }

    private void destroyRecognizer()
    {
        DEBUG("destroyRecognizer:" + recognizer);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
            recognizer = null;
        }
    }
    @Override
    public void onError(Exception error) {
        makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    private static void DEBUG(String msg)
    {
        Log.d("Sphinx", msg);
    }
    private static void ERROR(String msg) { Log.e("Sphinx", msg); }

    private int parseColor(String colorName) throws Exception
    {
        int result = 0;
        try
        {
            result = getResources().getColor(getResources().getIdentifier(colorName ,"color",getPackageName()));
        }catch (Exception err)
        {
            ERROR(err.getMessage());
            result = Color.parseColor(colorName);
        }
        return result;
    }

}
