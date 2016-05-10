package com.example.xyzreader.ui;

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
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
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

    public static String generateTransitionName(long position){
        return "transition_" + String.valueOf(position);
    }

    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
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

                    View[] elements = new View[] {
                            mRecyclerView.findViewWithTag(newTransitionName),
//                            mRecyclerView.findViewWithTag(newTransitionName + "_title"),
//                            mRecyclerView.findViewWithTag(newTransitionName + "_author_date"),
                            mRecyclerView.findViewWithTag(newTransitionName + "_container"),
                            findViewById(android.R.id.statusBarBackground),
                            findViewById(android.R.id.navigationBarBackground)
                    } ;

                    if (elements[0] != null) {
                        names.clear();
                        sharedElements.clear();

                        for(View v: elements) {
                            if(v != null){
                                names.add(v.getTransitionName());
                                sharedElements.put(v.getTransitionName(), v);
                            }
                        }
                    }
                }

                mTmpReenterState = null;
            } else {
                // If mTmpReenterState is null, then the activity is exiting.
                Log.i("SharedElementCallback", "onMapSharedElement:exiting");
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


    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utility.sGlobalContext = this.getApplicationContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            getWindow().getDecorView().setSystemUiVisibility(
//                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
//                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        setContentView(R.layout.activity_article_list);
        setExitSharedElementCallback(mCallback);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);


//        getWindow().setExitTransition(null);
//        getWindow().setEnterTransition(null);

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
            mRecyclerView.scrollToPosition(currentPosition);
        }
        if(Utility.POST_LOLLIPOP) {
            Log.i(ArticleListActivity.class.getSimpleName(), "onActivityReenter");
            postponeEnterTransition();
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
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

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private Activity mActivity;
        private int mPosition;

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

                    Bundle bundle = null;
                    if (Utility.POST_LOLLIPOP)
                        bundle = ActivityOptions.makeSceneTransitionAnimation(
                                ArticleListActivity.this,
                                vh.getShareElementPairs()).toBundle();

                    Intent intent = new Intent(Intent.ACTION_VIEW, ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    intent.putExtra(EXTRA_STARTING_POSITION, mPosition);
                    startActivity(intent, bundle);
                }

            });
            return vh;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            mPosition = position;
            mCursor.moveToPosition(position);

            Utility.ArticleInfoSimple articleInfo = new Utility.ArticleInfoSimple(mCursor);
            holder.bindView(articleInfo);

            sTransitionNameMap.put(position, ArticleListActivity.generateTransitionName(articleInfo.id));
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
        private View mCardView;
        private Activity mActivity;

        public ViewHolder(View view, Activity activity) {
            super(view);
            thumbnailView       = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView           = (TextView) view.findViewById(R.id.article_title);
            subtitleView        = (TextView) view.findViewById(R.id.article_subtitle);
            thumbnailContainer  = view.findViewById(R.id.thumbnail_container);
            mCardView           = view.findViewById(R.id.card_view);
            mActivity           = activity;
        }

        public void bindView(final Utility.ArticleInfoSimple articleInfo) {

            String dateStr  = Utility.makeDateString(articleInfo.date);
            titleView.setText(articleInfo.title);
            subtitleView.setText(mActivity.getResources().getString(R.string.author_date_str_plain, dateStr, articleInfo.author));

            paintTextView(articleInfo.id);

            if(!Utility.hasArticleColor(articleInfo.id))
                thumbnailView.setOnSetImageBitmapListener(new DynamicHeightNetworkImageView.OnSetImageBitmapListener() {
                    @Override
                    public void onSetImageBitmap(Bitmap bitmap) {
                        if(bitmap != null)
                            Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                                @Override
                                public void onGenerated(Palette palette) {
                                    int color = palette.getDarkMutedColor(0xFF333333);
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

            if(Utility.POST_LOLLIPOP){
                String transitionName = generateTransitionName(articleInfo.id);
                String transitionNameContainer = transitionName + "_container";

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
                mCardView.setBackgroundColor(color);
            }
        }

        private class PairViewString extends Pair<View, String> {
            public PairViewString(View first, String second) {
                super(first, second);
            }
        }

        public PairViewString[] getShareElementPairs(){
            View[] sharedViews = new View[] {
                    thumbnailView,
//                    titleView,
//                    subtitleView,
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
