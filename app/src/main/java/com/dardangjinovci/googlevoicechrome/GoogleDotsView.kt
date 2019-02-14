package com.dardangjinovci.googlevoicechrome

import android.animation.Animator
import android.animation.FloatEvaluator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import java.util.*

/**
 * Created by Dardan on: 15/02/2018
 */

const val TAG = "GoogleDotsView"

class GoogleDotsView : View {

    private val dotsManager = DotsManager(this)
    var state: State
        get() = dotsManager.state
        set(state) {
            Log.d(TAG, "new state: $state")
            dotsManager.state = state
        }

    var rms: Float
        get() = dotsManager.rms
        set(value) {
            dotsManager.rms = value
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            dotsManager.init(measuredWidth, measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        dotsManager.draw(canvas)
    }

}

enum class State {
    UNKNOWN,
    IDLE,
    LISTENING,
    USER_SPEAKS,
    THINKING,
    REPLYING,
    INCOMPREHENSION
}

internal class DotsManager(private val container: View) {

    private val RADIAN = 6.28319
    private val cells = 9
    private var tileWidth = 0f
    private var tileHeight = 0f

    internal var rms = 0f
    private val rotationAngle = Math.toRadians(3.0)

    private var animator: ValueAnimator? = null
    private val center = PointF()
    private val size = Point()
    var state: State = State.UNKNOWN
        set(state) {

            if (field == state) return
            field = state

            stopAnimator()

            when (state) {
                State.IDLE -> idleState()
                State.USER_SPEAKS -> userSpeakState()
                State.THINKING -> thinkState()
                State.REPLYING -> replyState()
                State.LISTENING -> listenState()
                State.INCOMPREHENSION -> incomprehensionState()
                else -> {
                }
            }
        }

    private val dots = arrayOf(
            Dot(Color.parseColor("#4285F4")), //Blue
            Dot(Color.parseColor("#EA4335")), //Red
            Dot(Color.parseColor("#FBBC05")), //Yellow
            Dot(Color.parseColor("#34A853"))//Green
    )


    fun init(x: Int, y: Int) {
        size.set(x, y)

        tileWidth = size.x.toFloat() / cells
        tileHeight = size.y.toFloat() / cells


        val halfDivisor = cells / 2f

        val cx = halfDivisor * tileWidth - (tileWidth / 2)
        val cy = halfDivisor * tileHeight - (tileHeight / 2)
        center.set(cx, cy)


        for (d in dots) {
            d.size = tileWidth
        }

        state = State.IDLE
    }

    private fun idleState() {

        stopAnimator()


        val prevDots = Arrays.copyOf(dots, dots.size)

        //positions are set based on tile width/height, whatever size is set of the view
        //these positions will apply for that size
        val positions = arrayOf(
                PointF(tileWidth * 2, tileHeight * 2),//Blue
                PointF(tileWidth * 5, tileHeight * 4.3f), //Red
                PointF(tileWidth * 5, tileHeight * 5.7f), //Yellow
                PointF(tileWidth * 6, tileHeight * 3)) //Green

        val scales = floatArrayOf(
                3f, //Blue
                1f,  // Red
                1.25f, //Yellow
                0.7f) // Green


        animator = ValueAnimator.ofFloat(FloatEvaluator().evaluate(.1f, 0f, 1f))
        animator!!.interpolator = AccelerateInterpolator()
        animator!!.addUpdateListener { animation ->
            val percent = animation.animatedFraction
            var x: Float
            var y: Float
            var h: Float
            var s: Float
            for (i in dots.indices) {
                x = dots[i].x() + distance(prevDots[i].x(), positions[i].x, percent)
                y = dots[i].y() + distance(prevDots[i].y(), positions[i].y, percent)
                s = dots[i].scale() + distance(prevDots[i].scale(), scales[i], percent)
                //height where in speaking mode is changed from rms, so reset to current size
                h = dots[i].height() + distance(prevDots[i].height(), dots[i].size, percent)
                dots[i].set(x, y).height(h).scale(s)
            }

            container.postInvalidate()

        }
        animator!!.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                //Make sure we are in the corrent positions
                for (i in dots.indices) {
                    dots[i].reset().set(positions[i].x, positions[i].y).height(dots[i].size).scale(scales[i])
                }
                container.invalidate()

            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator!!.duration = 500
        animator!!.start()
    }

