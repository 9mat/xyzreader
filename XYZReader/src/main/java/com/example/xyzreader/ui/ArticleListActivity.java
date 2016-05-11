package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.CardView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private SwipeRefreshLayout mSwipeRefreshLayout;

    static final String EXTRA_STARTING_POSITION = "extra_starting_item_position";
    static final String EXTRA_CURRENT_POSITION = "extra_current_item_position";

    private RecyclerView mRecyclerView;
    private Bundle mTmpReenterState;

    private boolean mIsDetailsActivityStarted;
    private static Map<Integer, String> sTransitionNameMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utility.sGlobalContext = this.getApplicationContext();

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            getWindow().getDecorView().setSystemUiVisibility(
//                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
//                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
//        }

        setContentView(R.layout.activity_article_list);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            SharedElementCallback mCallback = new SharedElementCallback() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                    if (mTmpReenterState != null) {
                        Log.i("SharedElementCallback", "onMapSharedElement:reentering");
                        int startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_POSITION);
                        int currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_POSITION);
                        if (startingPosition != currentPosition) {
                            // If startingPosition != currentPosition the user must have swiped to a
                            // different page in the DetailsActivity. We must update the shared element
                            // so that the correct one falls into place.
                            String newTransitionName = sTransitionNameMap.get(currentPosition);

                            View[] elements = new View[]{
                                    mRecyclerView.findViewWithTag(newTransitionName),
                                    mRecyclerView.findViewWithTag(Utility.makeTransitionName(newTransitionName, "container")),
                                    findViewById(android.R.id.statusBarBackground),
                                    findViewById(android.R.id.navigationBarBackground)
                            };

                            if (elements[0] != null) {
                                names.clear();
                                sharedElements.clear();

                                for (View v : elements) {
                                    if (v != null) {
                                        names.add(v.getTransitionName());
                                        sharedElements.put(v.getTransitionName(), v);
                                    }
                                }
                            }
                        }

                        mTmpReenterState = null;
                    } else {
                        // If mTmpReenterState is null, then the activity is exiting.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                            View navigationBar = findViewById(android.R.id.navigationBarBackground);
                            View statusBar = findViewById(android.R.id.statusBarBackground);
                            if (navigationBar != null) {
                                names.add(navigationBar.getTransitionName());
                                sharedElements.put(navigationBar.getTransitionName(), navigationBar);
                            }
                            if (statusBar != null) {
                                names.add(statusBar.getTransitionName());
                                sharedElements.put(statusBar.getTransitionName(), statusBar);
                            }
                        }
                    }
                }
            };
            setExitSharedElementCallback(mCallback);
        }

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);


        if (savedInstanceState == null) {
            refresh();
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsDetailsActivityStarted = false;
    }

    @Override
    public void onActivityReenter(int requestCode, Intent data) {
        super.onActivityReenter(requestCode, data);
        mTmpReenterState = new Bundle(data.getExtras());
        int startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_POSITION);
        int currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_POSITION);
        if (startingPosition != currentPosition) {
            scrollTo(mRecyclerView.getChildAt(currentPosition));
//            mRecyclerView.scrollToPosition(currentPosition);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.i(ArticleListActivity.class.getSimpleName(), "onActivityReenter");
                postponeEnterTransition();
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                public boolean onPreDraw() {
                    mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    // TODO: figure out why it is necessary to request layout here in order to get a smooth transition.
//                    mRecyclerView.requestLayout();
                    startPostponedEnterTransition();
                    return true;
                }
            });
        }
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor, this);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
//        StaggeredGridLayoutManager sglm =
//                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        GridLayoutManager layoutManager = new GridLayoutManager(this, columnCount);
        mRecyclerView.setLayoutManager(layoutManager);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    public void scrollTo(View view) {
        if(view == null) return;

        int cardTop = view.getTop();
        int cardBottom = view.getBottom();
        int recyclerViewHeight = mRecyclerView.getHeight();

        if(cardTop < 0) mRecyclerView.scrollBy(0, cardTop);
        else if(cardBottom > recyclerViewHeight) mRecyclerView.scrollBy(0, -recyclerViewHeight + cardBottom);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private Activity mActivity;
//        private int mPosition;

        public Adapter(Cursor cursor, Activity activity) {
            mCursor = cursor;
            mActivity = activity;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view, mActivity);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mIsDetailsActivityStarted) return;
                    mIsDetailsActivityStarted = true;

                    scrollTo(vh.mCardView);

                    Bundle bundle = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        bundle = ActivityOptions.makeSceneTransitionAnimation(
                                        ArticleListActivity.this,
                                        vh.getShareElementPairs()).toBundle();
                    }

                    final Bundle bundle1 = bundle;

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(Intent.ACTION_VIEW, ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                            intent.putExtra(EXTRA_STARTING_POSITION, vh.getAdapterPosition());
                            startActivity(intent, bundle1);
                        }
                    }, getResources().getInteger(R.integer.list_item_scroll_duration));
                }

            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
