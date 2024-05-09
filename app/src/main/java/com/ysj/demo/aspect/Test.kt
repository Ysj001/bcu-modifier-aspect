package com.ysj.demo.aspect

import android.util.Log

/**
 *
 *
 * @author Ysj
 * Create time: 2024/3/25
 */
class Test {

    companion object {
        private const val TAG = "Test"
    }

    private val a: Int
    private val b: String

    constructor() : this(0)
    constructor(a: Int) : this(a, "")
    constructor(a: Int, b: String) {
        this.a = a
        this.b = b
    }

//    constructor()
//    constructor(a: Int)
//    constructor(a: Int, b: String)

    fun test() {
        Log.i(TAG, "test. a=$a , b=$b")
    }
}