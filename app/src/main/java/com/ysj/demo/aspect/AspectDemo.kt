package com.ysj.demo.aspect

import android.util.Log
import com.ysj.lib.bcu.modifier.aspect.api.Aspect
import com.ysj.lib.bcu.modifier.aspect.api.CallingPoint
import com.ysj.lib.bcu.modifier.aspect.api.POSITION_CALL
import com.ysj.lib.bcu.modifier.aspect.api.POSITION_RETURN
import com.ysj.lib.bcu.modifier.aspect.api.POSITION_START
import com.ysj.lib.bcu.modifier.aspect.api.Pointcut

/**
 * AOP 功能演示。
 *
 * @author Ysj
 * Create time: 2023/10/15
 */
@Aspect
object AspectDemo {

    private const val TAG = "AspectDemo"

    /**
     * [Test] ```<init>```
     */
    @Pointcut(
        target = "class:com/ysj/demo/aspect/Test",
        funName = "<init>",
        position = POSITION_CALL,
    )
    fun testInit(cp: CallingPoint): Test {
        Log.i(TAG, "testInit.")
        return cp.call() as Test
    }

    /**
     * [TJ.aaaa]
     */
    @Pointcut(
        target = "class:com/ysj/demo/aspect/TJ",
        funName = "aaaa",
        position = POSITION_CALL,
    )
    fun testStatic(cp: CallingPoint) {
        Log.i(TAG, "testStatic.")
        cp.call()
    }

    /**
     * [MainActivity.test1]
     */
    @Pointcut(
        target = "class:com/ysj/demo/aspect/MainActivity",
        funName = "test1",
        position = POSITION_START,
    )
    fun test1onStart() {
        Log.i(TAG, "test1onStart.")
    }

    /**
     * [MainActivity.test1]
     */
    @Pointcut(
        target = "class:.*/MainActivity",
        funName = "test1",
        position = POSITION_RETURN,
    )
    fun test1onReturn() {
        Log.i(TAG, "test1onReturn.")
    }

    /**
     * [MainActivity.test2] (str: String)
     */
    @Pointcut(
        target = "class:.*/MainActivity",
        funName = "test2",
        funDesc = "\\(Ljava/lang/String;\\)V",
        position = POSITION_CALL,
    )
    fun test2Str(cp: CallingPoint) {
        // 演示使用自定义参数调用源方法
        val arg = cp.args.first() as String
        cp.call("$arg ha ha!")
    }

    /**
     * [MainActivity.test2]  (num: Int)
     */
    @Pointcut(
        target = "class:.*/MainActivity",
        funName = "test2",
        funDesc = "\\(I\\)V",
        position = POSITION_CALL,
    )
    fun test2Num(cp: CallingPoint) {
        Log.i(TAG, "test2Num: ${cp.args.contentToString()}")
        // 演示不执行 test2(num: Int) 方法
        // cp.call()
    }

    /**
     * [MainActivity.test3]
     */
    @Pointcut(
        target = "class:.*/MainActivity",
        funName = "test3",
        funDesc = "\\(II\\)I",
        position = POSITION_CALL,
    )
    fun test3(cp: CallingPoint): Int {
        // 演示返回自定义的值
        val result = cp.call() as Int
        return result + 1
    }


    /**
     * [MainActivity.test4]
     */
    @Pointcut(
        target = "class:.*/MainActivity",
        funName = "test4",
        funDesc = "\\(\\[Ljava/lang/String;\\)V",
        position = POSITION_CALL,
    )
    fun test4(cp: CallingPoint) {
        val varArgs = cp.args.first() as Array<*>
        Log.i(TAG, "test4: ${varArgs.contentToString()}")
        cp.call()
    }

}