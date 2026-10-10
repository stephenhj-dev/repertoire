package app.oslo.tivimate.guide.remote

internal object Keys {
    const val HOME = 3
    const val BACK = 4
    const val DIGIT_0 = 7
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val VOLUME_MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val CAPTIONS = 175
    const val MEDIA_AUDIO_TRACK = 222

    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3

    const val TIVIMATE = "ar.tvplayer.tv"

    fun digit(number: Int): Int = DIGIT_0 + number
}

internal object Features {
    const val PING = 1
    const val KEY = 2
    const val IME = 4
    const val POWER = 32
    const val VOLUME = 64
    const val APP_LINK = 512
    const val WANTED = PING or KEY or IME or POWER or VOLUME or APP_LINK
}
