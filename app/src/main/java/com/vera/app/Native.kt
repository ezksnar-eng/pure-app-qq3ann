package com.vera.app

object Native {
    init { System.loadLibrary("vera-jni") }

    interface TokenCallback { fun onToken(bytes: ByteArray) }

    external fun load(path: String): String
    external fun generate(
        prompt: ByteArray, maxTokens: Int, temp: Float, topP: Float,
        nCtx: Int, threads: Int, cb: TokenCallback
    ): String
    external fun stop()
    external fun unload()
}
