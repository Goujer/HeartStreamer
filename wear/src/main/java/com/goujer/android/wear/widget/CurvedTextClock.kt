package com.goujer.android.wear.widget

import android.content.*
import android.database.ContentObserver
import android.icu.text.DateTimePatternGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.ViewDebug.ExportedProperty
import androidx.wear.widget.CurvedTextView
import java.util.*

//Based on API 23 with some API 31 influence
open class CurvedTextClock: CurvedTextView {

	private var mFormat12: CharSequence? = null
	private var mFormat24: CharSequence? = null
	private var mDescFormat12: CharSequence? = null
	private var mDescFormat24: CharSequence? = null

	@ExportedProperty
	private var mFormat: CharSequence? = null

	@ExportedProperty
	private var mHasSeconds = false

	private var mDescFormat: CharSequence? = null

	private var mAttached = false

	private var mTime: Calendar? = null
	private var mTimeZone: String? = null

	private var mShowCurrentUserTime = false

	private val mFormatChangeObserver: ContentObserver = object : ContentObserver(Handler(Looper.myLooper()?: Looper.getMainLooper())) {
		override fun onChange(selfChange: Boolean) {
			chooseFormat()
			onTimeChanged()
		}

		override fun onChange(selfChange: Boolean, uri: Uri?) {
			chooseFormat()
			onTimeChanged()
		}
	}

