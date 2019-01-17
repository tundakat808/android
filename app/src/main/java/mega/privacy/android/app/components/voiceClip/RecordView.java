package mega.privacy.android.app.components.voiceClip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.content.res.AppCompatResources;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;

import io.supercharge.shimmerlayout.ShimmerLayout;
import mega.privacy.android.app.R;

import mega.privacy.android.app.lollipop.megachat.ChatActivityLollipop;
import mega.privacy.android.app.utils.Util;

public class RecordView extends RelativeLayout {

    public enum UserBehaviour {
        CANCELING,
        LOCKING,
        NONE
    }

    public static final int DEFAULT_CANCEL_BOUNDS = 1; //8dp
    private ImageView smallBlinkingMic, basketImg;
    private Chronometer counterTime;
    private TextView slideToCancel;
    private ShimmerLayout slideToCancelLayout;
    private RelativeLayout cancelRecordLayout;
    private ImageView arrow;
    private float initialX,initialY, basketInitialY, difX = 0;
    private float basketInitialX = 0;
    private float heightButtom = 0;

    private float cancelBounds = DEFAULT_CANCEL_BOUNDS;
    private long startTime, elapsedTime = 0;
    private Context context;
    private OnRecordListener recordListener;
    private boolean isSwiped, isLessThanSecondAllowed = false;
    private boolean isSoundEnabled = true;
    private int RECORD_START = R.raw.record_start;
    private int RECORD_FINISHED = R.raw.record_finished;
    private int RECORD_ERROR = R.raw.record_error;
    private MediaPlayer player;
    private AnimationHelper animationHelper;
    private View layoutLock;
    private ImageView imageLock, imageArrow;
    private boolean flagRB = false;
    private boolean isLockpadShown = false;
    Handler handlerStartRecord = new Handler();
    Handler handlerShowPadLock = new Handler();
    float previewX = 0;
    float previewY = 0;
    private Animation animJump, animJumpFast;
    int cont = 0;

    float density;
    DisplayMetrics outMetrics;
    Display display;

    private float lastX, lastY;
    private float firstX, firstY;

    private float directionOffset, cancelOffset, lockOffset;
    private boolean isLocked = false;
    private UserBehaviour userBehaviour = UserBehaviour.NONE;


    public RecordView(Context context) {
        super(context);
        this.context = context;
        init(context, null, -1, -1);
    }