//            mPosition = position;
            mCursor.moveToPosition(position);

            Utility.ArticleInfoSimple articleInfo = new Utility.ArticleInfoSimple(mCursor);
            holder.bindViews(articleInfo);

            sTransitionNameMap.put(position, Utility.makeTransitionName(articleInfo.id));
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private DynamicHeightNetworkImageView thumbnailView;
        private TextView titleView;
        private TextView subtitleView;
        private View thumbnailContainer;
        private Activity mActivity;
        private CardView mCardView;

        public ViewHolder(View view, Activity activity) {
            super(view);
            thumbnailView       = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView           = (TextView) view.findViewById(R.id.article_title);
            subtitleView        = (TextView) view.findViewById(R.id.article_subtitle);
            thumbnailContainer  = view.findViewById(R.id.thumbnail_container);
            mActivity           = activity;
            mCardView           = (CardView) view.findViewById(R.id.card_view);
        }

        public void bindViews(final Utility.ArticleInfoSimple articleInfo) {

            titleView.setText(articleInfo.title);
            subtitleView.setText(mActivity.getResources().getString(
                    R.string.author_date_str_plain,
                    Utility.makeDateString(articleInfo.date),
                    articleInfo.author));

            paintTextView(articleInfo.id);

            if(!Utility.hasArticleColor(articleInfo.id))
                thumbnailView.setOnSetImageBitmapListener(new DynamicHeightNetworkImageView.OnSetImageBitmapListener() {
                    @Override
                    public void onSetImageBitmap(Bitmap bitmap) {
                        if(bitmap != null)
                            Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                                @Override
                                public void onGenerated(Palette palette) {
                                    int color = palette.getDarkMutedColor(Utility.getPrimaryDarkColor(mActivity));
                                    Utility.updateArticleColorMap(articleInfo.id, color);
                                    paintTextView(articleInfo.id);
                                }
                            });

                    }
                });
            else {
                // remove the listener to prevent RecyclerView from reusing the wrong listener
                thumbnailView.setOnSetImageBitmapListener(null);
                paintTextView(articleInfo.id);
            }

            thumbnailView.setImageUrl(articleInfo.photoUrl,
                    ImageLoaderHelper.getInstance(mActivity).getImageLoader());

            thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnailView.setAspectRatio(1.5f);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                String transitionName = Utility.makeTransitionName(articleInfo.id);
                String transitionNameContainer = Utility.makeTransitionName(articleInfo.id, "container");

                thumbnailView.setTransitionName(transitionName);
                thumbnailView.setTag(transitionName);
                thumbnailContainer.setTransitionName(transitionNameContainer);
                thumbnailContainer.setTag(transitionNameContainer);
            }

        }

        public void paintTextView(long id) {
            if(Utility.hasArticleColor(id)) {
                int color = Utility.getArticleColor(id);
                titleView.setBackgroundColor(color);
                subtitleView.setBackgroundColor(color);
//                mCardView.setBackgroundColor(color);
            }
        }

        private class PairViewString extends Pair<View, String> {
            public PairViewString(View first, String second) {
                super(first, second);
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public PairViewString[] getShareElementPairs(){
            View[] sharedViews = new View[] {
                    thumbnailView,
                    thumbnailContainer,
                    mActivity.findViewById(android.R.id.statusBarBackground),
                    mActivity.findViewById(android.R.id.navigationBarBackground)
            };

            PairViewString[] sharedElements = new PairViewString[sharedViews.length];

            int i = 0;
            for (View v: sharedViews) sharedElements[i++] = new PairViewString(v, v.getTransitionName());

            return sharedElements;
        }

    }
}
