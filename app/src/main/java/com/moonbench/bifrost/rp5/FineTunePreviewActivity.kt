package com.moonbench.bifrost.rp5

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FineTunePreviewActivity : AppCompatActivity() {
    private lateinit var editor: ThumbstickViewAreaEditorView
    private lateinit var preview: ThumbstickLivePreview
    private lateinit var store: FineTuneStore

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = FineTuneStore(this)
        val name = intent.getStringExtra(EXTRA_PROFILE_NAME)
        val profile = name?.let(store::find) ?: run { finish(); return }

        preview = ThumbstickLivePreview(
            this,
            { frame -> editor.setPreviewFrame(frame) },
            { left, right -> editor.setSampledColors(left, right) },
            beforeCapture = { window.decorView.alpha = 0f },
            afterCapture = { window.decorView.alpha = 1f },
        )
        editor = ThumbstickViewAreaEditorView(this)
        editor.setRegions(profile.leftViewArea, profile.rightViewArea)
        editor.onPreviewRequested = { left, right -> preview.preview(left, right, profile.colourMode) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@FineTunePreviewActivity).apply {
                text = "FINE TUNE PREVIEW • " + profile.name
                setTextColor(-1)
                textSize = 16f
                setPadding(12, 12, 12, 12)
            })
            addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(Button(this@FineTunePreviewActivity).apply { text = "EXIT"; setOnClickListener { finish() } })
        })
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) {
            preview.start()
            store.find(intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty())?.let {
                preview.preview(it.leftViewArea, it.rightViewArea, it.colourMode)
            }
        }
    }

    override fun onPause() {
        if (::preview.isInitialized) preview.stop()
        window.decorView.alpha = 1f
        super.onPause()
    }

    companion object { const val EXTRA_PROFILE_NAME = "fine_tune_profile_name" }
}
