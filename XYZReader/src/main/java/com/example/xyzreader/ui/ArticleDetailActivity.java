package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import java.util.List;
import java.util.Map;


import static com.example.xyzreader.ui.ArticleListActivity.EXTRA_CURRENT_POSITION;
import static com.example.xyzreader.ui.ArticleListActivity.EXTRA_STARTING_POSITION;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */

/**
 * see the following link for shared-element transition animation in viewpager
 * https://github.com/alexjlockwood/activity-transitions/blob/master/app/src/main/java/com/alexjlockwood/activity/transitions/DetailsActivity.java
 */

public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private Cursor mCursor;
    private long mStartId;

    private long mSelectedItemId;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;

    private static final String STATE_CURRENT_PAGE_POSITION= "state_current_page_position";

    private ArticleDetailFragment mCurrentDetailsFragment;
    private int mCurrentPosition;
    private int mStartingPosition;
    private boolean mIsReturning;

    @Override
    public void onBackPressed() {
        mCurrentDetailsFragment.expandAppBar();
        final ArticleDetailActivity activity = this;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mCurrentDetailsFragment.setUserVisibleHint(false);
                activity.finishAfterTransition();
            }
        }, getResources().getInteger(R.integer.detail_expanding_appbar_duration));
    }

    @Override
    protected void onResume() {
        updateStatusBarColor();
        super.onResume();
    }

    @Override
    public void onEnterAnimationComplete() {
        if(mCurrentDetailsFragment != null) {
            View view = mCurrentDetailsFragment.getTitleView();
            view.setAlpha(0f);
            view.animate()
                    .setDuration(getResources().getInteger(R.integer.detail_title_alpha_animation_duration))
                    .alpha(1f);
        }
        super.onEnterAnimationComplete();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            getWindow().getDecorView().setSystemUiVisibility(
//                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            postponeEnterTransition();
            setContentView(R.layout.activity_article_detail);
            SharedElementCallback mCallback = new SharedElementCallback() {
                @Override
                public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                    Log.i(SharedElementCallback.class.getSimpleName(), "onMapSharedElements");
                    if (mIsReturning) {
                        ImageView sharedElement = mCurrentDetailsFragment.getPhotoView();
                        if (sharedElement == null) {
                            // If shared element is null, then it has been scrolled off screen and
                            // no longer visible. In this case we cancel the shared element transition by
                            // removing the shared element from the shared elements map.
                            names.clear();
                            sharedElements.clear();
                        } else if (mStartingPosition != mCurrentPosition) {
                            // If the user has swiped to a different ViewPager page, then we need to
                            // remove the old shared element and replace it with the new shared element
                            // that should be transitioned instead.
                            names.clear();
                            sharedElements.clear();

                            for (View v : mCurrentDetailsFragment.getSharedElements()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    names.add(v.getTransitionName());
                                    sharedElements.put(v.getTransitionName(), v);
                                }
                            }
                        }
                    }

                }
            };
            setEnterSharedElementCallback(mCallback);
        } else {
            setContentView(R.layout.activity_article_detail);
        }

        mStartingPosition = getIntent().getIntExtra(EXTRA_STARTING_POSITION, 0);
        if (savedInstanceState == null) {
            mCurrentPosition = mStartingPosition;
        } else {
            mCurrentPosition = savedInstanceState.getInt(STATE_CURRENT_PAGE_POSITION);
        }

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                    mSelectedItemId = mCursor.getLong(ArticleLoader.Query._ID);
                    if(mCurrentDetailsFragment != null) mCurrentDetailsFragment.paintGradientBackground();
                    updateStatusBarColor();
                }
                mCurrentPosition = position;
            }
        });

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                mStartId = ItemsContract.Items.getItemId(getIntent().getData());
                mSelectedItemId = mStartId;
            }
        }
        updateStatusBarColor();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_PAGE_POSITION, mCurrentPosition);
    }

    public long getSelectedItemId () {
        return mSelectedItemId;
    }


    @Override
    public void finishAfterTransition() {
        mIsReturning = true;
        Intent data = new Intent();
        data.putExtra(EXTRA_CURRENT_POSITION, mCurrentPosition);
        data.putExtra(EXTRA_STARTING_POSITION, mStartingPosition);
        setResult(RESULT_OK, data);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) super.finishAfterTransition();
        else finish();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

        // Select the start ID
        if (mStartId > 0) {
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mStartId) {
                    final int position = mCursor.getPosition();
                    mPager.setCurrentItem(position, false);
                    break;
                }
                mCursor.moveToNext();
            }
            mStartId = 0;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    public void updateStatusBarColor() {
        if (Utility.hasArticleColor(mSelectedItemId)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(Utility.getArticleColor(mSelectedItemId));
            }
        }
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {

        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mCurrentDetailsFragment = (ArticleDetailFragment) object;
            if(mCurrentDetailsFragment != null) mCurrentDetailsFragment.paintGradientBackground();
        }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);
            return ArticleDetailFragment.newInstance(mCursor.getLong(ArticleLoader.Query._ID), position, mStartingPosition);
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }
    }
}
