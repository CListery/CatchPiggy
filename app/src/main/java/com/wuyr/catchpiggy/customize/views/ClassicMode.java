package com.wuyr.catchpiggy.customize.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import com.wuyr.catchpiggy.R;
import com.wuyr.catchpiggy.customize.MyLayoutParams;
import com.wuyr.catchpiggy.models.PositionData;
import com.wuyr.catchpiggy.models.WayData;
import com.wuyr.catchpiggy.models.WayData2;
import com.wuyr.catchpiggy.utils.BitmapUtil;
import com.wuyr.catchpiggy.utils.ComputeWayUtil;
import com.wuyr.catchpiggy.utils.LevelUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Stack;

/**
 * Created by wuyr on 17-9-29 下午8:09.
 */

@SuppressWarnings("EmptyCatchBlock")
public class ClassicMode extends ViewGroup {

    private int[][] mItemStatus;
    private Item[][] mItems;
    private Stack<int[][]> mHistory;
    private int mHorizontalPos, mVerticalPos;
    private int mItemSize;
    private int mItemSpacing;
    private int mVerticalCount;
    private int mHorizontalCount;
    private int mItemPadding;
    private View mDropTouchView;
    private OnTouchListener mDropTouchListener;
    private ImageView mSelectedView, mOccupiedView, mDropView;
    private boolean isAnimationPlaying, mLastItemIsLeft;
    private int mOffset;
    private Random mRandom;
    private AnimationDrawable mGoLeftAnimationDrawable, mGoRightAnimationDrawable,
            mDropLeftAnimationDrawable, mDropRightAnimationDrawable;
    private OnGameOverListener mOnGameOverListener;
    private OnPiggyDraggedListener mOnPiggyDraggedListener;
    private boolean isDragEnable, isNavigationOn;
    private boolean isGameOver;
    private int mCurrentLevel;
    private int mLastX, mLastY;

    public ClassicMode(Context context) {
        this(context, null);
    }

    public ClassicMode(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClassicMode(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ClassicMode, defStyleAttr, 0);
        mItemSize = (int) a.getDimension(R.styleable.ClassicMode_item_size, 36);
        mItemSpacing = (int) a.getDimension(R.styleable.ClassicMode_item_spacing, 4);
        mVerticalCount = a.getInteger(R.styleable.ClassicMode_item_vertical_count, 8);
        mHorizontalCount = a.getInteger(R.styleable.ClassicMode_item_horizontal_count, 8);
        if (mItemSpacing == 0) {
            mItemPadding = (int) getResources().getDimension(R.dimen.item_spacing);
        }
        a.recycle();
        init();
    }

    public void setDragEnable() {
        isDragEnable = true;
    }

    public void setNavigationOn() {
        isNavigationOn = true;
    }