	private val mIntentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (mTimeZone == null && Intent.ACTION_TIMEZONE_CHANGED == intent.action) {
				val timeZone = intent.getStringExtra("time-zone")
				createTime(timeZone)
			}
			onTimeChanged()
		}
	}

	private val mTicker: Runnable = object : Runnable {
		override fun run() {
			onTimeChanged()
			val now = SystemClock.uptimeMillis()
			val next = now + (1000 - now % 1000)
			handler.postAtTime(this, next)
		}
	}

	constructor(context: Context): super(context) {
		init()
	}

	constructor(context: Context, attrs: AttributeSet): this(context, attrs, android.R.attr.textViewStyle)

	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)

	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes) {
		//val a = context.obtainStyledAttributes(attrs, android.R.styleable.TextClock, defStyleAttr, defStyleRes)
		//if (Build.VERSION.SDK_INT >= 29 ) {
		//	saveAttributeDataForStyleable(context, android.R.styleable.TextClock, attrs, a, defStyleAttr, defStyleRes)
		//}
		//try {
		//	mFormat12 = a.getText(android.R.styleable.TextClock_format12Hour)
		//	mFormat24 = a.getText(android.R.styleable.TextClock_format24Hour)
		//	mTimeZone = a.getString(android.R.styleable.TextClock_timeZone)
		//} finally {
		//	a.recycle()
		//}
		init()
	}
	private fun init() {
		if (mFormat12 == null) {
			mFormat12 = getBestDateTimePattern("hm")
		}
		if (mFormat24 == null) {
			mFormat24 = getBestDateTimePattern("Hm")
		}
		createTime(mTimeZone)
		// Wait until onAttachedToWindow() to handle the ticker
		chooseFormat(false)
	}

	private fun createTime(timeZone: String?) {
		mTime = if (timeZone != null) {
			Calendar.getInstance(TimeZone.getTimeZone(timeZone))
		} else {
			Calendar.getInstance()
		}
	}

	/**
	 * Returns the formatting pattern used to display the date and/or time
	 * in 12-hour mode. The formatting pattern syntax is described in
	 * [DateFormat].
	 *
	 * @return A [CharSequence] or null.
	 *
	 * @see .setFormat12Hour
	 * @see .is24HourModeEnabled
	 */
	@ExportedProperty
	open fun getFormat12Hour(): CharSequence? {
		return mFormat12
	}

	/**
	 *
	 * Specifies the formatting pattern used to display the date and/or time
	 * in 12-hour mode. The formatting pattern syntax is described in
	 * [DateFormat].
	 *
	 *
	 * If this pattern is set to null, [.getFormat24Hour] will be used
	 * even in 12-hour mode. If both 24-hour and 12-hour formatting patterns
	 * are set to null, the default pattern for the current locale will be used
	 * instead.
	 *
	 *
	 * **Note:** if styling is not needed, it is highly recommended
	 * you supply a format string generated by
	 * [DateFormat.getBestDateTimePattern]. This method
	 * takes care of generating a format string adapted to the desired locale.
	 *
	 *
	 * @param format A date/time formatting pattern as described in [DateFormat]
	 *
	 * @see .getFormat12Hour
	 * @see .is24HourModeEnabled
	 * @see DateFormat.getBestDateTimePattern
	 * @see DateFormat
	 *
	 *
	 * @attr ref android.R.styleable#TextClock_format12Hour
	 */
	open fun setFormat12Hour(format: CharSequence?) {
		mFormat12 = format
		chooseFormat()
		onTimeChanged()
	}

	/**
	 * Like setFormat12Hour, but for the content description.
	 * @hide
	 */
	open fun setContentDescriptionFormat12Hour(format: CharSequence?) {
		mDescFormat12 = format
		chooseFormat()
		onTimeChanged()
	}

	/**
	 * Returns the formatting pattern used to display the date and/or time
	 * in 24-hour mode. The formatting pattern syntax is described in
	 * [DateFormat].
	 *
	 * @return A [CharSequence] or null.
	 *
	 * @see .setFormat24Hour
	 * @see .is24HourModeEnabled
	 */
	@ExportedProperty
	open fun getFormat24Hour(): CharSequence? {
		return mFormat24
	}

	/**
	 *
	 * Specifies the formatting pattern used to display the date and/or time
	 * in 24-hour mode. The formatting pattern syntax is described in
	 * [DateFormat].
	 *
	 *
	 * If this pattern is set to null, [.getFormat24Hour] will be used
	 * even in 12-hour mode. If both 24-hour and 12-hour formatting patterns
	 * are set to null, the default pattern for the current locale will be used
	 * instead.
	 *
	 *
	 * **Note:** if styling is not needed, it is highly recommended
	 * you supply a format string generated by
	 * [DateFormat.getBestDateTimePattern]. This method
	 * takes care of generating a format string adapted to the desired locale.
	 *
	 * @param format A date/time formatting pattern as described in [DateFormat]
	 *
	 * @see .getFormat24Hour
	 * @see .is24HourModeEnabled
	 * @see DateFormat.getBestDateTimePattern
	 * @see DateFormat
	 *
	 *
	 * @attr ref android.R.styleable#TextClock_format24Hour
	 */
	open fun setFormat24Hour(format: CharSequence?) {
		mFormat24 = format
		chooseFormat()
		onTimeChanged()
	}

	/**
	 * Like setFormat24Hour, but for the content description.
	 * @hide
	 */
	open fun setContentDescriptionFormat24Hour(format: CharSequence?) {
		mDescFormat24 = format
		chooseFormat()
		onTimeChanged()
	}

	/**
	 * Sets whether this clock should always track the current user and not the user of the
	 * current process. This is used for single instance processes like the systemUI who need
	 * to display time for different users.
	 *
	 * @hide
	 */
	open fun setShowCurrentUserTime(showCurrentUserTime: Boolean) {
		mShowCurrentUserTime = showCurrentUserTime
		chooseFormat()
		onTimeChanged()
		unregisterObserver()
		registerObserver()
	}

	/**
	 * Indicates whether the system is currently using the 24-hour mode.
	 *
	 * When the system is in 24-hour mode, this view will use the pattern
	 * returned by [.getFormat24Hour]. In 12-hour mode, the pattern
	 * returned by [.getFormat12Hour] is used instead.
	 *
	 * If either one of the formats is null, the other format is used. If
	 * both formats are null, the default formats for the current locale are used.
	 *
	 * @return true if time should be displayed in 24-hour format, false if it
	 * should be displayed in 12-hour format.
	 *
	 * @see .setFormat12Hour
	 * @see .getFormat12Hour
	 * @see .setFormat24Hour
	 * @see .getFormat24Hour
	 */
	open fun is24HourModeEnabled(): Boolean {
		return DateFormat.is24HourFormat(context)
	}

	/**
	 * Indicates which time zone is currently used by this view.
	 *
	 * @return The ID of the current time zone or null if the default time zone,
	 * as set by the user, must be used
	 *
	 * @see TimeZone
	 *
	 * @see java.util.TimeZone.getAvailableIDs
	 * @see .setTimeZone
	 */
	open fun getTimeZone(): String? {
		return mTimeZone
	}

	/**
	 * Sets the specified time zone to use in this clock. When the time zone
	 * is set through this method, system time zone changes (when the user
	 * sets the time zone in settings for instance) will be ignored.
	 *
	 * @param timeZone The desired time zone's ID as specified in [TimeZone]
	 * or null to user the time zone specified by the user
	 * (system time zone)
	 *
	 * @see .getTimeZone
	 * @see java.util.TimeZone.getAvailableIDs
	 * @see TimeZone.getTimeZone
	 * @attr ref android.R.styleable#TextClock_timeZone
	 */
	open fun setTimeZone(timeZone: String?) {
		mTimeZone = timeZone
		createTime(timeZone)
		onTimeChanged()
	}

	/**
	 * Selects either one of [.getFormat12Hour] or [.getFormat24Hour]
	 * depending on whether the user has selected 24-hour format.
	 *
	 * Calling this method does not schedule or unschedule the time ticker.
	 */
	private fun chooseFormat() {
		chooseFormat(true)
	}

	/**
	 * Returns the current format string. Always valid after constructor has
	 * finished, and will never be `null`.
	 *
	 * @hide
	 */
	open fun getFormat(): CharSequence? {
		return mFormat
	}

	/**
	 * Selects either one of [.getFormat12Hour] or [.getFormat24Hour]
	 * depending on whether the user has selected 24-hour format.
	 *
	 * @param handleTicker true if calling this method should schedule/unschedule the
	 * time ticker, false otherwise
	 */
	private fun chooseFormat(handleTicker: Boolean) {
		val format24Requested = is24HourModeEnabled()
		if (format24Requested) {
			mFormat = abc(mFormat24, mFormat12, getBestDateTimePattern("Hm"))
			mDescFormat = abc(mDescFormat24, mDescFormat12, mFormat)
		} else {
			mFormat = abc(mFormat12, mFormat24, getBestDateTimePattern("hm"))
			mDescFormat = abc(mDescFormat12, mDescFormat24, mFormat)
		}
		val hadSeconds = mHasSeconds
		mHasSeconds = hasSeconds(mFormat)
		if (handleTicker && mAttached && hadSeconds != mHasSeconds) {
			if (hadSeconds) handler.removeCallbacks(mTicker) else mTicker.run()
		}
	}

	private fun getBestDateTimePattern(skeleton: String): String? {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			val dtpg = DateTimePatternGenerator.getInstance(context.resources.configuration.locales[0])
			dtpg.getBestPattern(skeleton)
		} else {
			DateFormat.getBestDateTimePattern(context.resources.configuration.locale, skeleton)
		}
	}


	/**
	 * Indicates whether the specified format string contains seconds.
	 *
	 * Always returns false if the input format is null.
	 *
	 * @param inFormat the format string, as described in {@link android.text.format.DateFormat}
	 *
	 * @return true if the format string contains {@link #SECONDS}, false otherwise
	 *
	 * @hide
	 */
	open fun hasSeconds(inFormat: CharSequence?): Boolean {
		if (inFormat == null) return false
		val length = inFormat.length
		var insideQuote = false
		for (i in 0 until length) {
			val c = inFormat[i]
			if (c == '\'') {
				insideQuote = !insideQuote
			} else if (!insideQuote) {
				if (c == 's') {
					return true
				}
			}
		}
		return false
	}

	/**
	 * Returns a if not null, else return b if not null, else return c.
	 */
	private fun abc(a: CharSequence?, b: CharSequence?, c: CharSequence?): CharSequence? {
		return a ?: (b ?: c)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		if (!mAttached) {
			mAttached = true
			registerReceiver()
			registerObserver()
			createTime(mTimeZone)
			if (mHasSeconds) {
				mTicker.run()
			} else {
				onTimeChanged()
			}
		}
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		if (mAttached) {
			unregisterReceiver()
			unregisterObserver()
			handler.removeCallbacks(mTicker)
			mAttached = false
		}
	}

	private fun registerReceiver() {
		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_TIME_TICK)
		filter.addAction(Intent.ACTION_TIME_CHANGED)
		filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
		context.registerReceiver(mIntentReceiver, filter, null, handler)
	}

	private fun registerObserver() {
		val resolver = context.contentResolver
		resolver.registerContentObserver(Settings.System.CONTENT_URI, true, mFormatChangeObserver)
	}

	private fun unregisterReceiver() {
		context.unregisterReceiver(mIntentReceiver)
	}

	private fun unregisterObserver() {
		val resolver = context.contentResolver
		resolver.unregisterContentObserver(mFormatChangeObserver)
	}

	private fun onTimeChanged() {
		mTime!!.timeInMillis = System.currentTimeMillis()
		text = DateFormat.format(mFormat, mTime) as String?
		contentDescription = DateFormat.format(mDescFormat, mTime)
	}
}