package com.wayww.zxinglibrary;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.google.zxing.ResultPoint;

/**
 * Created by Administrator on 2016/8/6.
 */
public class ScannerView extends View {

    private static final String TAG = ScannerView.class.getSimpleName();
    private int backgroundColor;
    private Bitmap mBitmap;
    private int cameraOrientation;
    private float scale = 1f;
    private String text;
    private ResultPoint[] points;
    private ResultPoint centerPoint;
    private float resultSize;
    private float boundary;
    private double angleOffset;
    private Paint bgPaint;
    private Paint bmPaint;
    private Paint textPaint;

    private ValueAnimator focusAnimator;
    private ValueAnimator transAnimator;
    private ValueAnimator outAnimator;
    private float animValue ;
    private float transValue ;
    private float outValue;
    private Matrix matrix;
    private Path mPath;
    private float height;
    private float width;
    private float bmHeight;
    private float bmWidth;
    private CallBack callback;

    private boolean isRunning;
    private ColorMode mode = ColorMode.Black;
    private float mTranslate;
    private LinearGradient gradient;
    public enum ColorMode{
        White,Black
    }

    public ScannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setVisibility(INVISIBLE);
        init();
    }

    private void init(){
        bgPaint = new Paint();
        bgPaint.setStrokeWidth(10.0f);
        bmPaint = new Paint();
        bmPaint.setAntiAlias(true);
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(40);
        gradient = new LinearGradient(0, 0, getMeasuredWidth(), 0
                , new int[]{Color.WHITE, Color.BLUE,Color.WHITE},new float[]{0.45f , 0.5f, 0.55f}
                , Shader.TileMode.CLAMP);
        mPath = new Path();
        matrix = new Matrix();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        this.width = getMeasuredWidth();
        this.height = getHeight();
        mTranslate = -width/2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null){
            drawBackground(canvas);
            drawBitmap(canvas);
            //  drawResultPoint(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                out();
                break;
        }
        return true;
    }

    //对输入的图片进行旋转处理
    private void handleBitmap(){
        mBitmap = bitmapRotation(mBitmap, cameraOrientation);
    }

    //普通二维码有3或4个定位点，确定中点，旋转角度
    private void handlePoints(ResultPoint[] resultPoints){
        points = new ResultPoint[4];

        switch (cameraOrientation){
            case 90:
                for (int i=0; i<resultPoints.length; i++){
                    points [i] = new ResultPoint(bmWidth - scale * resultPoints[i].getY(), scale * resultPoints[i].getX());
                }
                break;
            case 180:
                for (int i=0; i<resultPoints.length; i++){
                    points [i] = new ResultPoint(bmWidth-scale * resultPoints[i].getX(), bmHeight-scale * resultPoints[i].getY());
                }
                break;

            case 270:
                for (int i=0; i<resultPoints.length; i++){
                    points [i] = new ResultPoint(scale * resultPoints[i].getY(), bmHeight- scale * resultPoints[i].getX());
                }
                break;

            default:
                for (int i=0; i<resultPoints.length; i++){
                    points [i] = resultPoints[i];
                }
        }

        float scaleX = width/bmWidth;
        float scaleY = height/bmHeight;
        for (int i=0; i<resultPoints.length; i++){
            points [i] = new ResultPoint(scaleX*points[i].getX(), scaleY*points[i].getY());
        }

        this.centerPoint = new ResultPoint((points[0].getX()+points[2].getX())/2,
                (points[0].getY()+points[2].getY())/2);

        points[3] = new ResultPoint(centerPoint.getX()*2-points[1].getX(),
                centerPoint.getY()*2-points[1].getY());


        //边界的宽度
        resultSize = ResultPoint.distance(points[0],points[1]);
        if (text.length()<8){
            boundary = resultSize/2;
        }else if (text.length() >80){
            boundary = resultSize/8;
        }else {
            boundary = resultSize/2 - text.length()*resultSize/320;
        }
        Log.d(TAG,"text " + text);


        double angle = Math.asin((points[0].getY()-centerPoint.getY()) / ResultPoint.distance(centerPoint,points[0]));
        angle = Math.toDegrees(angle);
        Log.d(TAG,"angle = "+ angle);
        angleOffset = angle - 45;
    }

    private Bitmap bitmapRotation(Bitmap bitmap, final int orientationDegree) {
        Matrix matrix = new Matrix();
        matrix.setRotate(orientationDegree, (float) bitmap.getWidth()/2, (float) bitmap.getHeight()/2);
        try {
            Bitmap bm= Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            bitmap = null;
            this.bmHeight = bm.getHeight();
            this.bmWidth = bm.getWidth();
            return bm;
        } catch (OutOfMemoryError ex) {

        }
        return null;
    }


    private void drawBitmap(Canvas canvas){
        float offset = height;
        mPath.reset();
        bmPaint.reset();
        matrix.reset();

        // 根据以中点为原点所在的象限决定其移动的方向。
        boolean isMoveTo = false;
        float baseX = 0;
        float baseY = 0;
        for(int i = 0 ; i<4 ; i++){
            int xDirection = points[i].getX()>centerPoint.getX() ? 1 : -1;
            int yDirection = points[i].getY()>centerPoint.getY() ? 1 : -1;
            if (!isMoveTo){
                baseX = points[i].getX()+xDirection*offset*animValue ;
                baseY = points[i].getY()+yDirection*offset*animValue;
                mPath.moveTo(baseX, baseY);
                isMoveTo = true;
            }else {
                mPath.lineTo(points[i].getX()+xDirection*offset*animValue
                        ,points[i].getY()+yDirection*offset*animValue);
            }
        }
        mPath.lineTo(baseX, baseY);
        mPath.close();

        bmPaint.setAntiAlias(true);
        bmPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        bmPaint.setStrokeWidth(boundary);
        bmPaint.setColor(Color.WHITE);

        canvas.save();
        matrix.setTranslate((width/2-centerPoint.getX())*transValue
                ,(height/4 -centerPoint.getY())*transValue);

        //以一半的时间旋转，即两倍速度旋转
        if (transValue < 0.5f){
            matrix.postRotate((float) angleOffset * transValue*2
                    ,centerPoint.getX()+(width/2-centerPoint.getX())*transValue
                    ,centerPoint.getY()+ (height/4 -centerPoint.getY())*transValue);
        }else {
            matrix.postRotate((float) angleOffset
                    , centerPoint.getX() + (width / 2 - centerPoint.getX()) * transValue
                    , centerPoint.getY() + (height / 4 - centerPoint.getY()) * transValue);
        }

        canvas.concat(matrix);

        RectF rect = new RectF(0,0,width,height);
        int count =  canvas.saveLayer(rect,null,Canvas.CLIP_TO_LAYER_SAVE_FLAG);
        canvas.drawPath(mPath,bmPaint);
        bmPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(mBitmap,null,new RectF(0,0,width,height),bmPaint);
        canvas.restoreToCount(count);
        canvas.restore();

        //text
        if (transValue == 1f && outValue == 0){
            matrix.reset();
            mTranslate += width/80;
            Log.d(TAG, "mTranslate: "+mTranslate);
            if (mTranslate>width/2)
                mTranslate = -width;
            matrix.setTranslate(mTranslate,0);
            gradient = new LinearGradient(0, 0, getMeasuredWidth(), 0
                    , new int[]{Color.WHITE, getResources().getColor(R.color.scan),Color.WHITE}
                    ,new float[]{0.48f , 0.5f, 0.52f}
                    , Shader.TileMode.CLAMP);
            gradient.setLocalMatrix(matrix);
            textPaint.setShader(gradient);
            canvas.drawText(text, width/2, height/4 + 2*resultSize -height*outValue, textPaint);
            postInvalidateDelayed(30);
        }

        //out
        if (!isRunning){
            bgPaint.setColor(Color.WHITE);
            RectF rectF = new RectF(width/2-outValue*height, height/4-outValue*height
                    , width/2+outValue*height, height/4+outValue*height);
            canvas.drawRect(rectF,bgPaint);
        }

    }

    private void drawBackground(Canvas canvas){
        switch (mode){
            case White:
                bgPaint.setColor(Color.WHITE);
                break;
            case Black:
                bgPaint.setColor(Color.BLACK);
                break;
        }
        canvas.drawRect(0,0,width,height,bgPaint);
    }

    private void drawResultPoint(Canvas canvas){
        int []colors = {Color.RED,Color.YELLOW,Color.BLUE,Color.GREEN};
        int i = 0;
        for (ResultPoint point : points) {
            if (point != null) {
                bgPaint.setColor(colors[i]);
                canvas.drawPoint(point.getX(), point.getY(), bgPaint);
                i++;
            }
        }
    }

    private void startFocusAnimation(){
        if (mBitmap == null)
            return;
        focusAnimator = ValueAnimator.ofFloat(1f,0f);
        focusAnimator.setDuration(1000);
        focusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animValue = (float) focusAnimator.getAnimatedValue();
                Log.d(TAG,"animValue = " + animValue);
                invalidate();
            }
        });
        focusAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startTransAnimation();
            }
        });
        focusAnimator.start();
        isRunning = true;
    }

    private void startTransAnimation(){
        transAnimator = ValueAnimator.ofFloat(0f,1f);
        transAnimator.setDuration(400);
        transAnimator.setInterpolator(new DecelerateInterpolator());
        transAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                transValue = (float) transAnimator.getAnimatedValue();
                invalidate();
            }
        });
        transAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isRunning = false;
                if (callback != null)
                    callback.onAnimationEnd();

            }
        });
        transAnimator.start();
    }

    private void startOutAnimation(){
        outAnimator = ValueAnimator.ofFloat(0f,1f);
        outAnimator.setDuration(1000);
        outAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                outValue = (float) outAnimator.getAnimatedValue();
                invalidate();
            }
        });
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (callback != null)
                    callback.isOut();
            }
        });
        outAnimator.start();
    }



    public void setBitmap(Bitmap bitmap, float scale, String text, ResultPoint[] points, int Orientation){
        this.mBitmap = bitmap;
        this.scale = scale;
        this.text = text;
        this.cameraOrientation = Orientation;
        if (bitmap == null){
            postInvalidate();
            return;
        }
        if (points.length ==3 || points.length ==4){
            handleBitmap();
            handlePoints(points);
            invalidate();
            setVisibility(VISIBLE);
            startFocusAnimation();
            isRunning = true;
        }else {
            this.mBitmap = null;
        }
    }

    public void setBackgroundColor(int color){
        this.backgroundColor = color;
        bgPaint.setColor(color);
    }

    public boolean isRunning(){
        return isRunning;
    }

    public void setColorMode(ColorMode mode){
        this.mode = mode;
        switch (mode){
            case White:
                textPaint.setColor(Color.BLACK);
                break;
            case Black:
                textPaint.setColor(Color.WHITE);
                break;
        }
    }


    public void out(){
        if (isRunning)
            return;
        startOutAnimation();
    }

    public void setCallBack(CallBack callback){
        this.callback = callback;
    }


    public interface CallBack{
        void onAnimationEnd();
        void isOut();
    }

}