    public void setLevel(int level) {
        if (isAnimationPlaying) {
            return;
        }
        isDragEnable = isNavigationOn = false;
        mCurrentLevel = level;
        refresh();
        if (mCurrentLevel > 0) {
            int[][] position = LevelUtil.getDefaultFencePosition(mVerticalCount, mHorizontalCount, level);
            for (int vertical = 0; vertical < mVerticalCount; vertical++) {
                for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                    if (position[vertical][horizontal] == Item.STATE_SELECTED) {
                        mItemStatus[vertical][horizontal] = Item.STATE_SELECTED;
                        mItems[vertical][horizontal].setStatus(Item.STATE_SELECTED);
                    }
                }
            }
        } else setRandomSelected();
    }

    public void refresh() {
        isGameOver = false;
        if (isAnimationPlaying) {
            return;
        }
        mHistory.clear();
        clearAllSelected();
        mHorizontalPos = mHorizontalCount / 2;
        mVerticalPos = mVerticalCount / 2;
        mItems[mVerticalPos][mHorizontalPos].setStatus(Item.STATE_OCCUPIED);
        mItemStatus[mVerticalPos][mHorizontalPos] = Item.STATE_OCCUPIED;
        requestLayout();
    }

    public void undo() {
        if (isAnimationPlaying || mHistory.empty() || isGameOver) {
            return;
        }
        mItemStatus = mHistory.pop();
        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                Item item = mItems[vertical][horizontal];
                int state = mItemStatus[vertical][horizontal];
                if (state == Item.STATE_OCCUPIED) {
                    mVerticalPos = vertical;
                    mHorizontalPos = horizontal;
                    requestLayout();
                }
                item.setStatus(state);
            }
        }
    }

    public void release() {
        if (mItems != null) {
            for (int vertical = 0; vertical < mVerticalCount; vertical++) {
                for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                    if (mItems[vertical][horizontal] != null) {
                        mItems[vertical][horizontal].release();
                    }
                }
            }
            mItems = null;
        }
        mItemStatus = null;
        mHistory = null;
        mDropTouchView = null;
        mDropTouchListener = null;
        mSelectedView = null;
        mOccupiedView = null;
        mDropView = null;
        mRandom = null;
        mGoLeftAnimationDrawable = null;
        mGoRightAnimationDrawable = null;
        mDropLeftAnimationDrawable = null;
        mDropRightAnimationDrawable = null;
        mOnGameOverListener = null;
    }

    public int getHistorySize() {
        return mHistory == null ? 0 : mHistory.size();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return isGameOver;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        mHistory = new Stack<>();
        mRandom = new Random();
        setClipChildren(false);
        setClipToPadding(false);
        mItemStatus = new int[mVerticalCount][mHorizontalCount];
        mItems = new Item[mVerticalCount][mHorizontalCount];
        mDropTouchListener = (v, event) -> {
            if (!isDragEnable) {
                return false;
            }
            MyLayoutParams layoutParams = (MyLayoutParams) mDropView.getLayoutParams();
            int x = (int) event.getX(), y = (int) event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isAnimationPlaying = true;
                    mLastX = x;
                    mLastY = y;
                    mItems[mVerticalPos][mHorizontalPos].hideOccupiedImage();
                    layoutParams.isDrag = false;
                    requestLayout();
                    if (mLastItemIsLeft = mItems[mVerticalPos][mHorizontalPos].isLeft()) {
                        mDropView.setImageDrawable(mDropLeftAnimationDrawable);
                        mDropLeftAnimationDrawable.start();
                    } else {
                        mDropView.setImageDrawable(mDropRightAnimationDrawable);
                        mDropRightAnimationDrawable.start();
                    }
                    mDropView.setVisibility(VISIBLE);
                    break;
                case MotionEvent.ACTION_MOVE:
                    layoutParams.isDrag = true;
                    layoutParams.x = x - mLastX;
                    layoutParams.y = y - mLastY;
                    requestLayout();
                    mLastX = x;
                    mLastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    layoutParams = (MyLayoutParams) mDropView.getLayoutParams();
                    layoutParams.isDrag = true;
                    layoutParams.x = x - mLastX;
                    layoutParams.y = y - mLastY;
                    requestLayout();
                    mLastX = x;
                    mLastY = y;
                    layoutParams.isDrag = false;
                    locationOccupiedView(mDropView.getLeft() + (mLastItemIsLeft ? ((mDropView.getWidth() / 2) + (mDropView.getWidth() / 4))
                                    : mDropView.getWidth() - ((mDropView.getWidth() / 2) + (mDropView.getWidth() / 4))),
                            (float) (mDropView.getTop() + (mDropView.getHeight() * .8)));
                    if (isDragEnable && mOnPiggyDraggedListener != null) {
                        mOnPiggyDraggedListener.onDragged();
                        isDragEnable = false;
                    }
                    break;
            }
            return true;
        };
        initChildrenViews((horizontalPos, verticalPos) -> {
            if (isAnimationPlaying) {
                return;
            }
            isAnimationPlaying = true;
            mDropTouchView.setOnTouchListener(null);
            mHistory.push(copyItemStatus());
            mItemStatus[verticalPos][horizontalPos] = Item.STATE_SELECTED;
            mSelectedView.startAnimation(getFenceAnimation(mItems[verticalPos][horizontalPos]));
        });
    }

    private void locationOccupiedView(float x, float y) {
        RectF rect = new RectF();
        boolean isPlaceChanged = false;
        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                Item item = mItems[vertical][horizontal];
                rect.left = item.getLeft();
                rect.right = item.getRight();
                rect.top = item.getTop();
                rect.bottom = item.getBottom();
                if (rect.contains(x, y) && mItemStatus[vertical][horizontal] == Item.STATE_UNSELECTED) {
                    mDropView.setVisibility(INVISIBLE);
                    if (mLastItemIsLeft) {
                        mDropLeftAnimationDrawable.stop();
                    } else {
                        mDropRightAnimationDrawable.stop();
                    }
                    mItems[mVerticalPos][mHorizontalPos].setStatus(Item.STATE_UNSELECTED);
                    mItemStatus[mVerticalPos][mHorizontalPos] = Item.STATE_UNSELECTED;
                    item.setIsLeft(mLastItemIsLeft);
                    item.setStatus(Item.STATE_OCCUPIED);
                    mItemStatus[vertical][horizontal] = Item.STATE_OCCUPIED;
                    requestLayout();
                    mVerticalPos = vertical;
                    mHorizontalPos = horizontal;
                    mOffset = mVerticalPos % 2 == 0 ? 0 : 1;
                    isPlaceChanged = true;
                    break;
                }
            }
        }
        isAnimationPlaying = false;
        if (!isPlaceChanged) {
            mItems[mVerticalPos][mHorizontalPos].showOccupiedImage();
            mDropView.setVisibility(INVISIBLE);
            if (mLastItemIsLeft) {
                mDropLeftAnimationDrawable.stop();
            } else {
                mDropRightAnimationDrawable.stop();
            }
        } else {
            WayData2 left = computeLeft();
            WayData2 leftTop = computeLeftTop();
            WayData2 leftBottom = computeLeftBottom();
            WayData2 right = computeRight();
            WayData2 rightTop = computeRightTop();
            WayData2 rightBottom = computeRightBottom();
            if (left.isBlock && left.count < 2
                    && right.isBlock && right.count < 2
                    && leftTop.isBlock && leftTop.count < 2
                    && leftBottom.isBlock && leftBottom.count < 2
                    && rightTop.isBlock && rightTop.count < 2
                    && rightBottom.isBlock && rightBottom.count < 2) {
                isGameOver = true;
                if (mOnGameOverListener != null) {
                    isAnimationPlaying = false;
                    mDropTouchView.setOnTouchListener(mDropTouchListener);
                    mDropTouchView.setEnabled(true);
                    mOnGameOverListener.onWin();
                }
            }
        }
    }

    private void initChildrenViews(Item.OnItemPressedListener onItemPressedListener) {
        BitmapDrawable unselectedDrawable = getUnselectedDrawable();
        BitmapDrawable selectedDrawable = getSelectedDrawable();
        BitmapDrawable occupiedDrawableLeft = getOccupiedDrawableLeft();
        BitmapDrawable occupiedDrawableRight = getOccupiedDrawableRight();
        BitmapDrawable guideDrawable = getGuideDrawable();

        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                Item tmp = new Item(getContext());
                tmp.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                tmp.setOnItemPressedListener(onItemPressedListener);
                tmp.setPositions(horizontal, vertical);
                tmp.setUnSelectedBitmap(unselectedDrawable.getBitmap());
                tmp.setSelectedBitmap(selectedDrawable.getBitmap());
                tmp.setOccupiedBitmapLeft(occupiedDrawableLeft.getBitmap());
                tmp.setOccupiedBitmapRight(occupiedDrawableRight.getBitmap());
                tmp.setGuideBitmap(guideDrawable.getBitmap());
                mItems[vertical][horizontal] = tmp;
                addView(tmp);
            }
        }

        mSelectedView = new ImageView(getContext());
        mSelectedView.setAdjustViewBounds(true);

        mSelectedView.setImageResource(R.drawable.anim_drop_left);
        mDropLeftAnimationDrawable = (AnimationDrawable) mSelectedView.getDrawable();

        mSelectedView.setImageResource(R.drawable.anim_drop_right);
        mDropRightAnimationDrawable = (AnimationDrawable) mSelectedView.getDrawable();

        mSelectedView.setImageResource(R.drawable.anim_run_left);
        mGoLeftAnimationDrawable = (AnimationDrawable) mSelectedView.getDrawable();

        mSelectedView.setImageResource(R.drawable.anim_run_right);
        mGoRightAnimationDrawable = (AnimationDrawable) mSelectedView.getDrawable();

        mSelectedView.setVisibility(INVISIBLE);
        LayoutParams lp = new LayoutParams(selectedDrawable.getBitmap().getWidth(), selectedDrawable.getBitmap().getHeight());
        mSelectedView.setLayoutParams(lp);
        mSelectedView.setImageDrawable(selectedDrawable);
        addView(mSelectedView);

        mOccupiedView = new ImageView(getContext());
        mOccupiedView.setAdjustViewBounds(true);
        mOccupiedView.setScaleType(ImageView.ScaleType.FIT_XY);
        LayoutParams lp2 = new LayoutParams(occupiedDrawableLeft.getBitmap().getWidth(), occupiedDrawableLeft.getBitmap().getHeight());
        mOccupiedView.setLayoutParams(lp2);
        mOccupiedView.setVisibility(INVISIBLE);
        addView(mOccupiedView);

        mDropView = new ImageView(getContext());
        mDropView.setAdjustViewBounds(true);
        mDropView.setScaleType(ImageView.ScaleType.FIT_XY);
        float occupiedScale = getOccupiedScale(new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_unselected)));
        MyLayoutParams lp3 = new MyLayoutParams((int) (mDropLeftAnimationDrawable.getIntrinsicWidth() * occupiedScale),
                (int) (mDropLeftAnimationDrawable.getIntrinsicHeight() * occupiedScale));
        mDropView.setLayoutParams(lp3);
        mDropView.setVisibility(INVISIBLE);
        addView(mDropView);

        mDropTouchView = new View(getContext());
        mDropTouchView.setLayoutParams(lp);
        mDropTouchView.setOnTouchListener(mDropTouchListener);
        addView(mDropTouchView);
    }

    private void computeWay() {
        mOffset = mVerticalPos % 2 == 0 ? 0 : 1;
        int offset2 = mVerticalPos % 2 == 0 ? 1 : 0;
        List<WayData> ways = new ArrayList<>();

        //left
        WayData2 left = computeLeft();
        if (left.item != null) {
            ways.add(new WayData(left.count, left.isBlock, mHorizontalPos - 1, mVerticalPos));
        }

        //leftTop
        WayData2 leftTop = computeLeftTop();
        if (leftTop.item != null) {
            ways.add(new WayData(leftTop.count, leftTop.isBlock, mHorizontalPos - mOffset, mVerticalPos - 1));
        }

        //leftBottom
        WayData2 leftBottom = computeLeftBottom();
        if (leftBottom.item != null) {
            ways.add(new WayData(leftBottom.count, leftBottom.isBlock, mHorizontalPos - mOffset, mVerticalPos + 1));
        }

        //right
        WayData2 right = computeRight();
        if (right.item != null) {
            ways.add(new WayData(right.count, right.isBlock, mHorizontalPos + 1, mVerticalPos));
        }

        //rightTop
        WayData2 rightTop = computeRightTop();
        if (rightTop.item != null) {
            ways.add(new WayData(rightTop.count, rightTop.isBlock, mHorizontalPos + offset2, mVerticalPos - 1));
        }

        //rightBottom
        WayData2 rightBottom = computeRightBottom();
        if (rightBottom.item != null) {
            ways.add(new WayData(rightBottom.count, rightBottom.isBlock, mHorizontalPos + offset2, mVerticalPos + 1));
        }
        Collections.sort(ways, (o1, o2) -> (o1.count < o2.count) ? -1 : ((o1.count == o2.count) ? 0 : 1));
        if (left.isBlock && left.count < 2
                && right.isBlock && right.count < 2
                && leftTop.isBlock && leftTop.count < 2
                && leftBottom.isBlock && leftBottom.count < 2
                && rightTop.isBlock && rightTop.count < 2
                && rightBottom.isBlock && rightBottom.count < 2) {
            isGameOver = true;
            if (mOnGameOverListener != null) {
                isAnimationPlaying = false;
                mDropTouchView.setOnTouchListener(mDropTouchListener);
                mDropTouchView.setEnabled(true);
                mOnGameOverListener.onWin();
            }
            return;
        }
        WayData nextPos = null;
        try {
            nextPos = ComputeWayUtil.findWay(mCurrentLevel, mItemStatus, new WayData(mHorizontalPos, mVerticalPos), ways);
            if (nextPos == null) {
                while (true) {
                    nextPos = ways.get(mRandom.nextInt(ways.size()));
                    if (mItemStatus[nextPos.y][nextPos.x] != Item.STATE_SELECTED) {
                        break;
                    }
                }
            }
            findExit(nextPos);
        } catch (ArrayIndexOutOfBoundsException e) {
            isAnimationPlaying = true;
            mDropTouchView.setOnTouchListener(null);
            isGameOver = true;
            if (mOnGameOverListener != null) {
                if (nextPos != null) {
                    startRunAnimation(nextPos);
                }
            }
        }
    }

    private void startRunAnimation(WayData wayData) {
        Item item;
        PositionData positionData = new PositionData();
        if (wayData.x >= mHorizontalCount) {
            item = mItems[wayData.y][wayData.x - 1];
            positionData.startX = item.getX();
            positionData.endX = getHeight() * 2;
            positionData.endY = positionData.startY = item.getBottom() - mOccupiedView.getHeight();
        } else if (wayData.x < 0) {
            item = mItems[wayData.y][0];
            positionData.startX = item.getX();
            positionData.endX = -getHeight();
            positionData.endY = positionData.startY = item.getBottom() - mOccupiedView.getHeight();
        } else if (wayData.y >= mVerticalCount) {
            item = mItems[wayData.y - 1][wayData.x];
            positionData.endX = positionData.startX = item.getX();
            positionData.startY = item.getBottom() - mOccupiedView.getHeight();
            positionData.endY = getHeight() * 2;
        } else {
            item = mItems[0][wayData.x];
            positionData.endX = positionData.startX = item.getX();
            positionData.startY = item.getBottom() - mOccupiedView.getHeight();
            positionData.endY = -getHeight();
        }
        mOccupiedView.startAnimation(getRunAnimation(item, positionData));
    }

    private WayData2 computeLeft() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos][mHorizontalPos - count]);
    }

    private WayData2 computeLeftTop() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos - count][mOffset == 0 || isSameGroup ? mHorizontalPos - (tmp - 1) : mHorizontalPos - (tmp - 1) - 1]);
    }

    private WayData2 computeLeftBottom() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos + count][mOffset == 0 || isSameGroup ? mHorizontalPos - (tmp - 1) : mHorizontalPos - (tmp - 1) - 1]);
    }

    private WayData2 computeRight() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos][mHorizontalPos + count]);
    }

    private WayData2 computeRightTop() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos - count][isSameGroup ? (mHorizontalPos + (tmp)) - 1 : mOffset == 0 ? (mHorizontalPos + (tmp - 1)) + 1 : mHorizontalPos + (tmp - 1)]);
    }

    private WayData2 computeRightBottom() {
        return computeDirection((count, isSameGroup, tmp) -> mItems[mVerticalPos + count][isSameGroup ? (mHorizontalPos + (tmp)) - 1 : mOffset == 0 ? (mHorizontalPos + (tmp - 1)) + 1 : mHorizontalPos + (tmp - 1)]);
    }

    private WayData2 computeDirection(@NonNull ComputeDirection computer) {
        Item item = null;
        int count = 0;
        boolean isBlock = false;
        try {
            int tmp = 0;
            boolean isSameGroup;
            while (count <= mVerticalCount) {
                isSameGroup = count % 2 == 0;
                if (isSameGroup) {
                    tmp++;
                }
                item = computer.getItem(count, isSameGroup, tmp);
                if (item.getStatus() == Item.STATE_SELECTED) {
                    isBlock = true;
                    break;
                }
                count++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        return new WayData2(item, count, isBlock);
    }

    private void findExit(WayData tmp) {
        mItemStatus[mVerticalPos][mHorizontalPos] = Item.STATE_UNSELECTED;
        mItemStatus[tmp.y][tmp.x] = Item.STATE_OCCUPIED;

        Item newItem = mItems[tmp.y][tmp.x];
        Item oldItem = mItems[mVerticalPos][mHorizontalPos];
        oldItem.hideOccupiedImage();

        boolean isLeft = isLeft(tmp.x);
        mOccupiedView.setImageDrawable(isLeft ? mGoLeftAnimationDrawable : mGoRightAnimationDrawable);
        mOccupiedView.startAnimation(getWalkAnimation(oldItem, newItem, isLeft));
        mVerticalPos = tmp.y;
        mHorizontalPos = tmp.x;
    }

    private boolean isLeft(int x) {
        if (x == mHorizontalPos) {
            return mVerticalPos % 2 == 0;
        } else {
            return x < mHorizontalPos;
        }
    }

    private int[][] copyItemStatus() {
        int[][] result = new int[mVerticalCount][mHorizontalCount];
        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            System.arraycopy(mItemStatus[vertical], 0, result[vertical], 0, mHorizontalCount);
        }
        return result;
    }

    private void clearAllSelected() {
        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                mItems[vertical][horizontal].setStatus(Item.STATE_UNSELECTED);
                mItemStatus[vertical][horizontal] = Item.STATE_UNSELECTED;
            }
        }
    }

    private void setRandomSelected() {
        int selectedSize = (Math.min(mHorizontalCount, mVerticalCount) / 2) + 1;
        int tmp = 0;
        while (tmp < selectedSize) {
            int vertical = mRandom.nextInt(mVerticalCount);
            int horizontal = mRandom.nextInt(mHorizontalCount);
            if (mItemStatus[vertical][horizontal] == Item.STATE_UNSELECTED || mItemStatus[vertical][horizontal] == Item.STATE_GUIDE) {
                mItemStatus[vertical][horizontal] = Item.STATE_SELECTED;
                mItems[vertical][horizontal].setStatus(Item.STATE_SELECTED);
                tmp++;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec + mDropLeftAnimationDrawable.getIntrinsicHeight());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int currentWidth;
        int currentHeight;
        for (int vertical = 0; vertical < mVerticalCount; vertical++) {
            currentHeight = (mItemSize * vertical) + (mItemSpacing / 2 * vertical);
            for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                currentWidth = (mItemSize * horizontal) + (vertical % 2 == 0 ? mItemSize / 2 : 0) + (mItemSpacing * horizontal);
                mItems[vertical][horizontal].layout(currentWidth, currentHeight + mDropView.getLayoutParams().height,
                        currentWidth + mItemSize, currentHeight + mDropView.getLayoutParams().height + mItemSize);
            }
        }
        mSelectedView.layout(0, 0, mSelectedView.getLayoutParams().width, mSelectedView.getLayoutParams().height);
        mOccupiedView.layout(0, 0, mOccupiedView.getLayoutParams().width, mOccupiedView.getLayoutParams().height);
        mDropTouchView.layout(0, (int) ((mDropTouchView.getLayoutParams().height) - (mDropTouchView.getLayoutParams().height * .8)),
                mDropTouchView.getLayoutParams().width, (int) (mDropTouchView.getLayoutParams().height * .8));
        layoutDropTouchView(mItems[mVerticalPos][mHorizontalPos]);
        layoutDropView(mItems[mVerticalPos][mHorizontalPos]);
    }

    public void setOnOverListener(OnGameOverListener listener) {
        mOnGameOverListener = listener;
    }

    public Animation getFenceAnimation(final Item selectedItem) {
        selectedItem.startAnimation(getItemTouchAnimation());
        AnimationSet animationSet = new AnimationSet(false);
        animationSet.addAnimation(new ScaleAnimation(0, 1, 0, 1, Animation.RELATIVE_TO_SELF, .5F, Animation.RELATIVE_TO_SELF, .7F));
        TranslateAnimation translateAnimation = new TranslateAnimation(selectedItem.getLeft(), selectedItem.getLeft(),
                selectedItem.getBottom() - mSelectedView.getHeight(), selectedItem.getBottom() - mSelectedView.getHeight());
        animationSet.setDuration(300);
        animationSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mSelectedView.setVisibility(VISIBLE);
                selectedItem.hideSelectedImage();
                selectedItem.setStatus(Item.STATE_SELECTED);
                computeWay();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mSelectedView.setVisibility(INVISIBLE);
                selectedItem.showSelectedImage();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        animationSet.addAnimation(translateAnimation);
        return animationSet;
    }

    public Animation getItemTouchAnimation() {
        ScaleAnimation scaleAnimation = new ScaleAnimation(1, .7F, 1, .7F, Animation.RELATIVE_TO_SELF, .5F, Animation.RELATIVE_TO_SELF, .5F);
        scaleAnimation.setDuration(130);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        return scaleAnimation;
    }

    public BitmapDrawable getSelectedDrawable() {
        BitmapDrawable selectedDrawable = new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_selected));
        float selectedScale = getOccupiedScale(selectedDrawable);
        selectedDrawable = BitmapUtil.scaleDrawable(selectedDrawable, (int) (selectedDrawable.getBitmap().getWidth() * selectedScale),
                (int) (selectedDrawable.getBitmap().getHeight() * selectedScale));
        return selectedDrawable;
    }

    public BitmapDrawable getOccupiedDrawableLeft() {
        BitmapDrawable occupiedDrawableLeft = new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_occupied_left_0));
        float occupiedScale = getOccupiedScale(occupiedDrawableLeft);
        occupiedDrawableLeft = BitmapUtil.scaleDrawable(occupiedDrawableLeft, (int) (occupiedDrawableLeft.getBitmap().getWidth() * occupiedScale),
                (int) (occupiedDrawableLeft.getBitmap().getHeight() * occupiedScale));
        return occupiedDrawableLeft;
    }

    public BitmapDrawable getOccupiedDrawableRight() {
        BitmapDrawable occupiedDrawableRight = new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_occupied_right_0));
        float occupiedScale = getOccupiedScale(occupiedDrawableRight);
        occupiedDrawableRight = BitmapUtil.scaleDrawable(occupiedDrawableRight, (int) (occupiedDrawableRight.getBitmap().getWidth() * occupiedScale),
                (int) (occupiedDrawableRight.getBitmap().getHeight() * occupiedScale));
        return occupiedDrawableRight;
    }

    public BitmapDrawable getUnselectedDrawable() {
        BitmapDrawable unselectedDrawable = new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_unselected));
        float occupiedScale = getOccupiedScale(unselectedDrawable);
        unselectedDrawable = BitmapUtil.scaleDrawable(unselectedDrawable, (int) (unselectedDrawable.getBitmap().getWidth() * occupiedScale),
                (int) (unselectedDrawable.getBitmap().getHeight() * occupiedScale));
        return unselectedDrawable;
    }

    public BitmapDrawable getGuideDrawable() {
        BitmapDrawable guideDrawable = new BitmapDrawable(getResources(),
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_guide));
        float occupiedScale = getOccupiedScale(guideDrawable);
        guideDrawable = BitmapUtil.scaleDrawable(guideDrawable, (int) (guideDrawable.getBitmap().getWidth() * occupiedScale),
                (int) (guideDrawable.getBitmap().getHeight() * occupiedScale));
        return guideDrawable;
    }

    private float getOccupiedScale(BitmapDrawable occupiedDrawableRight) {
        return (float) mItemSize / occupiedDrawableRight.getBitmap().getWidth();
    }

    public TranslateAnimation getRunAnimation(final Item item, PositionData positionData) {
        if (positionData.startX < positionData.endX) {
            mOccupiedView.setImageDrawable(mGoRightAnimationDrawable);
        } else if (positionData.startX > positionData.endX) {
            mOccupiedView.setImageDrawable(mGoLeftAnimationDrawable);
        } else {
            mOccupiedView.setImageDrawable(item.isLeft() ? mGoLeftAnimationDrawable : mGoRightAnimationDrawable);
        }
        TranslateAnimation animation = new TranslateAnimation(
                positionData.startX, positionData.endX, positionData.startY, positionData.endY);
        animation.setDuration(600);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mGoLeftAnimationDrawable.start();
                mGoRightAnimationDrawable.start();
                mOccupiedView.setVisibility(VISIBLE);
                item.setStatus(Item.STATE_UNSELECTED);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mOccupiedView.setVisibility(INVISIBLE);
                mGoLeftAnimationDrawable.stop();
                mGoRightAnimationDrawable.stop();
                isGameOver = true;
                mOnGameOverListener.onLost();
                isAnimationPlaying = false;
                mDropTouchView.setOnTouchListener(mDropTouchListener);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        return animation;
    }

    private TranslateAnimation getWalkAnimation(final Item oldItem, final Item newItem, final boolean isLeft) {
        TranslateAnimation animation = new TranslateAnimation(oldItem.getX(), newItem.getX(),
                oldItem.getBottom() - mOccupiedView.getHeight(), newItem.getBottom() - mOccupiedView.getHeight());
        animation.setDuration(400);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mGoLeftAnimationDrawable.start();
                mGoRightAnimationDrawable.start();
                mOccupiedView.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mOccupiedView.setVisibility(INVISIBLE);
                mGoLeftAnimationDrawable.stop();
                mGoRightAnimationDrawable.stop();
                oldItem.setIsLeft(isLeft);
                oldItem.setStatus(Item.STATE_UNSELECTED);
                newItem.setIsLeft(isLeft);
                newItem.setStatus(Item.STATE_OCCUPIED);
                requestLayout();
                isAnimationPlaying = false;
                mDropTouchView.setOnTouchListener(mDropTouchListener);
                if (isNavigationOn) {
                    List<WayData> guide = ComputeWayUtil.findWay2(mItemStatus, new WayData(mHorizontalPos, mVerticalPos));
                    if (guide != null && !guide.isEmpty()) {
                        guide.remove(0);
                        for (WayData tmp : guide) {
                            mItems[tmp.y][tmp.x].setStatus(Item.STATE_GUIDE);
                        }
                    }
                    for (int vertical = 0; vertical < mVerticalCount; vertical++) {
                        for (int horizontal = 0; horizontal < mHorizontalCount; horizontal++) {
                            if (mItems[vertical][horizontal].getStatus() == Item.STATE_GUIDE) {
                                if (guide != null && guide.contains(new WayData(horizontal, vertical))) {
                                    continue;
                                }
                                mItems[vertical][horizontal].setStatus(Item.STATE_UNSELECTED);
                            }
                        }
                    }
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        return animation;
    }

    private void layoutDropTouchView(Item item) {
        mDropTouchView.layout(item.getLeft(), item.getTop() - ((int) (mDropTouchView.getHeight() / .8) - item.getHeight()), item.getLeft() + mDropTouchView.getWidth(),
                item.getTop() + mDropTouchView.getHeight() - ((int) (mDropTouchView.getHeight() / .8) - item.getHeight()));
    }

    private void layoutDropView(Item item) {
        MyLayoutParams layoutParams = (MyLayoutParams) mDropView.getLayoutParams();
        if (layoutParams.isDrag) {
            mDropView.layout(mDropView.getLeft() + layoutParams.x, mDropView.getTop() + layoutParams.y,
                    mDropView.getRight() + layoutParams.x, mDropView.getBottom() + layoutParams.y);
        } else {
            mDropView.layout(0, 0, mDropView.getLayoutParams().width, mDropView.getLayoutParams().height);
            mDropView.layout((int) item.getX() - (mDropView.getWidth() - item.getWidth()),
                    item.getBottom() - mDropView.getHeight(), (int) item.getX() + mDropView.getWidth(), item.getBottom());
        }
    }

    @Override
    protected MyLayoutParams generateDefaultLayoutParams() {
        return new MyLayoutParams(0, 0);
    }

    public void setOnPiggyDraggedListener(OnPiggyDraggedListener listener) {
        mOnPiggyDraggedListener = listener;
    }

    public interface OnGameOverListener {
        void onWin();

        void onLost();
    }

    private interface ComputeDirection {
        Item getItem(int count, boolean isSameGroup, int tmp);
    }

    public interface OnPiggyDraggedListener {
        void onDragged();
    }
}
