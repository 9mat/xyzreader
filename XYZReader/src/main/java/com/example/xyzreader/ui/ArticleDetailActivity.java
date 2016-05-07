package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.transition.Transition;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import java.util.HashMap;
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
    private int mSelectedItemUpButtonFloor = Integer.MAX_VALUE;
    private int mTopInset;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;

    private static Map<Long, Integer> mStatusBarColorMap = new HashMap<>();

    private static final String STATE_CURRENT_PAGE_ITEM_ID = "state_current_page_item_id";
    private static final String STATE_CURRENT_PAGE_POSITION= "state_current_page_position";

    private ArticleDetailFragment mCurrentDetailsFragment;
    private int mCurrentPosition;
    private int mStartingPosition;
    private boolean mIsReturning;

    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements, List<View> sharedElementSnapshots) {
            Log.i(SharedElementCallback.class.getSimpleName(), "onSharedElementStart");
            mCurrentDetailsFragment.scrollToTop();
            super.onSharedElementStart(sharedElementNames, sharedElements, sharedElementSnapshots);
        }

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
                    View[] elements = new View[] { sharedElement,
                            mCurrentDetailsFragment.getTitleView(),
                            mCurrentDetailsFragment.getAuthorDateView()};
                    names.clear();
                    sharedElements.clear();

                    for(View v : elements){
                        names.add(v.getTransitionName());
                        sharedElements.put(v.getTransitionName(), v);
                    }
                }
            }

        }
    };

    @Override
    public void onBackPressed() {
        Log.i(ArticleListActivity.class.getSimpleName(), "onBackPressed");
        mCurrentDetailsFragment.scrollToTop();
        final ArticleDetailActivity activity = this;

        AsyncTask<Void,Void, Void> pauseTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                activity.supportFinishAfterTransition();
                super.onPostExecute(aVoid);
            }
        };

        pauseTask.execute();


    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        postponeEnterTransition();
        setContentView(R.layout.activity_article_detail);
        setEnterSharedElementCallback(mCallback);
        setExitSharedElementCallback(new SharedElementCallback() {
            @Override
            public View onCreateSnapshotView(Context context, Parcelable snapshot) {
                mCurrentDetailsFragment.scrollToTop();
                return super.onCreateSnapshotView(context, snapshot);
            }
        });

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

//        mPager.setPageMargin((int) TypedValue
//                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
//        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }

            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                    mSelectedItemId = mCursor.getLong(ArticleLoader.Query._ID);
                    updateStatusBarColor(mSelectedItemId);
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

    }

//    public void scheduleStartPostponedTransition(final View sharedElement) {
//        sharedElement.getViewTreeObserver().addOnPreDrawListener(
//                new ViewTreeObserver.OnPreDrawListener() {
//                    @Override
//                    public boolean onPreDraw() {
//                        sharedElement.getViewTreeObserver().removeOnPreDrawListener(this);
//                        startPostponedEnterTransition();
//                        return true;
//                    }
//                });
//    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_PAGE_POSITION, mCurrentPosition);
    }


    @Override
    public void finishAfterTransition() {
        mIsReturning = true;
        Intent data = new Intent();
        data.putExtra(EXTRA_CURRENT_POSITION, mCurrentPosition);
        data.putExtra(EXTRA_STARTING_POSITION, mStartingPosition);
        setResult(RESULT_OK, data);
        super.finishAfterTransition();
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

    public static void updateStatusBarColorMap(long id, int color) {
        if(!mStatusBarColorMap.containsKey(id)) mStatusBarColorMap.put(id, color);
    }

    public void updateStatusBarColor(long id) {
        if (id == mSelectedItemId & mStatusBarColorMap.containsKey(id))
            getWindow().setStatusBarColor(mStatusBarColorMap.get(id));
    }


    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mCurrentDetailsFragment = (ArticleDetailFragment) object;
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
