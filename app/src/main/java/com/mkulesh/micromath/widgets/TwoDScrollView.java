/*
 * microMathematics - Extended Visual Calculator
 * Copyright (C) 2014-2022 by Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */
package com.mkulesh.micromath.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ScaleGestureDetectorCompat;
import androidx.core.view.ViewCompat;

import com.mkulesh.micromath.R;

/**
 * Layout container for a view hierarchy that can be scrolled by the user, allowing it to be larger than the physical
 * display. A TwoDScrollView is a {@link FrameLayout}, meaning you should place one child in it containing the entire
 * contents to scroll; this child may itself be a layout manager with a complex hierarchy of objects. A child that is
 * often used is a {@link LinearLayout} in a vertical orientation, presenting a vertical array of top-level items that
 * the user can scroll through.
 *
 * <p>
 * The {@link TextView} class also takes care of its own scrolling, so does not require a TwoDScrollView, but using the
 * two together is possible to achieve the effect of a text view within a larger container.
 *
 * Source: http://web.archive.org/web/20131020193237/http://blog.gorges.us/2010/06/android-two-dimensional-scrollview
 */
public class TwoDScrollView extends FrameLayout
{
    private static final int ANIMATED_SCROLL_GAP = 250;
    private static final float MAX_SCROLL_FACTOR = 0.5f;

    private long mLastScroll;
    private ListChangeIf listChangeIf = null;

    private final Rect mTempRect = new Rect();
    private Scroller mScroller;

    /**
     * True when the layout has changed but the traversal has not come through yet. Ideally the view hierarchy would
     * keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;

    /**
     * The child to give focus to in the event that a child has requested focus while the layout is dirty. This prevents
     * the scroll from being wrong if the child has not been laid out before requesting focus.
     */
    private View mChildToScrollTo = null;

    private boolean scaleDetectorActive = true, enableZoom = true;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;

    private final MyGestureListener mGestureListener = new MyGestureListener();
    private final MyScaleListener mScaleListener = new MyScaleListener();

    private int autoScrollMargins = 0;

    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;

    /*--------------------------------------------------------*
     * Creating
     *--------------------------------------------------------*/

    public TwoDScrollView(Context context)
    {
        super(context);
        prepare(null);
    }

