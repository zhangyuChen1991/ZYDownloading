package com.czy.zydownloading;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 一个蛮酷的加载进度条
 * Created by zhangyu on 2016/12/17.
 */

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ZYDownloading extends View {
    private static final String TAG = "CoolDownloading";
    private int vWidth, vHeight;
    private Point center, lineCenter;
    private final double sin45 = Math.sin(45 * 2 * Math.PI / 360);
    private Context context;
    private Paint outPaint, innerPaint, circlePaint;
    //左右两段三阶贝塞尔曲线的起点、终点、各自的两个控制点
    private Point startP, stopPL, stopPR, ctrlL1, ctrlL2, ctrlR1, ctrlR2;
    //贝塞尔曲线控制点坐标与中心点坐标的差值 与 圆框半径的比率
    private float ctrlWRate = 1.35f, ctrlHRate = 1;
    //左右两段三阶贝塞尔曲线的path
    private Path pathLeft, pathRight, linePath, cornerRectPath, progressRectPath;
    private ValueAnimator scaleAnim, circleToLinePathAnim, lineJumpAnim, arrowToRectAnim, mergeAnim;
    private final int SCALE = 0X1229, CIRCLE_TO_LINE = 0X1331, LINE_JUMP = 0X1332, SHOW_LOADINGBAR = 0X1333;
    private int nowDrawState = SCALE;
    //直线是否弹跳到最高点
    private boolean JUMP_HIGHEST = false;
    //弹跳到最高点的y位置 以及与中心点的垂直距离
    private float jumpHightY, distance;
    //圆圈的半径
    private float circleRadius;
    private int circlePaintAlpha = 255;
    //圆形填充颜色
    private int circleColor = Color.parseColor("#A52A2A");//Color.parseColor("#A9A9A9");
    //箭头颜色
    private int arrowColor = Color.WHITE;//Color.BLACK;
    //下载进度 100满格
    private int progress = 0;

    //是否正在下载
    private boolean isDownloading = false;


    public ZYDownloading(Context context) {
        super(context);
        init(context);
    }

    public ZYDownloading(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZYDownloading(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(final Context context) {
        this.context = context;
        outPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outPaint.setStyle(Paint.Style.STROKE);
        outPaint.setColor(arrowColor);
        outPaint.setStrokeWidth(5);

        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStrokeWidth(5);
        innerPaint.setStyle(Paint.Style.FILL);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.FILL);

        pathLeft = new Path();
        pathRight = new Path();
        linePath = new Path();
        cornerRectPath = new Path();
        progressRectPath = new Path();


        LinearInterpolator linearInterpolator = new LinearInterpolator();

        scaleAnim = ValueAnimator.ofFloat(1, 0.85f, 1);
        scaleAnim.setInterpolator(linearInterpolator);
        scaleAnim.setDuration(350);
        scaleAnim.addUpdateListener(scaleListener);
        scaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                nowDrawState = CIRCLE_TO_LINE;
                //开始上下弹的动画
                circleToLinePathAnim.start();

            }
        });

        circleToLinePathAnim = ValueAnimator.ofFloat(0, 1);
        circleToLinePathAnim.setInterpolator(linearInterpolator);
        circleToLinePathAnim.setDuration(200);
        circleToLinePathAnim.addUpdateListener(C2LUpdateListener);
        circleToLinePathAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                nowDrawState = LINE_JUMP;
                //开始上下弹的动画
                lineJumpAnim.start();

            }
        });

        lineJumpAnim = ValueAnimator.ofFloat(0, -2.5f, 1.3f, -1f, 0.5f, -0.2f,0);
        lineJumpAnim.setInterpolator(linearInterpolator);
        lineJumpAnim.setDuration(450);
        lineJumpAnim.addUpdateListener(LJumpUpdateListener);

        arrowToRectAnim = ValueAnimator.ofFloat(0, -0.5f, 1);
        arrowToRectAnim.setInterpolator(linearInterpolator);
        arrowToRectAnim.setDuration(350);//350
        arrowToRectAnim.addUpdateListener(arrowToCircleAnimListener);
        arrowToRectAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

                nowDrawState = SHOW_LOADINGBAR;
                mergeAnim.start();
                super.onAnimationEnd(animation);
            }
        });

        //圆滑方框与线条融合的动画
        mergeAnim = ValueAnimator.ofFloat(0, 1);
        mergeAnim.setInterpolator(linearInterpolator);
        mergeAnim.setDuration(350);
        mergeAnim.addUpdateListener(mergeAnimListener);
        mergeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                JUMP_HIGHEST = false;
                radius = circleRadius;
                arrowCenter = new Point(center.x, center.y);
                circlePaintAlpha = 255;
                super.onAnimationEnd(animation);
            }
        });

    }

    private float barHeight = 5;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (nowDrawState == SCALE) {//圆框缩放阶段
            drawInnerCircle(canvas, circlePaintAlpha);
            drawCurvePath(canvas);
            drawArrow(canvas);
        } else if (nowDrawState == CIRCLE_TO_LINE) {
            drawCurvePath(canvas);
            if (startP.y <= center.y + rate3 * circleRadius) {//碰到曲线时，跟随弹动
                arrowCenter.y = startP.y - rate3 * circleRadius;
                drawArrow(canvas);
            } else {
                drawArrow(canvas);
            }
        } else if (nowDrawState == LINE_JUMP) {
            drawLinePath(canvas);
            if (!JUMP_HIGHEST) {//在接触过程中还没弹到最高点,跟随线条上弹
                arrowCenter.y = lineCenter.y - rate3 * circleRadius;
                drawArrow(canvas);
            } else {//接触过程中弹到最高点，开始飞起，再落下，过程中箭头渐变成圆角方框
                drawArrowToRect(canvas, radius);
            }
        } else if (nowDrawState == SHOW_LOADINGBAR) {
            drawArrowToRect(canvas, radius);
            drawLoadingBar(canvas, barHeight);
        }
    }

    /**
     * 绘制上下跳动的直线轨迹
     *
     * @param canvas
     */
    private void drawLinePath(Canvas canvas) {
        outPaint.setStyle(Paint.Style.STROKE);
        linePath.reset();
        //从上一阶段两条三阶贝塞尔曲线变成直线后再让直线上下跳动
        //stopL成为现在的linePath新起点，stopR成为新的终点 lineCenter作为控制点 构造新的二阶贝塞尔曲线
        linePath.moveTo(stopPL.x, stopPL.y);
        linePath.quadTo(lineCenter.x, lineCenter.y, stopPR.x, stopPR.y);
        canvas.drawPath(linePath, outPaint);
    }

    /**
     * 绘制贝塞尔曲线的轨迹
     *
     * @param canvas
     */
    private void drawCurvePath(Canvas canvas) {
        outPaint.setStyle(Paint.Style.STROKE);
        pathLeft.reset();
        pathRight.reset();

        pathLeft.moveTo(startP.x, startP.y);
        pathRight.moveTo(startP.x, startP.y);

        pathLeft.cubicTo(ctrlL1.x, ctrlL1.y, ctrlL2.x, ctrlL2.y, stopPL.x, stopPL.y);
        pathRight.cubicTo(ctrlR1.x, ctrlR1.y, ctrlR2.x, ctrlR2.y, stopPR.x, stopPR.y);

        canvas.drawPath(pathLeft, outPaint);
        canvas.drawPath(pathRight, outPaint);
    }

    private void drawInnerCircle(Canvas canvas, int alpha) {
        Path circlePath = new Path();
        circlePath.moveTo(startP.x, startP.y);
        circlePath.cubicTo(ctrlL1.x, ctrlL1.y, ctrlL2.x, ctrlL2.y, stopPL.x, stopPL.y);
        circlePath.cubicTo(ctrlR2.x, ctrlR2.y, ctrlR1.x, ctrlR1.y, startP.x, startP.y);

        circlePaint.setColor(circleColor);
        circlePaint.setAlpha(alpha);
        canvas.drawPath(circlePath, circlePaint);
    }

    //箭头的各个顶点
    Point arrowP0, arrowP1, arrowP2, arrowP3, arrowP4, arrowP5, arrowP6;
    //中心点到各个顶点距离与大圆半径的比率
    private float rate1 = 0.27f, rate2 = 0.55f, rate3 = 2 * rate1;
    private Point arrowCenter;

    private void initData() {
        center = new Point(vWidth / 2f, vHeight / 2f);

        float base = vWidth > vHeight ? vWidth : vHeight;
        circleRadius = base * 0.8f / 8f;
        //初始化直线的中心点
        lineCenter = new Point(center.x, center.y);

        //圆的外切正方形，两段贝塞尔曲线，控制点分别为正方形的四个顶点
        //左下角顶点 ctrlL1 左上角顶点 ctrlL2; 右下角顶点 ctrlR1 右上角顶点 ctrlR2
        updateCtrlPoint();

        arrowCenter = new Point(center.x, center.y);
    }

    private void updateCtrlPoint() {

        //初始数据模拟画圆
        //将圆分为左半边曲线和右半边曲线，起点为圆上正下方的点，终点为正上方的点
        startP = new Point(center.x, center.y + ctrlHRate * circleRadius);
        stopPL = new Point(center.x, center.y - ctrlHRate * circleRadius);
        stopPR = new Point(center.x, center.y - ctrlHRate * circleRadius);

        ctrlL1 = new Point(center.x - ctrlWRate * circleRadius, center.y + ctrlHRate * circleRadius);
        ctrlL2 = new Point(center.x - ctrlWRate * circleRadius, center.y - ctrlHRate * circleRadius);
        ctrlR1 = new Point(center.x + ctrlWRate * circleRadius, center.y + ctrlHRate * circleRadius);
        ctrlR2 = new Point(center.x + ctrlWRate * circleRadius, center.y - ctrlHRate * circleRadius);
    }

    private ValueAnimator.AnimatorUpdateListener scaleListener = new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float value = (float) animation.getAnimatedValue();//value 1-->0.8-->1
            ctrlWRate = value * 1.35f;
            ctrlHRate = value;

            rate1 = 0.27f * value;
            rate2 = 0.55f * value;
            rate3 = 2 * rate1;

            updateCtrlPoint();
            Log.i(TAG, "circlePaintAlpha = " + circlePaintAlpha);
            if (circlePaintAlpha > 0) {
                circlePaintAlpha -= 5;
                circlePaintAlpha = circlePaintAlpha < 0 ? 0 : circlePaintAlpha;
            }
            invalidate();
        }
    };

    /**
     * 圆变直线的动画监听  描述数据变化过程
     */
    private ValueAnimator.AnimatorUpdateListener C2LUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float value = (float) animation.getAnimatedValue();//value 0-->1
            startP.y = center.y + (1 - value) * circleRadius;

            stopPR.x = center.x + value * 3.8f * circleRadius;
            stopPR.y = center.y - (1 - value) * circleRadius;

            stopPL.x = center.x - value * 3.8f * circleRadius;
            stopPL.y = center.y - (1 - value) * circleRadius;

            ctrlL1.y = center.y + (1 - value) * circleRadius;

            ctrlL2.y = center.y - circleRadius + value * circleRadius;

            ctrlR1.y = center.y + (1 - value) * circleRadius;

            ctrlR2.y = center.y - circleRadius + value * circleRadius;

            invalidate();
        }
    };

    /**
     * 直线上下跳动的动画监听  描述数据变化过程
     */
    private ValueAnimator.AnimatorUpdateListener LJumpUpdateListener = new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float value = (float) animation.getAnimatedValue();
            lineCenter.y = center.y + value * circleRadius;

            Log.d(TAG, "LJumpUpdateListener  value = " + value);
            if (!JUMP_HIGHEST && value <= -2.1f) {
                JUMP_HIGHEST = true;
                arrowToRectAnim.start();
                //接触线的时候弹到的最高点
                jumpHightY = lineCenter.y;
                distance = center.y - jumpHightY;
            }
            invalidate();
        }
    };

    private float radius = circleRadius;
    private ValueAnimator.AnimatorUpdateListener arrowToCircleAnimListener = new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            //arrow0、3、4、6点位移至左上、右上、右下、左下方形成矩形 顶点加圆弧效果，模拟圆形
            float value = (float) animation.getAnimatedValue();//value 0-->-0.25-->1

            //中心点变化
            arrowCenter.y = jumpHightY + distance * value - rate3 * circleRadius;

            if (value > 0 && value <= 1) {//arrow顶点位移变化,只有0,3,4,6与value关联变化
                radius = circleRadius + value * circleRadius;
                float valueH = value * 0.4f;
                arrowP0 = new Point(arrowCenter.x - (rate1 + valueH) * circleRadius, arrowCenter.y - rate2 * value * circleRadius);
                arrowP1 = new Point(arrowCenter.x - rate1 * circleRadius, arrowCenter.y - circleRadius * rate2);
                arrowP2 = new Point(arrowCenter.x + rate1 * circleRadius, arrowCenter.y - circleRadius * rate2);
                arrowP3 = new Point(arrowCenter.x + (rate1 + valueH) * circleRadius, arrowCenter.y - rate2 * value * circleRadius);
                arrowP4 = new Point(arrowCenter.x + (rate3 - rate1 + valueH) * circleRadius, arrowCenter.y + value * rate3 * circleRadius);
                arrowP5 = new Point(arrowCenter.x, arrowCenter.y + rate3 * circleRadius);
                arrowP6 = new Point(arrowCenter.x - (rate3 + valueH - rate1) * circleRadius, arrowCenter.y + value * rate3 * circleRadius);
            } else {
                updateArrowPointByCenter();
            }
            invalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener mergeAnimListener = new ValueAnimator.AnimatorUpdateListener() {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float value = (float) animation.getAnimatedValue();//value 0-->1
            //圆弧线框向下缩小  线条变粗
            //点0,1,2,3向下移
            float distance = center.y - arrowP0.y;
            arrowP0.y = center.y - (1 - value) * distance;
            arrowP1.y = center.y - (1 - value) * distance;
            arrowP2.y = center.y - (1 - value) * distance;
            arrowP3.y = center.y - (1 - value) * distance;

            barHeight = 5 + 25 * value;

            invalidate();
        }
    };

    private void drawArrow(Canvas canvas) {
        innerPaint.setPathEffect(null);
        innerPaint.setColor(arrowColor);
        updateArrowPointByCenter();

        Path arrowPath = createArrowPath();

        canvas.drawPath(arrowPath, innerPaint);
    }

    private void drawArrowToRect(Canvas canvas, float radius) {
        //arrow0、3、4、6点位移至左上、右上、右下、左下方形成矩形 顶点加圆弧效果
        innerPaint.setPathEffect(new CornerPathEffect(radius));
        innerPaint.setColor(arrowColor);
        Path path = createArrowPath();

        canvas.drawPath(path, innerPaint);

    }

    /**
     * @param canvas
     * @param height
     */
    private void drawLoadingBar(Canvas canvas, float height) {
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setPathEffect(new CornerPathEffect(30));
        //完整矩形左上角，右下角的点
        Point lu = new Point(stopPL.x, stopPL.y - height);
        Point rd = new Point(stopPR.x, stopPR.y + height);

        float length = stopPR.x - stopPL.x;
        //进度右下角点

        innerPaint.setTextSize(35);


        //完整进度条长度
        innerPaint.setColor(arrowColor);
        cornerRectPath.reset();
        cornerRectPath.moveTo(lu.x, lu.y);
        cornerRectPath.lineTo(rd.x, lu.y);
        cornerRectPath.lineTo(rd.x, rd.y);
        cornerRectPath.lineTo(lu.x, rd.y);
        cornerRectPath.close();
        canvas.drawPath(cornerRectPath, innerPaint);


        //已下载长度
        innerPaint.setColor(circleColor);
        progressRectPath.reset();
        progressRectPath.moveTo(lu.x, lu.y);
        progressRectPath.lineTo(lu.x + progress * 0.01f * length, lu.y);
        progressRectPath.lineTo(lu.x + progress * 0.01f * length, rd.y);
        progressRectPath.lineTo(lu.x, rd.y);
        progressRectPath.close();
        canvas.drawPath(progressRectPath, innerPaint);


        String text = progress + "%";
        innerPaint.setColor(arrowColor);
        canvas.drawText(text, lu.x + progress * 0.01f * length - text.length() * 25, stopPR.y + 10, innerPaint);
    }

    /**
     * 根据中心点绘制箭头各顶点
     */
    private void updateArrowPointByCenter() {
        arrowP0 = new Point(arrowCenter.x - rate1 * circleRadius, arrowCenter.y);
        arrowP1 = new Point(arrowCenter.x - rate1 * circleRadius, arrowCenter.y - circleRadius * rate2);
        arrowP2 = new Point(arrowCenter.x + rate1 * circleRadius, arrowCenter.y - circleRadius * rate2);
        arrowP3 = new Point(arrowCenter.x + rate1 * circleRadius, arrowCenter.y);
        arrowP4 = new Point(arrowCenter.x + rate3 * circleRadius, arrowCenter.y);
        arrowP5 = new Point(arrowCenter.x, arrowCenter.y + rate3 * circleRadius);
        arrowP6 = new Point(arrowCenter.x - rate3 * circleRadius, arrowCenter.y);
    }

    private Path createArrowPath() {
        Path arrowPath = new Path();
        arrowPath.moveTo(arrowP0.x, arrowP0.y);
        arrowPath.lineTo(arrowP1.x, arrowP1.y);
        arrowPath.lineTo(arrowP2.x, arrowP2.y);
        arrowPath.lineTo(arrowP3.x, arrowP3.y);
        arrowPath.lineTo(arrowP4.x, arrowP4.y);
        arrowPath.lineTo(arrowP5.x, arrowP5.y);
        arrowPath.lineTo(arrowP6.x, arrowP6.y);
        arrowPath.close();
        return arrowPath;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        vHeight = getMeasuredHeight();
        vWidth = getMeasuredWidth();

        initData();
    }


    /**
     * 设置下载进度
     *
     * @param progress
     */
    public void setProgress(int progress) {
        this.progress = progress;
        if (progress == 100) {//下载完毕
            isDownloading = false;

        }
        invalidate();
    }

    /**
     * 设置圆形填充颜色
     *
     * @param circleColor
     */
    public void setCircleColor(int circleColor) {
        this.circleColor = circleColor;
    }

    /**
     * 设置箭头填充颜色
     *
     * @param arrowColor
     */
    public void setArrowColor(int arrowColor) {
        this.arrowColor = arrowColor;
    }


    public boolean startDownload() {
        if (!isDownloading) {
            progress = 0;
            isDownloading = true;
            nowDrawState = SCALE;
            scaleAnim.start();
            return true;
        }
        return false;
    }

    public void stopDownloading() {
        isDownloading = false;
        scaleAnim.cancel();
        circleToLinePathAnim.cancel();
        lineJumpAnim.cancel();
        mergeAnim.cancel();
        nowDrawState = SCALE;
        invalidate();
    }

    /**
     * 是否正在下载
     *
     * @return
     */
    public boolean isDownloading() {
        return isDownloading;
    }

    private class Point {
        float x, y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public Point() {
        }
    }
}
