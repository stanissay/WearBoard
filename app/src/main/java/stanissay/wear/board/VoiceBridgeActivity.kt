package stanissay.wear.board

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.core.content.edit

class VoiceBridgeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent.getStringExtra(MainConstants.EXTRA_LANGUAGE) ?: "uk-UA"

        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                language
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                language
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.listen)
            )
        }

        try {
            startActivityForResult(voiceIntent, MainConstants.REQUEST_VOICE)
        } catch (_: ActivityNotFoundException) { finish() }
    }

    @Deprecated("Using Activity Result API is unnecessary here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == MainConstants.REQUEST_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

            if (!text.isNullOrEmpty()) {
                getSharedPreferences(MainConstants.PREFS, MODE_PRIVATE).edit {
                    putString(MainConstants.RESULT_TEXT, text)
                }
            }
        }

        finish()
    }
}