    private fun listenState() {


        resetToHorizontal(Handler.Callback {
            stopAnimator()

            //First time only first dot will start animation
            //after first dot reaches certain point, will tell next dot to animate (will set dotAngles[next] = 0.0)
            //when the next dot reaches certain point, will tell next dot and so on
            val dotAngles = doubleArrayOf(0.0, -1.0, -1.0, -1.0)

            val height = tileHeight / 2
            val angleScale = .07

            animator = TimeAnimator().apply {
                setTimeListener { animation, totalTime, deltaTime ->
                    dots.forEachIndexed { i, dot ->
                        //check if this dot will be animated by checking if angle is >= 0
                        if (dotAngles[i] >= 0) {
                            val cos = Math.sin(dotAngles[i])
                            val y = center.y + height * cos
                            dot.set(dot.x(), y.toFloat())
                            dotAngles[i] = (dotAngles[i] + angleScale) % RADIAN //Radians

                            //if this dot has reached certain position, tell next dot to animate
                            if (cos > .5 && (i + 1) < dots.size && dotAngles[i + 1] < 0.0) {
                                dotAngles[i + 1] = 0.0
                            }
                        }
                    }
                    container.postInvalidate()
                }
                repeatCount = ValueAnimator.INFINITE

                start()
            }
            true
        })

    }

    private fun incomprehensionState() {
        resetToHorizontal(Handler.Callback {
            stopAnimator()

            animator = ValueAnimator.ofFloat(FloatEvaluator().evaluate(1f, 0, RADIAN)).apply {
                addUpdateListener { a ->
                    //this value is usualy an angle between 0->6.28 in Radians
                    val angleRadian = (a.animatedValue as Float).toDouble()

                    dots.forEach { dot ->
                        val sin = Math.sin(angleRadian)
                        val x = dot.x() - ((tileWidth / 2) * sin)
                        dot.set(x.toFloat(), dot.y())
                    }
                    container.postInvalidate()
                }
                repeatMode = ValueAnimator.RESTART
                repeatCount = 4
                duration = 100
                start()
            }
            true

        })
    }

    private fun userSpeakState() {
        resetToHorizontal(Handler.Callback {
            stopAnimator()

            animator = TimeAnimator().apply {
                setTimeListener { animation, totalTime, deltaTime ->
                    dots.forEach {
                        //tweak a little bit to look random for each dot :P
                        val rand = Math.random()
                        val _rms = (rms * rand * if (rand > .5) 1 else -1).toFloat()
                        //if this random is less than half of currentRms, make negative

                        val h = lerp(it.height(), rms + _rms, .1f)
                        it.height(h)
                    }
                    container.postInvalidate()
                }
                repeatCount = ValueAnimator.INFINITE
                start()
            }
            true
        })

    }


