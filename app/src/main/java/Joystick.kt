package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt


interface OnJoystickMoveListener {
    fun onJoystickValueChanged(x:Float, y:Float)
}

class Joystick(context: Context, attrs: AttributeSet) : View(context, attrs){
    private var buttonRadius: Int = 0
    private var joystickRadius: Int = 0
    private var centerX: Float = 0.0f
    private var centerY: Float = 0.0f
    private var xPosition:Int = 0
    private var yPosition:Int = 0
    private var thread: Thread? = null
    private var listener:OnJoystickMoveListener? = null
    private var repeatInterval:Long = 1000

    private var defaultX:Int = 0
    private var defaultY:Int = 0

    private val defaultXPercent:Float
    private val defaultYPercent:Float

    private val xReturnDefault:Boolean
    private val yReturnDefault:Boolean

    var enable:Boolean=true
    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.Joystick,
            0, 0).apply {
            try {
                defaultXPercent = getFloat(R.styleable.Joystick_defaultXPercent, 0f)
                defaultYPercent = getFloat(R.styleable.Joystick_defaultYPercent, 0f)
                xReturnDefault = getBoolean(R.styleable.Joystick_xReturnDefault,true)
                yReturnDefault = getBoolean(R.styleable.Joystick_yReturnDefault,true)
            } finally {
                recycle()
            }

        }
    }
    private fun run() = Runnable {
        while(!Thread.interrupted()){
            listener?.onJoystickValueChanged(getOutX(), getOutY())
            try {
                Thread.sleep(repeatInterval)
            }catch (e:InterruptedException){
                break
            }
        }
    }
    fun setOnJoystickMoveListener(listener:OnJoystickMoveListener , interval:Long){
        this.listener=listener
        repeatInterval=interval
    }

    private val mainCirclePaint = Paint(ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = Color.BLUE
    }
    private val sencondCirclePaint = Paint(0).apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
    }

    private val buttonPaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // setting the measured values to resize the view to a certain width and
        // height
        val d = Math.min(measure(widthMeasureSpec), measure(heightMeasureSpec))
        setMeasuredDimension(d, d)
    }

    override fun onSizeChanged(xNew: Int, yNew: Int, xOld: Int, yOld: Int) {
        super.onSizeChanged(xNew, yNew, xOld, yOld)
        // before measure, get the center of view
        centerX= width/2.0f
        centerY= height/2.0f

        //xPosition = width / 2
        //yPosition = width / 2
        val d = Math.min(xNew, yNew)
        buttonRadius = (d / 2 * 0.25).toInt()
        joystickRadius = (d / 2 * 0.75).toInt()

        defaultX = (centerX + defaultXPercent*joystickRadius).toInt()
        defaultY = (centerY - defaultYPercent*joystickRadius).toInt()

        xPosition=defaultX
        yPosition=defaultY

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.apply {
            drawCircle(centerX,centerY,joystickRadius.toFloat(),mainCirclePaint)
            drawCircle(centerX,centerY,joystickRadius.toFloat()/2,sencondCirclePaint)
            drawLine(centerX,centerY,centerX+joystickRadius,centerY,sencondCirclePaint)
            drawLine(centerX,centerY,centerX,centerY-joystickRadius,sencondCirclePaint)
            drawCircle(xPosition.toFloat(), yPosition.toFloat(), buttonRadius.toFloat(), buttonPaint)

        }
    }

    private fun getOutX()=(xPosition - centerX) / joystickRadius
    private fun getOutY()=-(yPosition - centerY) / joystickRadius

    public fun setXY(targetX:Float,targetY:Float){
        xPosition=(joystickRadius*targetX+centerX).toInt()
        yPosition=(centerY-joystickRadius*targetY).toInt()
        invalidate()
    }

    private fun measure(measureSpec: Int): Int {
        var result = 0

        // Decode the measurement specifications.
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        result = if (specMode == MeasureSpec.UNSPECIFIED) {
            // Return a default size of 200 if no bounds are specified.
            200
        } else {
            // As you want to fill the available space
            // always return the full available bounds.
            specSize
        }
        return result
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {

        if(!enable){
            return true
        }

        xPosition = event.x.toInt()
        yPosition = event.y.toInt()
        val abs = sqrt(
            (xPosition - centerX) * (xPosition - centerX)
                    + (yPosition - centerY) * (yPosition - centerY)
        )
        if(xPosition>centerX+joystickRadius){
            xPosition=(centerX+joystickRadius).toInt()
        }else if(xPosition<centerX-joystickRadius){
            xPosition=(centerX-joystickRadius).toInt()
        }

        if(yPosition>centerY+joystickRadius){
            yPosition=(centerY+joystickRadius).toInt()
        }else if(yPosition<centerY-joystickRadius){
            yPosition=(centerY-joystickRadius).toInt()
        }

/*        if (abs > joystickRadius) {
            xPosition = ((xPosition - centerX) * joystickRadius / abs + centerX).toInt()
            yPosition = ((yPosition - centerY) * joystickRadius / abs + centerY).toInt()
        }*/
        invalidate()
        if (event.action == MotionEvent.ACTION_UP) {
            //xPosition = centerX.toInt()
            //yPosition = centerY.toInt()
            if(xReturnDefault){
                xPosition = defaultX
            }
            if(yReturnDefault){
                yPosition = defaultY
            }

            thread?.interrupt()
            listener?.onJoystickValueChanged(getOutX(),getOutY())

        }else if (event.action==MotionEvent.ACTION_DOWN && listener!=null){
           if(thread?.isAlive == true){
                thread?.interrupt()
           }
            thread=Thread(run())
            thread?.start()
        }
        return true
    }
}