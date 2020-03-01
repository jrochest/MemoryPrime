package com.md

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.Menu
import com.md.modesetters.*


class SpacedRepeaterActivity : Activity(), TapUiHandler {
    private var toneGenerator: ToneGenerator? = null
    private var mRemoteControlResponder: ComponentName? = null
    private var mAudioManager: AudioManager? = null


    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DbContants.setup(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        val activityHelper = ActivityHelper()
        activityHelper.commonActivitySetup(this)
        // Normal mode.
        CreateModeSetter.getInstance().setUp(this, modeHand)
        BrowsingModeSetter.getInstance().setup(this, modeHand)
        DeckChooseModeSetter.getInstance().setUp(this, modeHand)
        LearningModeSetter.getInstance().setUp(this, modeHand)
        DeckChooseModeSetter.getInstance().setupMode(this)
        SettingModeSetter.getInstance().setup(this, modeHand)
        CleanUpAudioFilesModeSetter.getInstance().setup(this, modeHand)
        mAudioManager = this.getSystemService(
                Context.AUDIO_SERVICE) as AudioManager
        startService(Intent(this, PlayerService::class.java))

        mediaBrowser = MediaBrowserCompat(
        this,
        ComponentName(this, PlayerService::class.java),
        connectionCallbacks,
        null)

    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {

            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->

                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(
                        this@SpacedRepeaterActivity, // Context
                        token
                )

                // Save the controller
                MediaControllerCompat.setMediaController(this@SpacedRepeaterActivity, mediaController)

                mediaController.transportControls.play()
                // Register a Callback to stay in sync
                mediaController.registerCallback(controllerCallback)
            }
        }
    }

    lateinit var mediaBrowser: MediaBrowserCompat

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {}

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {}
    }

    var modeHand = ModeHandler(this)
    override fun onBackPressed() {
        modeHand.goBack()
        return
    }

    override fun onStart() {
        super.onStart()
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 1)
    }

    override fun onStop() {
        super.onStop()
        if (toneGenerator != null) {
            toneGenerator!!.release()
            toneGenerator = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val activityHelper = ActivityHelper()
        activityHelper.createCommonMenu(menu, this)
        return true
    }

    fun makeDialog(message: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(message)
        builder.create()
        builder.show()
    }

    private fun isFromMemprimeDevice(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) {
            return false
        }
        val device = event.device ?: return false
        if (isFromMultiButtonMemprimeDevice(keyCode, event)) {
            return true
        }
        val name = device.name
        return name.contains("AB Shutter3") ||
                name.contains("AK LIFE BT") ||
                name.contains("BLE") ||
                name.contains("BR301") ||
                name.contains("memprime") ||
                name.contains("STRIM-BTN10") ||  // MARREX.
                name.contains("Button Jack") ||
                name.contains("PhotoShot") // Wide flat one
    }

    private fun isFromMultiButtonMemprimeDevice(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) {
            return false
        }
        val device = event.device ?: return false
        val name = device.name
        return name.contains("Shutter Camera")
    }

    private var mPressGroupLastPressMs: Long = 0
    private var mPressGroupLastPressEventMs: Long = 0
    private var mPressGroupCount = 0
    private var mPressSequenceNumber = 0
    private var hasAudioFocus = false
    private val afListener = OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> hasAudioFocus = false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        println("TODOJ key up event$event")
        val modeSetter = modeHand.whoseOnTop()
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyUp(keyCode, event)
        }
        println("TODOJ event$event")
        return if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            super.onKeyUp(keyCode, event)
        } else true
    }

    /**
     * We pay more attention to down events because for some reason they are much more likely
     * to be sent. At least that's true on the AK life BT shutters. Perhaps it's acting like a stuck
     * press. There's a repeat count:
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        println("TODOJ key down event$event")
        val modeSetter = modeHand.whoseOnTop()
        println("TODOJ event$event")
        // BR301 sends an enter command, which we want to ignore.
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true
        }
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyDown(keyCode, event)
        }
        if (modeSetter == null || !isFromMemprimeDevice(keyCode, event)) {
            return super.onKeyDown(keyCode, event)
        }
        if (modeSetter !is LearningModeSetter) {
            LearningModeSetter.getInstance().setupMode(this)
            return true
        }
        val eventTimeMs = event.eventTime
        return handleRhythmUiTaps(modeSetter, eventTimeMs, PRESS_GROUP_MAX_GAP_MS_BLUETOOTH, false)
    }

    override fun handleRhythmUiTaps(modeSetter: ModeSetter, eventTimeMs: Long, pressGroupMaxGapMs: Long, isBluetoothDoublePress: Boolean): Boolean {
        val currentTimeMs = SystemClock.uptimeMillis()
        if (mPressGroupLastPressMs == 0L) {
            mPressGroupCount = if (isBluetoothDoublePress) 2 else 1
            println("New Press group.")
        } else if (mPressGroupLastPressEventMs + pressGroupMaxGapMs < eventTimeMs) { // Too much time has ellapsed start a new press group.
            mPressGroupCount = if (isBluetoothDoublePress) 2 else 1 // Count the first double press, but not the subsequent.
            println("New Press group. Expiring old one.")
        } else {
            println("Time diff: " + (currentTimeMs - mPressGroupLastPressMs))
            println("Time diff event time: " + (eventTimeMs - mPressGroupLastPressEventMs))
            mPressGroupCount++
            println("mPressGroupCount++. $mPressGroupCount")
        }
        mPressGroupLastPressEventMs = eventTimeMs
        mPressGroupLastPressMs = currentTimeMs
        mPressSequenceNumber++
        val currentSequenceNumber = mPressSequenceNumber
        // Don't wait to handle 8 toggle focus.
        if (mPressGroupCount == 8) {
            maybeChangeAudioFocus(!hasAudioFocus)
            return true
        }
        // Don't let anything beyond eight go through. This avoid continually toggling audiofocus.
        if (mPressGroupCount > 8) {
            return true
        }
        Handler().postDelayed(Runnable {
            if (mPressSequenceNumber != currentSequenceNumber) {
                return@Runnable
            }
            println("TODOJ received actual count $mPressGroupCount")
            when (mPressGroupCount) {
                1 -> modeSetter.handleReplay()
                2 -> modeSetter.proceed()
                3 -> modeSetter.proceedFailure()
                4 -> modeSetter.undo()
                5 ->  // Reset!
                    modeSetter.setupMode(this@SpacedRepeaterActivity)
                6 -> modeSetter.toggleDim()
                7 -> modeSetter.mark()
                else -> {
                }
            }
        }, pressGroupMaxGapMs)
        return true
    }

    fun maybeChangeAudioFocus(shouldHaveFocus: Boolean) {
        if (hasAudioFocus == shouldHaveFocus) { // The audiofocus matches request already.
            return
        }
        val audioManager = this.getSystemService(
                Context.AUDIO_SERVICE) as AudioManager
        val mPlaybackAttributes = AudioAttributes.Builder().setUsage(
                AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val mFocusRequest = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(mPlaybackAttributes)
                .setOnAudioFocusChangeListener(afListener).build()
        hasAudioFocus = if (shouldHaveFocus) {
            val res = audioManager.requestAudioFocus(mFocusRequest)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                true
            } else {
                false
            }
        } else {
            audioManager.abandonAudioFocus(afListener)
            false
        }
    }

    fun hasAudioFocus(): Boolean {
        return hasAudioFocus
    }

    fun keepHeadphoneAlive() {
        if (toneGenerator == null) {
            return
        }
        // keep the headphones turned on by playing an almost silent sound n seconds.
        toneGenerator!!.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE,  /* Two minutes */1000 * 60 * 2)
    }

    fun maybeDim() {
        val modeSetter = modeHand.whoseOnTop()
        modeSetter.toggleDim()
    }

    companion object {
        private const val LOG_TAG = "SpacedRepeater"
        const val PRESS_GROUP_MAX_GAP_MS_BLUETOOTH = 400L
        // Jacob can consistently press every 180ms. With training we can probably drop this down.
// But on cold days 250 is hard to achieve.
        const val PRESS_GROUP_MAX_GAP_MS_SCREEN = 300L
    }
}