    private fun thinkState() {
        resetToCircle(Handler.Callback {

            stopAnimator()

            animator = TimeAnimator().apply {
                interpolator = AccelerateInterpolator()
                setTimeListener { animation, totalTime, deltaTime ->
                    dots.forEach { it.set(rotate(it.x(), it.y(), rotationAngle)) }
                    container.postInvalidate()
                }
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
            true
        })
    }


    private fun replyState() {

        resetToCircle(Handler.Callback {

            stopAnimator()


            animator = ValueAnimator.ofFloat(FloatEvaluator().evaluate(1f, 0, RADIAN)).apply {
                addUpdateListener { a ->

                    //this value is usualy an angle between 0->6.28 in Radians
                    val animatedValue = (a.animatedValue as Float).toDouble()

                    for (d in dots) {
                        //d.set(rotate(d.x(), d.y()))

                        val p = rotate(d.x(), d.y(), rotationAngle)

                        val deltaX = p.x - center.x
                        val deltaY = p.y - center.y
                        val angle = Math.atan2(deltaY.toDouble(), deltaX.toDouble())

                        val value = (Math.sin(animatedValue) * 1.2).toFloat()
                        p.x += value * Math.cos(angle).toFloat()
                        p.y += value * Math.sin(angle).toFloat()

                        d.set(p)
                    }
                    container.postInvalidate()
                }
                duration = 400
                repeatCount = ValueAnimator.INFINITE
                start()
            }

            true
        })
    }

    /**
     * Resets dots by animating from actual position to horizontal position
     * @param callback: The callback to notify when dots has reached the desired position
     */
    private fun resetToHorizontal(callback: Handler.Callback?) {
        //Make sure we are not running any animation
        stopAnimator()

        val prevDots = Arrays.copyOf(dots, dots.size)
        val listenPosX = floatArrayOf(
                tileWidth,      //Blue x position
                tileWidth * 3,  //Red x position
                tileWidth * 5,  //Yellow x position
                tileWidth * 7)  // Green x position


        animator = ValueAnimator.ofFloat(FloatEvaluator().evaluate(.1f, 0f, 1f))
        animator!!.interpolator = AccelerateInterpolator()
        animator!!.addUpdateListener { animation ->
            val fraction = animation.animatedFraction

            var x: Float
            var y: Float
            var h: Float
            var s: Float
            dots.forEachIndexed { i, dot ->
                x = dot.x() + distance(prevDots[i].x(), listenPosX[i], fraction)
                y = dot.y() + distance(prevDots[i].y(), center.y, fraction)
                h = dot.height() + distance(prevDots[i].height(), dot.size, fraction)
                s = dot.scale() + distance(prevDots[i].scale(), 1f, fraction)
                dot.set(x, y).height(h).scale(s)
            }

            container.postInvalidate()

        }
        animator!!.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                //when animation finishes
                //make sure we are in the desired positions
                for (i in dots.indices) {
                    dots[i].reset().set(listenPosX[i], center.y)
                }
                callback?.handleMessage(Message())
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator!!.duration = 500
        animator!!.start()
    }

    /**
     * Resets dots by animating from actual position to circle position
     * @param callback: The callback to notify when dots has reached the desired position
     */
    private fun resetToCircle(callback: Handler.Callback?) {
        stopAnimator()


        val prevDots = Arrays.copyOf(dots, dots.size)

        val radius = tileWidth * 1.5f

        val positions = arrayOf(
                floatArrayOf(center.x - radius, center.y),
                floatArrayOf(center.x, center.y - radius),
                floatArrayOf(center.x + radius, center.y),
                floatArrayOf(center.x, center.y + radius))


        //fraction, percentage normalized, from 0.0 (0%) to 1.0 (100%)
        animator = ValueAnimator.ofFloat(FloatEvaluator().evaluate(.1f, 0f, 1f))
        animator!!.interpolator = AccelerateInterpolator()
        animator!!.addUpdateListener { animation ->
            val fraction = animation.animatedFraction

            var x: Float
            var y: Float
            var h: Float
            var s: Float
            dots.forEachIndexed { i, dot ->
                x = dot.x() + distance(prevDots[i].x(), positions[i][0], fraction)
                y = dot.y() + distance(prevDots[i].y(), positions[i][1], fraction)
                s = dot.scale() + distance(prevDots[i].scale(), 1f, fraction)
                h = dot.height() + distance(prevDots[i].height(), dot.size, fraction)
                dot.set(x, y).height(h).scale(s)
            }
            container.postInvalidate()

        }
        animator!!.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                //Make sure that we are in position we want to be.
                for (i in dots.indices) {
                    dots[i].reset().set(positions[i][0], positions[i][1]).height(prevDots[i].size).scale(prevDots[i].scale())
                }
                callback?.handleMessage(Message())
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator!!.duration = 500
        animator!!.start()

    }


    /**
     * Linear Interpolation from point a to b by t time
     * @param a first point
     * @param b second point
     * @param t time
     * @return result from Linear Interpolation
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return (1 - t) * a + t * b
    }

    /**
     * Calculates the distance between two 2D points by a coeficient
     * @param from first point
     * @param to second point
     * @param coeffNormalized a coeficient from 0.0 -> 1.0
     * @return the calculated distance
     */
    private fun distance(from: Float, to: Float, coeffNormalized: Float): Float {
        return (to - from) * coeffNormalized
    }

    /**
     * Rotates two points by global center
     * @param x x-coordinate point
     * @param y y-coordinate point
     * @return PointF(x, y)
     */
    private fun rotate(x: Float, y: Float, angleInRad: Double): PointF {
        val cos = Math.cos(angleInRad)
        val sin = Math.sin(angleInRad)
        val dx = x - center.x
        val dy = y - center.y

        return PointF(
                (cos * dx - sin * dy + center.x).toFloat(),
                (sin * dx + cos * dy + center.y).toFloat()
        )
    }


    private fun stopAnimator() {
        animator?.let {
            it.removeAllUpdateListeners()
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
    }

    fun draw(canvas: Canvas) {

        //region Draw Grid
        if (BuildConfig.DEBUG) {
            val paint = Paint()
            paint.color = Color.GRAY


            for (i in 0..cells) {
                val x = (i * tileWidth)
                val y = (i * tileHeight)

                canvas.drawLine(x, 0f, x, size.y.toFloat(), paint)
                canvas.drawLine(0f, y, size.x.toFloat(), y, paint)
            }
        }
        //endregion

        for (d in dots) d.draw(canvas)
    }

    internal inner class Dot(color: Int) {
        internal var size = 20f
        private var x: Float = 0.toFloat()
        private var y: Float = 0.toFloat()
        private var width = size
        private var height = size
        private var scale = 1f

        private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)


        init {
            paint.color = color
        }

        fun x(): Float = x - width / 2
        fun y(): Float = y - height / 2

        fun height(h: Float): Dot {
            var h = h
            if (h < size) {
                h = size
            }
            this.height = h
            return this
        }

        fun height() = height
        fun width() = width


        fun scale(s: Float): Dot {
            scale = s
            return this
        }

        fun scale() = scale

        fun reset(): Dot {
            height = size
            width = size
            x = width / 2f
            y = height / 2f
            scale = 1f
            return this
        }


        fun set(x: Float, y: Float): Dot {
            this.x = x + width / 2f
            this.y = y + height / 2f
            return this
        }

        fun set(point: PointF): Dot = set(point.x, point.y)


        fun draw(canvas: Canvas) {
            //We need the origin at center , so x - width/2 and so on...
            val _x = x - width / 2f
            val _y = y - height / 2f
            canvas.drawRoundRect(_x, _y, _x + width * scale, _y + height * scale, width * scale, width * scale, paint)
        }
    }
}