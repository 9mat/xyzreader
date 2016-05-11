package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>
        , AppBarLayout.OnOffsetChangedListener
{
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;

    private static final float PERCENTAGE_TO_SHOW_TITLE_AT_TOOLBAR  = 0.9f;
    private static final float PERCENTAGE_TO_HIDE_TITLE_DETAILS     = 0.5f;
    private static final int ALPHA_ANIMATIONS_DURATION              = 200;

    private boolean mIsTheTitleVisible          = false;
    private boolean mIsTheTitleContainerVisible = true;

    private static final String ARG_CURRENT_POSITION = "arg_album_image_position";
    private static final String ARG_STARTING_POSITION = "arg_starting_album_image_position";

    private boolean mIsEnterTransitionStarted = false;
    private boolean mIsPhotoLoaded = false;

    private boolean mIsTimeOutTransitionStart;

    @Nullable
    ImageView getPhotoView() {
        if (isViewInBounds(getActivity().getWindow().getDecorView(), mHolder.photo)) {
            return mHolder.photo;
        }
        return null;
    }

    public boolean getIsEnterTransitionStarted() {
        return mIsEnterTransitionStarted;
    }

    View[] getSharedElements(){
        return mHolder.getSharedElements();
    }

    private static boolean isViewInBounds(@NonNull View container, @NonNull View view) {
        Rect containerBounds = new Rect();
        container.getHitRect(containerBounds);
        return view.getLocalVisibleRect(containerBounds);
    }

    public View getTitleView() {
        return mHolder.titleContainer;
    }

    private class ViewHolder {
        public TextView title, collapsedTitle, authorDate, body;
        public ImageView photo;
        public LinearLayout titleContainer;
        private View gradientView;
        public AppBarLayout scrollView;

        private long mArticleId;

        public ViewHolder(View root, long id) {
            photo               = (ImageView) root.findViewById(R.id.photo);
            titleContainer      = (LinearLayout) root.findViewById(R.id.meta_bar);
            collapsedTitle      = (TextView) root.findViewById(R.id.collapsed_title);
            gradientView        = root.findViewById(R.id.gradient_background);
            title               = (TextView) root.findViewById(R.id.article_title);
            authorDate          = (TextView) root.findViewById(R.id.article_byline);
            body                = (TextView) root.findViewById(R.id.article_body);
            scrollView          = (AppBarLayout) root.findViewById(R.id.appbar_detail);
            mArticleId          = id;

            paintGradientBackground();
        }

        public void bindViews(final Utility.ArticleInfo articleInfo) {

            mArticleId = articleInfo.id;

            title.setText(articleInfo.title);
            body.setText(Html.fromHtml(articleInfo.body));
            collapsedTitle.setText(articleInfo.title);

            authorDate.setText(Html.fromHtml(getString(
                    R.string.author_date_str_html,
                    Utility.makeDateString(articleInfo.date),
                    articleInfo.author
            )));

            paintGradientBackground();

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                photo.setTransitionName(Utility.makeTransitionName(articleInfo.id));
                gradientView.setTransitionName(Utility.makeTransitionName(articleInfo.id, "container"));
            }

            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(articleInfo.photoUrl, new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                if (!Utility.hasArticleColor(articleInfo.id))
                                    Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                                        @Override
                                        public void onGenerated(Palette palette) {
                                            Utility.updateArticleColorMap(articleInfo.id,
                                                    palette.getDarkMutedColor(Utility.getPrimaryDarkColor(getActivity())));
                                            paintGradientBackground();
                                            updateStatusBarColor();
                                            startPostponedEnterTransition();
                                        }
                                    });

                                photo.setImageBitmap(bitmap);
                                mIsPhotoLoaded = true;
                                startPostponedEnterTransition();
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {
                        }
                    });

            startPostponedEnterTransition();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public View[] getSharedElements(){
            return new View[] {
                    photo,
                    gradientView,
                    getActivity().findViewById(android.R.id.statusBarBackground),
                    getActivity().findViewById(android.R.id.navigationBarBackground)
            };
        }

        public void clearViews() {
            String noDataStr = getString(R.string.no_data_string);
            mHolder.title.setText(noDataStr);
            mHolder.authorDate.setText(noDataStr);
            mHolder.body.setText(noDataStr);
        }

        public void paintGradientBackground(){
            if(Utility.hasArticleColor(mArticleId)) {
                ((GradientDrawable) gradientView.getBackground()).setColors(new int[]{
                                Utility.getArticleColor(mArticleId),
                                Color.TRANSPARENT
                });
            }
        }
    }

    public void paintGradientBackground() {
        if(mHolder != null) mHolder.paintGradientBackground();
    }

    private ViewHolder mHolder;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId, int position, int startingPosition) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        arguments.putInt(ARG_CURRENT_POSITION, position);
        arguments.putInt(ARG_STARTING_POSITION, startingPosition);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsTimeOutTransitionStart = false;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mIsTimeOutTransitionStart = true;
                startPostponedEnterTransition();
            }
        }, getResources().getInteger(R.integer.deltail_enter_transition_time_out));

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

    }

    public void expandAppBar(){
        mHolder.scrollView.setExpanded(true, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        updateStatusBarColor();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        if(savedInstanceState != null && savedInstanceState.containsKey(ARG_ITEM_ID)){
            mItemId = savedInstanceState.getLong(ARG_ITEM_ID);
        }

        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mHolder = new ViewHolder(mRootView, mItemId);

        updateStatusBarColor();

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        mIsEnterTransitionStarted = false;
        mIsPhotoLoaded = false;

        bindViews();

        mHolder.scrollView.addOnOffsetChangedListener(this);
        startAlphaAnimation(mHolder.collapsedTitle, 0, View.INVISIBLE);

        return mRootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong(ARG_ITEM_ID, mItemId);
        super.onSaveInstanceState(outState);

    }

    private void startPostponedEnterTransition() {
        if(mIsEnterTransitionStarted) return;
        if(!mIsTimeOutTransitionStart && (!Utility.hasArticleColor(mItemId) || !mIsPhotoLoaded)) return;

        mIsEnterTransitionStarted = true;
        mHolder.photo.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mHolder.photo.getViewTreeObserver().removeOnPreDrawListener(this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    getActivity().startPostponedEnterTransition();
                }
                return true;
            }
        });
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        mHolder.authorDate.setMovementMethod(new LinkMovementMethod());
        //mHolder.body.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);

            mHolder.bindViews(new Utility.ArticleInfo(mCursor));

        } else {
            mRootView.setVisibility(View.GONE);
            mHolder.clearViews();
        }
    }

    private ArticleDetailActivity getActivityCast(){
        return (ArticleDetailActivity) getActivity();
    }

    public void updateStatusBarColor(){
        ArticleDetailActivity activity = getActivityCast();
        if(activity != null && activity.getSelectedItemId() == mItemId)
            activity.updateStatusBarColor();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
        int maxScroll = appBarLayout.getTotalScrollRange();
        float percentage = (float) Math.abs(verticalOffset) / (float) maxScroll;

        handleAlphaOnTitle(percentage);
        handleToolbarTitleVisibility(percentage);
    }


    private void handleToolbarTitleVisibility(float percentage) {
        if (percentage >= PERCENTAGE_TO_SHOW_TITLE_AT_TOOLBAR) {
            if(!mIsTheTitleVisible) {
                startAlphaAnimation(mHolder.collapsedTitle, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                mIsTheTitleVisible = true;
            }

        } else {
            if (mIsTheTitleVisible) {
                startAlphaAnimation(mHolder.collapsedTitle, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                mIsTheTitleVisible = false;
            }
        }
    }

    private void handleAlphaOnTitle(float percentage) {
        if (percentage >= PERCENTAGE_TO_HIDE_TITLE_DETAILS) {
            if(mIsTheTitleContainerVisible) {
                startAlphaAnimation(mHolder.titleContainer, ALPHA_ANIMATIONS_DURATION, View.INVISIBLE);
                mIsTheTitleContainerVisible = false;
            }
        } else {
            if (!mIsTheTitleContainerVisible) {
                startAlphaAnimation(mHolder.titleContainer, ALPHA_ANIMATIONS_DURATION, View.VISIBLE);
                mIsTheTitleContainerVisible = true;
            }
        }
    }

    public static void startAlphaAnimation (View v, long duration, int visibility) {
        AlphaAnimation alphaAnimation = (visibility == View.VISIBLE)
                ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);

        alphaAnimation.setDuration(duration);
        alphaAnimation.setFillAfter(true);
        v.startAnimation(alphaAnimation);
    }

}