    public RecordView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context, attrs, -1, -1);
    }

    public RecordView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init(context, attrs, defStyleAttr, -1);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        View view = View.inflate(context, R.layout.record_view_layout, null);
        addView(view);

        ViewGroup viewGroup = (ViewGroup) view.getParent();
        viewGroup.setClipChildren(false);

        display = ((Activity)context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);
        density  = getResources().getDisplayMetrics().density;

        arrow = view.findViewById(R.id.arrow);
        slideToCancel = view.findViewById(R.id.slide_to_cancel);
        smallBlinkingMic = view.findViewById(R.id.glowing_mic);
        counterTime = view.findViewById(R.id.counter_tv);
        basketImg = view.findViewById(R.id.basket_img);
        slideToCancelLayout = view.findViewById(R.id.shimmer_layout);
        cancelRecordLayout = view.findViewById(R.id.rl_cancel_record);
        layoutLock = view.findViewById(R.id.layout_lock);
        imageLock = view.findViewById(R.id.image_lock);
        imageArrow = view.findViewById(R.id.image_arrow);
        imageLock.setVisibility(GONE);
        imageArrow.setVisibility(GONE);
        animJump = AnimationUtils.loadAnimation(getContext(), R.anim.jump);
        animJumpFast = AnimationUtils.loadAnimation(getContext(), R.anim.jump_fast);
        layoutLock.setVisibility(GONE);
        cancelRecordLayout.setVisibility(GONE);
        cancelRecordLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log("cancelRecordLayout. onClick()");
                hideViews(false);
                if(animationHelper!=null){
                    animationHelper.animateBasket(basketInitialX);
                    animationHelper.setStartRecorded(false);
                }
                if(counterTime!=null){
                    counterTime.stop();
                }
                if (recordListener != null) {
                    recordListener.onCancel();
                }
            }
        });

        hideViews(true);

        animationHelper = new AnimationHelper(context, basketImg, smallBlinkingMic);
    }

    private void hideViews(boolean hideSmallMic) {
        log("hideViews()");
        slideToCancelLayout.setVisibility(GONE);
        cancelRecordLayout.setVisibility(GONE);
        counterTime.setVisibility(GONE);
        if (hideSmallMic){
            smallBlinkingMic.setVisibility(GONE);
        }
    }

    private void showViews() {
        log("showViews()");
        slideToCancelLayout.setVisibility(VISIBLE);
        cancelRecordLayout.setVisibility(GONE);
        smallBlinkingMic.setVisibility(VISIBLE);
        counterTime.setVisibility(VISIBLE);
    }

    public void showLock(boolean flag){
        log("showLock() -> "+flag);
        if(flag){
            cont = 0;
            if(layoutLock.getVisibility() == View.GONE){
                layoutLock.setVisibility(View.VISIBLE);
                int prevHeight  = layoutLock.getHeight();
                ValueAnimator valueAnimator = ValueAnimator.ofInt(prevHeight, Util.px2dp(125, outMetrics));
                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        layoutLock.getLayoutParams().height = (int) animation.getAnimatedValue();
                        layoutLock.requestLayout();
                    }
                });
                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        imageArrow.setVisibility(VISIBLE);
                        imageLock.setVisibility(VISIBLE);
                        if((imageArrow!=null)&& (imageLock!=null)){
                            imageArrow.clearAnimation();
                            imageLock.clearAnimation();
                            imageArrow.startAnimation(animJumpFast);
                            imageLock.startAnimation(animJump);
                            isLockpadShown = true;
                        }
                    }
                });
                valueAnimator.setInterpolator(new DecelerateInterpolator());
                valueAnimator.setDuration(500);
                valueAnimator.start();
            }
        }else{
            flagRB = false;
            cont ++;
            if(cont == 1) {
                if (layoutLock.getVisibility() == View.VISIBLE) {
                    layoutLock.setVisibility(View.GONE);
                    isLockpadShown = false;
                    int prevHeight = layoutLock.getHeight();
                    ValueAnimator valueAnimator = ValueAnimator.ofInt(prevHeight, 10);
                    valueAnimator.setInterpolator(new DecelerateInterpolator());
                    valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            layoutLock.getLayoutParams().height = (int) animation.getAnimatedValue();
                            layoutLock.requestLayout();
                        }
                    });
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if ((imageArrow != null) && (imageLock != null)) {
                                imageArrow.clearAnimation();
                                imageLock.clearAnimation();
                                imageArrow.startAnimation(animJumpFast);
                                imageLock.startAnimation(animJump);
                            }
                            imageArrow.setVisibility(GONE);
                            imageLock.setVisibility(GONE);
                            layoutLock.setVisibility(View.GONE);
                        }
                    });
                    valueAnimator.setInterpolator(new DecelerateInterpolator());
                    valueAnimator.setDuration(100);
                    valueAnimator.start();
                }
            }
        }
    }

    private boolean isLessThanOneSecond(long time) {
        return time <= 1500;
    }

    public void playSound(int soundRes) {
        log("playSound()");
        if (isSoundEnabled) {
            if (soundRes == 0)
                return;
            try {
                player = new MediaPlayer();
                AssetFileDescriptor afd = context.getResources().openRawResourceFd(soundRes);
                if (afd == null) return;
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.prepare();
                player.start();
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.release();
                    }
                });
                player.setLooping(false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    final Runnable runStartRecord = new Runnable(){
        @Override
        public void run() {
            if(flagRB){
                counterTime.setBase(SystemClock.elapsedRealtime());
                counterTime.start();
                handlerShowPadLock.postDelayed(runPadLock, 3000);
                if (recordListener != null) {
                    recordListener.onStart();
                }
            }
        }
    };

    final Runnable runPadLock = new Runnable(){
        @Override
        public void run() {
            if(flagRB){
                log("runPadLock() -> showLock");
                showLock(true);
            }
        }
    };

    protected void onActionDown(RecordButton recordBtn, MotionEvent motionEvent) {
        log("onActionDown()");
        animationHelper.setStartRecorded(true);
        animationHelper.resetBasketAnimation();
        animationHelper.resetSmallMic();
        slideToCancelLayout.startShimmerAnimation();
        initialX = recordBtn.getX();
        initialY = recordBtn.getY();
        heightButtom = recordBtn.getHeight();

        firstX = motionEvent.getRawX();
        firstY = motionEvent.getRawY();

//        lockOffset = (float) (recordBtn.getX() / 2.5);
        isLocked = false;
        playSound(RECORD_START);

        basketInitialY = basketImg.getY() + 90;
        basketInitialX = basketImg.getX() - 90;

        showViews();
        startTime = System.currentTimeMillis();
        isSwiped = false;
        animationHelper.animateSmallMicAlpha();
        handlerStartRecord.postDelayed(runStartRecord, 500); //500 milliseconds delay to record
        flagRB = true;
    }

    protected void onActionMove(RecordButton recordBtn, MotionEvent motionEvent) {
        log("onActionMove()");

        long time = System.currentTimeMillis() - startTime;
        if (!isSwiped) {
            UserBehaviour direction = UserBehaviour.NONE;
            float motionX = Math.abs(firstX - motionEvent.getRawX());
            float motionY = Math.abs(firstY - motionEvent.getRawY());

            if (motionX > directionOffset && motionX > directionOffset && lastX < firstX && lastY < firstY) {
                if (motionX > motionY && lastX < firstX) {
                    direction = UserBehaviour.CANCELING;
                } else if (motionY > motionX && lastY < firstY) {
                    direction = UserBehaviour.LOCKING;
                }

            } else if (motionX > motionY && motionX > directionOffset && lastX < firstX) {
                direction = UserBehaviour.CANCELING;

            } else if (motionY > motionX && motionY > directionOffset && lastY < firstY) {
                direction = UserBehaviour.LOCKING;
            }

            if (direction == UserBehaviour.CANCELING) {
                if (userBehaviour == UserBehaviour.NONE || motionEvent.getRawY() + recordBtn.getWidth() / 2 > firstY) {
                    if (slideToCancelLayout.getX() != 0 && slideToCancelLayout.getX() <= counterTime.getRight() + cancelBounds) {
                        //if the time was less than one second then do not start basket animation
                        if (isLessThanOneSecond(time)) {
                            log("onActionMove() CANCELING-> Swipe To Cancel less than one second --> no basket animation");
                            hideViews(true);
                            animationHelper.clearAlphaAnimation(false);
                            animationHelper.onAnimationEnd();
                        } else {
                            log("onActionMove() onActionMove() CANCELING->  Swipe To Cancel more than one second --> start basket animation");
                            hideViews(false);
                            animationHelper.animateBasket(basketInitialX);
                        }

                        if (recordListener != null) {
                            log("onActionMove() -> onCancel()");
                            recordListener.onCancel();
                            animationHelper.moveRecordButtonAndSlideToCancelBack(recordBtn, slideToCancelLayout, initialX, difX);
                            showLock(false);
                            counterTime.stop();
                            slideToCancelLayout.stopShimmerAnimation();
                            isSwiped = true;
                            animationHelper.setStartRecorded(false);
                            userBehaviour = UserBehaviour.CANCELING;
                        }

                    }else{
                        if(previewX == 0){
                            previewX = recordBtn.getX();
                        }else{
                            if(recordBtn.getX() <= (previewX - 25)){
                                showLock(false);
                            }
                        }
                        if (motionEvent.getRawX() < initialX) {
                            log("onActionMove() - to left");
                            recordBtn.animate().x(motionEvent.getRawX()).setDuration(0).start();
                            recordBtn.setTranslationY(0);
                            if (difX == 0) {
                                difX = (initialX - slideToCancelLayout.getX());
                            }
                            slideToCancelLayout.animate().x(motionEvent.getRawX() - difX).setDuration(0).start();
                        }
                        userBehaviour = UserBehaviour.NONE;
                    }
                }

            } else if (direction == UserBehaviour.LOCKING) {
                if (userBehaviour == UserBehaviour.NONE || motionEvent.getRawX() + recordBtn.getWidth() / 2 > firstX) {
                    if((layoutLock.getVisibility() == VISIBLE) && (isLockpadShown)){
                        if((firstY - motionEvent.getRawY()) >= (layoutLock.getHeight()- recordBtn.getHeight())){
                            if((!isLocked) && (recordListener != null)) {
                                log(" onActionMove() ----> LOCKING");
                                recordListener.onLock();
                                recordBtn.setTranslationY(0);
                                recordBtn.setTranslationX(0);
                                userBehaviour = UserBehaviour.LOCKING;
                                showLock(false);
                                slideToCancelLayout.stopShimmerAnimation();
                                slideToCancelLayout.setVisibility(GONE);
                                cancelRecordLayout.setVisibility(VISIBLE);
                                isLocked = true;
                                return;
                            }
                        }

                        recordBtn.setTranslationY(-(firstY - motionEvent.getRawY()));
                        recordBtn.setTranslationX(0);

//                        if ((-(firstY - motionEvent.getRawY())) < -lockOffset) {
//                            if(!isLocked){
//                                if (recordListener != null) {
//                                    log("onActionMove() LOCKING ");
//                                    recordListener.onLock();
//                                    recordBtn.setTranslationY(0);
//                                    recordBtn.setTranslationX(0);
//                                    userBehaviour = UserBehaviour.LOCKING;
//                                    showLock(false);
//                                    slideToCancelLayout.stopShimmerAnimation();
//                                    slideToCancelLayout.setVisibility(GONE);
//                                    cancelRecordLayout.setVisibility(VISIBLE);
//                                    isLocked = true;
//                                }
//                            }
//                            return;
//                        }
//                        recordBtn.setTranslationY(-(firstY - motionEvent.getRawY()));
//                        recordBtn.setTranslationX(0);
                    }
                }
            }

            lastX = motionEvent.getRawX();
            lastY = motionEvent.getRawY();
        }
    }

    protected void onActionUp(RecordButton recordBtn) {
        log("onActionUp()");
        elapsedTime = System.currentTimeMillis() - startTime;
        if (handlerShowPadLock != null){
            handlerShowPadLock.removeCallbacksAndMessages(null);
        }
        flagRB = false;
        if (!isLessThanSecondAllowed && isLessThanOneSecond(elapsedTime) && !isSwiped) {
            log("onActionUp() - less than a second");
            if (recordListener != null){
                recordListener.onLessThanSecond();
            }
            userBehaviour = UserBehaviour.NONE;
            animationHelper.setStartRecorded(false);
            firstX = 0;
            firstY = 0;
            lastX = 0;
            lastY = 0;
            recordBtn.setTranslationY(0);
            recordBtn.setTranslationX(0);
            playSound(RECORD_ERROR);

        }else{
            log("onActionUp() - more than a second");
            if(userBehaviour == UserBehaviour.LOCKING){
                log("onActionUp() - LOCKING()");
                showLock(false);
                firstX = 0;
                firstY = 0;
                lastX = 0;
                lastY = 0;
                userBehaviour = UserBehaviour.NONE;
                recordBtn.setTranslationY(0);
                recordBtn.setTranslationX(0);
                isLocked = false;
//                counterTime.stop();
//                slideToCancelLayout.stopShimmerAnimation();


            }else if(userBehaviour == UserBehaviour.CANCELING){
                log("onActionUp() - CANCELING()");

                animationHelper.setStartRecorded(false);
                animationHelper.moveRecordButtonAndSlideToCancelBack(recordBtn, slideToCancelLayout, initialX, difX);
                showLock(false);
                counterTime.stop();
                slideToCancelLayout.stopShimmerAnimation();
                recordBtn.setTranslationY(0);
                recordBtn.setTranslationX(0);
                firstX = 0;
                firstY = 0;
                lastX = 0;
                lastY = 0;
                userBehaviour = UserBehaviour.NONE;
                isLocked = false;

            }else{
                log("onActionUp() - NONE");
                if (recordListener != null && !isSwiped) {
                    recordListener.onFinish(elapsedTime);
                }
                animationHelper.setStartRecorded(false);
                if (!isSwiped) {
                    playSound(RECORD_FINISHED);
                }
                //if user has swiped then do not hide SmallMic since it will be hidden after swipe Animation
                hideViews(!isSwiped);
                if (!isSwiped) {
                    animationHelper.clearAlphaAnimation(true);
                }
                animationHelper.moveRecordButtonAndSlideToCancelBack(recordBtn, slideToCancelLayout, initialX, difX);
                showLock(false);
                counterTime.stop();
                slideToCancelLayout.stopShimmerAnimation();

                firstX = 0;
                firstY = 0;
                lastX = 0;
                lastY = 0;
                userBehaviour = UserBehaviour.NONE;
                recordBtn.setTranslationY(0);
                recordBtn.setTranslationX(0);
                isLocked = false;
            }
        }
    }

    public void setOnRecordListener(OnRecordListener recrodListener) {
        log("setOnRecordListener()");
        this.recordListener = recrodListener;
    }
    public void setOnBasketAnimationEndListener(OnBasketAnimationEnd onBasketAnimationEndListener) {
        log("setOnBasketAnimationEndListener()");
        animationHelper.setOnBasketAnimationEndListener(onBasketAnimationEndListener);
    }
    public void setSoundEnabled(boolean isEnabled) {
        isSoundEnabled = isEnabled;
    }
    public void setLessThanSecondAllowed(boolean isAllowed) {
        isLessThanSecondAllowed = isAllowed;
    }

    public void setCustomSounds(int startSound, int finishedSound, int errorSound) {
        //0 means do not play sound
        RECORD_START = startSound;
        RECORD_FINISHED = finishedSound;
        RECORD_ERROR = errorSound;
    }
    public float getCancelBounds() {
        return cancelBounds;
    }
    public void setCancelBounds(float cancelBounds) {
        setCancelBounds(cancelBounds, true);
    }
    //set Chronometer color
    public void setCounterTimeColor(int color) {
        counterTime.setTextColor(color);
    }
    private void setCancelBounds(float cancelBounds, boolean convertDpToPixel) {
        float bounds = convertDpToPixel ? Util.toPixel(cancelBounds, context) : cancelBounds;
        this.cancelBounds = bounds;
    }

    public static void collapse(final View v, int duration, int targetHeight) {
        log("collapse()");
        int prevHeight  = v.getHeight();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(prevHeight, targetHeight);
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                v.getLayoutParams().height = (int) animation.getAnimatedValue();
                v.requestLayout();
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

    public void destroyHandlers(){
        if (handlerStartRecord != null){
            handlerStartRecord.removeCallbacksAndMessages(null);
        }
        if (handlerShowPadLock != null){
            handlerShowPadLock.removeCallbacksAndMessages(null);
        }
        if(imageLock!=null){
            imageLock.clearAnimation();
        }
        if(imageArrow!=null){
            imageArrow.clearAnimation();
        }

    }
    public static void log(String message) {
        Util.log("RecordView",message);
    }

}
