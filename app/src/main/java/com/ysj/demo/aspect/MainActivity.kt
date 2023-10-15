package com.ysj.demo.aspect

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.ysj.demo.aspect.click.ClickInterval
import com.ysj.demo.aspect.databinding.ActivityMainBinding

/**
 * 演示 [modifier-aspect]。
 *
 * @author Ysj
 * Create time: 2023/8/31
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        vb.btnClickInterval.setOnClickListener {
            testClickInterval()
        }
        vb.btnAopDemo.setOnClickListener {
            onAopDemoClicked()
        }
    }

    @ClickInterval
    private fun testClickInterval() {
        Log.i(TAG, "testClickInterval")
    }

    private fun onAopDemoClicked() {
        test1()
        test2(12)
        test2("hello world")
        Log.i(TAG, "onAopDemoClicked: ${test3(1, 2)}")
    }

    private fun test1() {
        Log.i(TAG, "test1.")
    }

    private fun test2(str: String) {
        Log.i(TAG, "test2: $str")
    }


    private fun test2(num: Int) {
        Log.i(TAG, "test2: $num")
    }

    private fun test3(a: Int, b: Int): Int {
        return a + b
    }

}