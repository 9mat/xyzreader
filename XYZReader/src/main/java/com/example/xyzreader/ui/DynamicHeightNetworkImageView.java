package com.example.xyzreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;


public class DynamicHeightNetworkImageView extends NetworkImageView {

    private String mUrl;

    public interface OnSetImageBitmapListener {
        void onSetImageBitmap(Bitmap bitmap);
    }
    private float mAspectRatio = 1.5f;
    private OnSetImageBitmapListener mOnSetImageBitmapListener;

    public DynamicHeightNetworkImageView(Context context) {
        super(context);
    }

    public DynamicHeightNetworkImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DynamicHeightNetworkImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(float aspectRatio) {
        mAspectRatio = aspectRatio;
        requestLayout();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int measuredWidth = getMeasuredWidth();
        setMeasuredDimension(measuredWidth, (int) (measuredWidth / mAspectRatio));
    }

    public void setOnSetImageBitmapListener(OnSetImageBitmapListener listener) {
        mOnSetImageBitmapListener = listener;
    }

    @Override
    public void setImageUrl(String url, ImageLoader imageLoader) {
        mUrl = url;
        super.setImageUrl(url, imageLoader);
    }

    public String getUrl() {
        return mUrl;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        if(mOnSetImageBitmapListener != null)
            mOnSetImageBitmapListener.onSetImageBitmap(bm);
        super.setImageBitmap(bm);
    }
}