    public TwoDScrollView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        prepare(attrs);
    }

    public TwoDScrollView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        prepare(attrs);
    }

    private void prepare(AttributeSet attrs)
    {
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        mScroller = new Scroller(getContext());
        mScaleGestureDetector = new ScaleGestureDetector(getContext(), mScaleListener);
        mGestureDetector = new GestureDetectorCompat(getContext(), mGestureListener);
        if (attrs != null)
        {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.TwoDScrollView, 0, 0);
            autoScrollMargins = a.getDimensionPixelSize(R.styleable.TwoDScrollView_autoScrollMargins, 0);
            a.recycle();
        }
        mEdgeGlowLeft = new EdgeEffect(getContext());
        mEdgeGlowTop = new EdgeEffect(getContext());
        mEdgeGlowRight = new EdgeEffect(getContext());
        mEdgeGlowBottom = new EdgeEffect(getContext());
        setWillNotDraw(false);
    }

    @Override
    public void addView(View child)
    {
        if (getChildCount() > 0)
        {
            throw new IllegalStateException("TwoDScrollView can host only one direct child");
        }
        super.addView(child);
    }

    @Override
    public void addView(View child, int index)
    {
        if (getChildCount() > 0)
        {
            throw new IllegalStateException("TwoDScrollView can host only one direct child");
        }
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params)
    {
        if (getChildCount() > 0)
        {
            throw new IllegalStateException("TwoDScrollView can host only one direct child");
        }
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params)
    {
        if (getChildCount() > 0)
        {
            throw new IllegalStateException("TwoDScrollView can host only one direct child");
        }
        super.addView(child, index, params);
    }

    /*--------------------------------------------------------*
     * Drawing
     *--------------------------------------------------------*/

    @Override
    public void draw(Canvas canvas)
    {
        super.draw(canvas);

        boolean needsInvalidate = false;
        final View mContentRect = getMainLayout();
        final int width = Math.max(getWidth() - getPaddingLeft() - getPaddingRight(), mContentRect.getWidth());
        final int height = Math.max(getHeight() - getPaddingTop() - getPaddingBottom(), mContentRect.getHeight());

        if (!mEdgeGlowTop.isFinished())
        {
            final int restoreCount = canvas.save();
            canvas.translate(getPaddingLeft(), Math.min(0, getScrollY()));
            mEdgeGlowTop.setSize(width, getHeight());
            if (mEdgeGlowTop.draw(canvas))
            {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeGlowBottom.isFinished())
        {
            final int restoreCount = canvas.save();
            canvas.translate(-width + getPaddingLeft(), Math.max(getScrollRangeY(), getScrollY()) + getHeight());
            canvas.rotate(180, width, 0);
            mEdgeGlowBottom.setSize(width, getHeight());
            if (mEdgeGlowBottom.draw(canvas))
            {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeGlowLeft.isFinished())
        {
            final int restoreCount = canvas.save();
            canvas.rotate(270);
            canvas.translate(-height + getPaddingTop(), Math.min(0, getScrollX()));
            mEdgeGlowLeft.setSize(height, getWidth());
            if (mEdgeGlowLeft.draw(canvas))
            {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeGlowRight.isFinished())
        {
            final int restoreCount = canvas.save();
            canvas.rotate(90);
            canvas.translate(-getPaddingTop(), -(Math.max(getScrollRangeX(), getScrollX()) + getWidth()));
            mEdgeGlowRight.setSize(height, getWidth());
            if (mEdgeGlowRight.draw(canvas))
            {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (needsInvalidate)
        {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /*--------------------------------------------------------*
     * Interface
     *--------------------------------------------------------*/

    public LinearLayout getMainLayout()
    {
        return (LinearLayout) getChildAt(0);
    }

    public void setScaleListener(ListChangeIf listChangeIf)
    {
        this.listChangeIf = listChangeIf;
    }

    /*--------------------------------------------------------*
     * Touch processing
     *--------------------------------------------------------*/

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event)
    {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && event.getEdgeFlags() != 0)
        {
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants.
            return false;
        }
        onTouchEvent(event);
        return mGestureListener.isMoved;
    }

    public void setScaleDetectorActive(boolean scaleDetectorActive)
    {
        this.scaleDetectorActive = scaleDetectorActive;
    }

    public void setZoomMode(final String zoomMode)
    {
        enableZoom = !"none".equals(zoomMode);
        ScaleGestureDetectorCompat.setQuickScaleEnabled(mScaleGestureDetector, "enable-all".equals(zoomMode));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;

        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP)
        {
            mGestureListener.finish();
        }

        if (action == MotionEvent.ACTION_DOWN)
        {
            mScroller.abortAnimation();
            mGestureListener.finish();
        }

        return retVal || super.onTouchEvent(event);
    }

    /**
     * The gesture listener, used for handling simple gestures such as double touches, scrolls, and flings.
     */
    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener
    {
        private boolean isMoved = false;
        float velocityX = 0;
        float velocityY = 0;

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY)
        {
            if (canScrollX() && !mScaleListener.isScaled)
            {
                final int rangeX = getScrollRangeX();
                final int pulledToX = getScrollX() + (int) deltaX;
                if (pulledToX < 0)
                {
                    mEdgeGlowLeft.onPull(deltaX / getWidth());
                    if (!mEdgeGlowRight.isFinished())
                    {
                        mEdgeGlowRight.onRelease();
                    }
                    deltaX = 0;
                }
                else if (pulledToX > rangeX)
                {
                    mEdgeGlowRight.onPull(deltaX / getWidth());
                    if (!mEdgeGlowLeft.isFinished())
                    {
                        mEdgeGlowLeft.onRelease();
                    }
                    deltaX = 0;
                }
                if (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished())
                {
                    ViewCompat.postInvalidateOnAnimation(TwoDScrollView.this);
                }
            }

            if (canScrollY() && !mScaleListener.isScaled)
            {
                final int rangeY = getScrollRangeY();
                final int pulledToY = getScrollY() + (int) deltaY;
                if (pulledToY < 0)
                {
                    mEdgeGlowTop.onPull(deltaY / getHeight());
                    if (!mEdgeGlowBottom.isFinished())
                    {
                        mEdgeGlowBottom.onRelease();
                    }
                    deltaY = 0;
                }
                else if (pulledToY > rangeY)
                {
                    mEdgeGlowBottom.onPull(deltaY / getHeight());
                    if (!mEdgeGlowTop.isFinished())
                    {
                        mEdgeGlowTop.onRelease();
                    }
                    deltaY = 0;
                }
                if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished())
                {
                    ViewCompat.postInvalidateOnAnimation(TwoDScrollView.this);
                }
            }

            if ((int) deltaY != 0 || (int) deltaX != 0)
            {
                isMoved = true;
                scrollBy((int) deltaX, (int) deltaY);
            }
            return true;
        }

        void finish()
        {
            mEdgeGlowLeft.onRelease();
            mEdgeGlowRight.onRelease();
            mEdgeGlowTop.onRelease();
            mEdgeGlowBottom.onRelease();
            isMoved = false;
            velocityX = 0;
            velocityY = 0;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            fling((int) -velocityX, (int) -velocityY);
            return true;
        }
    }

    /**
     * The scale listener, used for handling multi-finger scale gestures.
     */
    private class MyScaleListener implements ScaleGestureDetector.OnScaleGestureListener
    {
        boolean isScaled = false;
        private float scale = 1.0f;

        public boolean onScaleBegin(ScaleGestureDetector detector)
        {
            if (scaleDetectorActive && enableZoom)
            {
                isScaled = true;
                scale = 1.0f;
                mEdgeGlowLeft.onRelease();
                mEdgeGlowRight.onRelease();
                mEdgeGlowTop.onRelease();
                mEdgeGlowBottom.onRelease();
            }
            else
            {
                isScaled = false;
            }
            return isScaled;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector)
        {
            if (isScaled && listChangeIf != null)
            {
                scale *= detector.getScaleFactor();
                getMainLayout().setScaleX(scale);
                getMainLayout().setScaleY(scale);
            }
            return true;
        }

        public void onScaleEnd(ScaleGestureDetector detector)
        {
            if (isScaled)
            {
                scale *= detector.getScaleFactor();
                if (listChangeIf != null)
                {
                    listChangeIf.onScale(scale);
                    getMainLayout().setScaleX(1.0f);
                    getMainLayout().setScaleY(1.0f);
                }
            }
            isScaled = false;
        }
    }

    /*--------------------------------------------------------*
     * Realization
     *--------------------------------------------------------*/

    @Override
    protected float getTopFadingEdgeStrength()
    {
        if (getChildCount() == 0)
        {
            return 0.0f;
        }
        final int length = getVerticalFadingEdgeLength();
        if (getScrollY() < length)
        {
            return getScrollY() / (float) length;
        }
        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength()
    {
        if (getChildCount() == 0)
        {
            return 0.0f;
        }
        final int length = getVerticalFadingEdgeLength();
        final int bottomEdge = getHeight() - getPaddingBottom();
        final int span = getMainLayout().getBottom() - getScrollY() - bottomEdge;
        if (span < length)
        {
            return span / (float) length;
        }
        return 1.0f;
    }

    @Override
    protected float getLeftFadingEdgeStrength()
    {
        if (getChildCount() == 0)
        {
            return 0.0f;
        }
        final int length = getHorizontalFadingEdgeLength();
        if (getScrollX() < length)
        {
            return getScrollX() / (float) length;
        }
        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength()
    {
        if (getChildCount() == 0)
        {
            return 0.0f;
        }
        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final int span = getMainLayout().getRight() - getScrollX() - rightEdge;
        if (span < length)
        {
            return span / (float) length;
        }
        return 1.0f;
    }

    /**
     * @return The maximum amount this scroll view will scroll in response to an arrow event.
     */
    private int getMaxScrollAmountVertical()
    {
        return (int) (MAX_SCROLL_FACTOR * getHeight());
    }

    private int getMaxScrollAmountHorizontal()
    {
        return (int) (MAX_SCROLL_FACTOR * getWidth());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        // Let the focused view and/or our descendants get the key first
        boolean handled = super.dispatchKeyEvent(event);
        if (handled)
        {
            return true;
        }
        return executeKeyEvent(event);
    }

    /**
     * You can call this function yourself to have the scroll view perform scrolling from a key event, just as if the
     * event had been dispatched to it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    private boolean executeKeyEvent(KeyEvent event)
    {
        mTempRect.setEmpty();
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            switch (event.getKeyCode())
            {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!event.isAltPressed())
                {
                    handled = arrowScroll(View.FOCUS_UP, false);
                }
                else
                {
                    handled = fullScroll(View.FOCUS_UP, false);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!event.isAltPressed())
                {
                    handled = arrowScroll(View.FOCUS_DOWN, false);
                }
                else
                {
                    handled = fullScroll(View.FOCUS_DOWN, false);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!event.isAltPressed())
                {
                    handled = arrowScroll(View.FOCUS_LEFT, true);
                }
                else
                {
                    handled = fullScroll(View.FOCUS_LEFT, true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!event.isAltPressed())
                {
                    handled = arrowScroll(View.FOCUS_RIGHT, true);
                }
                else
                {
                    handled = fullScroll(View.FOCUS_RIGHT, true);
                }
                break;
            }
        }
        return handled;
    }

    /**
     * Handles scrolling in response to a "home/end" shortcut press. This method will scroll the view to the top or
     * bottom and give the focus to the topmost/bottommost component in the new visible area. If no component is a good
     * candidate for focus, this scrollview reclaims the focus.
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP} to go the top of the view or
     *                  {@link android.view.View#FOCUS_DOWN} to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean fullScroll(int direction, boolean horizontal)
    {
        if (!horizontal)
        {
            boolean down = direction == View.FOCUS_DOWN;
            int height = getHeight();
            mTempRect.top = 0;
            mTempRect.bottom = height;
            if (down)
            {
                int count = getChildCount();
                if (count > 0)
                {
                    View view = getChildAt(count - 1);
                    mTempRect.bottom = view.getBottom();
                    mTempRect.top = mTempRect.bottom - height;
                }
            }
            return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom, 0, 0, 0);
        }
        else
        {
            boolean right = direction == View.FOCUS_DOWN;
            int width = getWidth();
            mTempRect.left = 0;
            mTempRect.right = width;
            if (right)
            {
                int count = getChildCount();
                if (count > 0)
                {
                    View view = getChildAt(count - 1);
                    mTempRect.right = view.getBottom();
                    mTempRect.left = mTempRect.right - width;
                }
            }
            return scrollAndFocus(0, 0, 0, direction, mTempRect.top, mTempRect.bottom);
        }
    }

    /**
     * Scrolls the view to make the area defined by <code>top</code> and <code>bottom</code> visible. This method
     * attempts to give the focus to a component visible in this area. If no component can be focused in the new visible
     * area, the focus is reclaimed by this scrollview.
     *
     * @param directionY the scroll direction: {@link android.view.View#FOCUS_UP} to go upward
     *                   {@link android.view.View#FOCUS_DOWN} to downward
     * @param top        the top offset of the new area to be made visible
     * @param bottom     the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocus(int directionY, int top, int bottom, int directionX, int left, int right)
    {
        boolean handled = true;
        int height = getHeight();
        int containerTop = getScrollY();
        int containerBottom = containerTop + height;
        boolean up = directionY == View.FOCUS_UP;
        int width = getWidth();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + width;
        boolean leftwards = directionX == View.FOCUS_UP;

        if ((top >= containerTop && bottom <= containerBottom) || (left >= containerLeft && right <= containerRight))
        {
            handled = false;
        }
        else
        {
            int deltaY = up ? (top - containerTop) : (bottom - containerBottom);
            int deltaX = leftwards ? (left - containerLeft) : (right - containerRight);
            doScroll(deltaX, deltaY);
        }
        return handled;
    }

    /**
     * Handle scrolling in response to an up or down arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was pressed
     * @return True if we consumed the event, false otherwise
     */
    private boolean arrowScroll(int direction, boolean horizontal)
    {
        View currentFocused = findFocus();
        if (currentFocused == this)
            currentFocused = null;
        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);
        final int maxJump = horizontal ? getMaxScrollAmountHorizontal() : getMaxScrollAmountVertical();

        if (!horizontal)
        {
            if (nextFocused != null)
            {
                nextFocused.getDrawingRect(mTempRect);
                offsetDescendantRectToMyCoords(nextFocused, mTempRect);
                final int deltaY = computeScrollDeltaToGetChildRectOnScreenY(mTempRect);
                doScroll(0, deltaY);
                nextFocused.requestFocus(direction);
            }
            else
            {
                // no new focus
                int scrollDelta = maxJump;
                if (direction == View.FOCUS_UP && getScrollY() < scrollDelta)
                {
                    scrollDelta = getScrollY();
                }
                else if (direction == View.FOCUS_DOWN)
                {
                    if (getChildCount() > 0)
                    {
                        int daBottom = getMainLayout().getBottom();
                        int screenBottom = getScrollY() + getHeight();
                        if (daBottom - screenBottom < maxJump)
                        {
                            scrollDelta = daBottom - screenBottom;
                        }
                    }
                }
                if (scrollDelta == 0)
                {
                    return false;
                }
                doScroll(0, direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta);
            }
        }
        else
        {
            if (nextFocused != null)
            {
                nextFocused.getDrawingRect(mTempRect);
                offsetDescendantRectToMyCoords(nextFocused, mTempRect);
                final int deltaX = computeScrollDeltaToGetChildRectOnScreenX(mTempRect);
                doScroll(deltaX, 0);
                nextFocused.requestFocus(direction);
            }
            else
            {
                // no new focus
                int scrollDelta = maxJump;
                if (direction == View.FOCUS_UP && getScrollY() < scrollDelta)
                {
                    scrollDelta = getScrollY();
                }
                else if (direction == View.FOCUS_DOWN)
                {
                    if (getChildCount() > 0)
                    {
                        int daBottom = getMainLayout().getBottom();
                        int screenBottom = getScrollY() + getHeight();
                        if (daBottom - screenBottom < maxJump)
                        {
                            scrollDelta = daBottom - screenBottom;
                        }
                    }
                }
                if (scrollDelta == 0)
                {
                    return false;
                }
                doScroll(direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta, 0);
            }
        }
        return true;
    }

    /**
     * Smooth scroll by a Y delta
     *
     * @param deltaX the number of pixels to scroll by on the X axis
     */
    private void doScroll(int deltaX, int deltaY)
    {
        if (deltaX != 0 || deltaY != 0)
        {
            smoothScrollBy(deltaX, deltaY);
        }
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    private void smoothScrollBy(int dx, int dy)
    {
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP)
        {
            mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
            awakenScrollBars(mScroller.getDuration());
            invalidate();
        }
        else
        {
            if (!mScroller.isFinished())
            {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * <p>
     * The scroll range of a scroll view is the overall height of all of its children.
     * </p>
     */
    @Override
    protected int computeVerticalScrollRange()
    {
        int count = getChildCount();
        return count == 0 ? getHeight() : getMainLayout().getBottom();
    }

    @Override
    protected int computeHorizontalScrollRange()
    {
        int count = getChildCount();
        return count == 0 ? getWidth() : getMainLayout().getRight();
    }

    private int getScrollRangeX()
    {
        int scrollRange = 0;
        if (getChildCount() > 0)
        {
            final View child = getMainLayout();
            scrollRange = Math.max(0, child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
        }
        return scrollRange;
    }

    private int getScrollRangeY()
    {
        int scrollRange = 0;
        if (getChildCount() > 0)
        {
            final View child = getMainLayout();
            scrollRange = Math.max(0, child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
        }
        return scrollRange;
    }

    /**
     * @return Returns true this HorizontalScrollView can be scrolled
     */
    private boolean canScrollX()
    {
        final View child = getMainLayout();
        if (child != null)
        {
            return getWidth() < child.getWidth() + getPaddingLeft() + getPaddingRight();
        }
        return false;
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private boolean canScrollY()
    {
        final View child = getMainLayout();
        if (child != null)
        {
            return getHeight() < child.getHeight() + getPaddingTop() + getPaddingBottom();
        }
        return false;
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec)
    {
        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed)
    {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(lp.leftMargin + lp.rightMargin,
                MeasureSpec.UNSPECIFIED);

        final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(lp.topMargin + lp.bottomMargin,
                MeasureSpec.UNSPECIFIED);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    public void computeScroll()
    {
        if (mScroller.computeScrollOffset())
        {
            // This is called at drawing time by ViewGroup. We don't want to
            // re-show the scrollbars at this point, which scrollTo will do,
            // so we replicate most of scrollTo here.
            //
            // It's a little odd to call onScrollChanged from inside the drawing.
            //
            // It is, except when you remember that computeScroll() is used to
            // animate scrolling. So unless we want to defer the onScrollChanged()
            // until the end of the animated scrolling, we don't really have a
            // choice here.
            //
            // I agree. The alternative, which I think would be worse, is to post
            // something and tell the subclasses later. This is bad because there
            // will be a window where mScrollX/Y is different from what the app
            // thinks it is.
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (oldX != x || oldY != y)
            {
                if (getChildCount() > 0)
                {
                    final View child = getMainLayout();
                    scrollTo(clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth()),
                            clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight()));
                }
                else
                {
                    scrollTo(x, y);
                }
                if (oldX != getScrollX() || oldY != getScrollY())
                {
                    onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
                }

                final int rangeX = getScrollRangeX();
                final int rangeY = getScrollRangeY();
                if (x < 0 && oldX >= 0)
                {
                    mEdgeGlowLeft.onAbsorb((int) mGestureListener.velocityX);
                }
                else if (x > rangeX && oldX <= rangeX)
                {
                    mEdgeGlowRight.onAbsorb((int) mGestureListener.velocityX);
                }
                if (y < 0 && oldY >= 0)
                {
                    mEdgeGlowTop.onAbsorb((int) mGestureListener.velocityY);
                }
                else if (y > rangeY && oldY <= rangeY)
                {
                    mEdgeGlowBottom.onAbsorb((int) mGestureListener.velocityY);
                }
            }

            // Keep on drawing until the animation has finished.
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    public void scrollToChild(View child)
    {
        child.getDrawingRect(mTempRect);
        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);
        mTempRect.top -= autoScrollMargins;
        mTempRect.bottom += autoScrollMargins;
        mTempRect.left -= autoScrollMargins;
        mTempRect.right += autoScrollMargins;
        final int deltaX = computeScrollDeltaToGetChildRectOnScreenX(mTempRect);
        final int deltaY = computeScrollDeltaToGetChildRectOnScreenY(mTempRect);
        if (deltaX != 0 || deltaY != 0)
        {
            scrollBy(deltaX, deltaY);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate)
    {
        final int deltaX = computeScrollDeltaToGetChildRectOnScreenX(rect);
        final int deltaY = computeScrollDeltaToGetChildRectOnScreenY(rect);
        final boolean scroll = (deltaX != 0 || deltaY != 0);
        if (scroll)
        {
            if (immediate)
            {
                scrollBy(deltaX, deltaY);
            }
            else
            {
                smoothScrollBy(deltaX, deltaY);
            }
        }
        return scroll;
    }

    /**
     * Compute the amount to scroll in the X direction in order to get a rectangle completely on the screen (or, if
     * taller than the screen, at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    private int computeScrollDeltaToGetChildRectOnScreenX(Rect rect)
    {
        if (getChildCount() == 0)
            return 0;
        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;
        int fadingEdge = getHorizontalFadingEdgeLength();
        // leave room for left fading edge as long as rect isn't at very left
        if (rect.left > 0)
        {
            screenLeft += fadingEdge;
        }
        // leave room for right fading edge as long as rect isn't at very right
        if (rect.right < getMainLayout().getWidth())
        {
            screenRight -= fadingEdge;
        }
        int scrollXDelta = 0;
        if (rect.right > screenRight && rect.left > screenLeft)
        {
            // need to move right to get it in view: move right just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).
            if (rect.width() > width)
            {
                // just enough to get screen size chunk on
                scrollXDelta += (rect.left - screenLeft);
            }
            else
            {
                // get entire rect at right of screen
                scrollXDelta += (rect.right - screenRight);
            }
            // make sure we aren't scrolling beyond the end of our content
            int right = getMainLayout().getRight();
            int distanceToRight = right - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);
        }
        else if (rect.left < screenLeft && rect.right < screenRight)
        {
            // need to move right to get it in view: move right just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).
            if (rect.width() > width)
            {
                // screen size chunk
                scrollXDelta -= (screenRight - rect.right);
            }
            else
            {
                // entire rect at left
                scrollXDelta -= (screenLeft - rect.left);
            }
            // make sure we aren't scrolling any further than the left our content
            scrollXDelta = Math.max(scrollXDelta, -getScrollX());
        }
        return scrollXDelta;
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get a rectangle completely on the screen (or, if
     * taller than the screen, at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    private int computeScrollDeltaToGetChildRectOnScreenY(Rect rect)
    {
        if (getChildCount() == 0)
            return 0;
        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;
        int fadingEdge = getVerticalFadingEdgeLength();
        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0)
        {
            screenTop += fadingEdge;
        }
        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.bottom < getMainLayout().getHeight())
        {
            screenBottom -= fadingEdge;
        }
        int scrollYDelta = 0;
        if (rect.bottom > screenBottom && rect.top > screenTop)
        {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).
            if (rect.height() > height)
            {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - screenTop);
            }
            else
            {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - screenBottom);
            }
            // make sure we aren't scrolling beyond the end of our content
            int bottom = getMainLayout().getBottom();
            int distanceToBottom = bottom - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
        }
        else if (rect.top < screenTop && rect.bottom < screenBottom)
        {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).
            if (rect.height() > height)
            {
                // screen size chunk
                scrollYDelta -= (screenBottom - rect.bottom);
            }
            else
            {
                // entire rect at top
                scrollYDelta -= (screenTop - rect.top);
            }
            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused)
    {
        if (!mIsLayoutDirty)
        {
            scrollToChild(focused);
        }
        else
        {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused;
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate)
    {
        if (child instanceof ViewGroup)
        {
            return true;
        }
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        return scrollToChildRect(rectangle, immediate);
    }

    @Override
    public void requestLayout()
    {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;
        // Give a child focus if it needs it
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this))
        {
            scrollToChild(mChildToScrollTo);
        }
        mChildToScrollTo = null;

        // Calling this with the present values causes it to re-claim them
        scrollTo(getScrollX(), getScrollY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (null == currentFocused || this == currentFocused)
        {
            return;
        }
        scrollToChild(currentFocused);
    }

    /**
     * Return true if child is an descendant of parent, (or equal to the parent).
     */
    private boolean isViewDescendantOf(View child, View parent)
    {
        if (child == parent)
        {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    /**
     * Fling the scroll view
     *
     * @param velocityY The initial velocity in the Y direction. Positive numbers mean that the finger/curor is moving down
     *                  the screen, which means we want to scroll towards the top.
     */
    private void fling(int velocityX, int velocityY)
    {
        if (getChildCount() > 0)
        {
            int height = getHeight() - getPaddingBottom() - getPaddingTop();
            int bottom = getMainLayout().getHeight();
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int right = getMainLayout().getWidth();

            mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY, 0, right - width, 0, bottom - height);

            awakenScrollBars(mScroller.getDuration());
            invalidate();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This version also clamps the scrolling to the bounds of our child.
     */
    public void scrollTo(int x, int y)
    {
        // we rely on the fact the View.scrollBy calls scrollTo.
        if (getChildCount() > 0)
        {
            final View child = getMainLayout();
            x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(), child.getWidth());
            y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(), child.getHeight());
            if (x != getScrollX() || y != getScrollY())
            {
                super.scrollTo(x, y);
            }
        }
    }

    private int clamp(int n, int my, int child)
    {
        if (my >= child || n < 0)
        {
            /*
             * my >= child is this case: |--------------- me ---------------| |------ child ------| or |---------------
             * me ---------------| |------ child ------| or |--------------- me ---------------| |------ child ------|
             *
             * n < 0 is this case: |------ me ------| |-------- child --------| |-- mScrollX --|
             */
            return 0;
        }
        if ((my + n) > child)
        {
            /*
             * this case: |------ me ------| |------ child ------| |-- mScrollX --|
             */
            return child - my;
        }
        return n;
    }
}
