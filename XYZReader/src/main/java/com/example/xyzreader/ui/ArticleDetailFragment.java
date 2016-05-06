package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.format.DateUtils;
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

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.util.HashMap;
import java.util.Map;

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
    private int mMutedColor = 0xFF333333;

    private static final float PERCENTAGE_TO_SHOW_TITLE_AT_TOOLBAR  = 0.9f;
    private static final float PERCENTAGE_TO_HIDE_TITLE_DETAILS     = 0.5f;
    private static final int ALPHA_ANIMATIONS_DURATION              = 200;

    private boolean mIsTheTitleVisible          = false;
    private boolean mIsTheTitleContainerVisible = true;

    private static Map<Long, Integer> mColorMap = new HashMap<>();

    private static final String ARG_CURRENT_POSITION = "arg_album_image_position";
    private static final String ARG_STARTING_POSITION = "arg_starting_album_image_position";
    private int mStartingPosition;
    private int mCurrentPosition;
    private boolean mIsTransitioning;
    private int mBackgroundImageFadeMillis;

    private final Callback mImageCallback = new Callback() {
        @Override
        public void onSuccess() {
            Bitmap bitmap = ((BitmapDrawable) mHolder.photo.getDrawable()).getBitmap();
            if(!mColorMap.containsKey(mItemId))
                Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        mMutedColor = palette.getDarkMutedColor(0xFF333333);
                        mHolder.gradientBackground.setColors(new int[]{mMutedColor, 0x00000000});
                        mColorMap.put(mItemId, mMutedColor);
                        ArticleDetailActivity.updateStatusBarColorMap(mItemId, mMutedColor);
                        updateStatusBarColor();
                    }
                });
            if(ArticleListActivity.POST_LOLLIPOP) startPostponedEnterTransition();
        }

        @Override
        public void onError() {
            Picasso.with(getActivity()).load(mCursor.getString(ArticleLoader.Query.THUMB_URL)).into(mHolder.photo, this);
        }
    };



    @Nullable
    ImageView getPhotoView() {
        if (isViewInBounds(getActivity().getWindow().getDecorView(), mHolder.photo)) {
            return mHolder.photo;
        }
        return null;
    }

    private static boolean isViewInBounds(@NonNull View container, @NonNull View view) {
        Rect containerBounds = new Rect();
        container.getHitRect(containerBounds);
        return view.getLocalVisibleRect(containerBounds);
    }


    private class ViewHolder {
        public TextView title, collapsedTitle, authorDate, body;
        public ImageView photo;
        public LinearLayout titleContainer;
        public GradientDrawable gradientBackground;
        public AppBarLayout scrollView;

        public ViewHolder(View root) {
            photo = (ImageView) root.findViewById(R.id.photo);
            titleContainer = (LinearLayout) root.findViewById(R.id.meta_bar);
            collapsedTitle = (TextView) root.findViewById(R.id.collapsed_title);
            gradientBackground = (GradientDrawable) root.findViewById(R.id.gradient_background).getBackground();
            title = (TextView) root.findViewById(R.id.article_title);
            authorDate = (TextView) root.findViewById(R.id.article_byline);
            body = (TextView) root.findViewById(R.id.article_body);
            scrollView = (AppBarLayout) root.findViewById(R.id.appbar);
        }
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

        mStartingPosition = getArguments().getInt(ARG_STARTING_POSITION);
        mCurrentPosition = getArguments().getInt(ARG_CURRENT_POSITION);
        mIsTransitioning = savedInstanceState == null && mStartingPosition == mCurrentPosition;
        // mBackgroundImageFadeMillis = getResources().getInteger(
        //        R.integer.fragment_details_background_image_fade_millis);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        if(mColorMap.containsKey(mItemId)) mMutedColor = mColorMap.get(mItemId);
        updateStatusBarColor();

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
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mHolder = new ViewHolder(mRootView);

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        mHolder.gradientBackground.setColors(new int[]{mMutedColor, 0x00000000});
        mHolder.scrollView.addOnOffsetChangedListener(this);
        startAlphaAnimation(mHolder.collapsedTitle, 0, View.INVISIBLE);


        bindViews();

        return mRootView;
    }


    private void startPostponedEnterTransition() {
        mHolder.photo.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mHolder.photo.getViewTreeObserver().removeOnPreDrawListener(this);
                getActivity().startPostponedEnterTransition();
                return true;
            }
        });
    }


    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        mHolder.authorDate.setMovementMethod(new LinkMovementMethod());
        mHolder.body.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);

            String title    = mCursor.getString(ArticleLoader.Query.TITLE);
            String body     = mCursor.getString(ArticleLoader.Query.BODY);
            String author   = mCursor.getString(ArticleLoader.Query.AUTHOR);
            String photoUrl = mCursor.getString(ArticleLoader.Query.THUMB_URL);
            long date       = mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE);

            String dateStr  = DateUtils.getRelativeTimeSpanString(date,
                    System.currentTimeMillis(),
                    DateUtils.HOUR_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_ALL).toString();

            mHolder.title.setText(title);
            mHolder.collapsedTitle.setText(title);
            mHolder.authorDate.setText(Html.fromHtml(dateStr + " by <font color='#ffffff'>" + author + "</font>"));
            mHolder.body.setText(Html.fromHtml(body));

            if(ArticleListActivity.POST_LOLLIPOP)
                mHolder.photo.setTransitionName(ArticleListActivity.generateTransitionName(
                        mCursor.getLong(ArticleLoader.Query._ID)
                ));


//            if (mIsTransitioning) {
//                albumImageRequest.noFade();
//            }

            Picasso.with(getActivity())
                    .load(photoUrl)
                    .into(mHolder.photo, mImageCallback);


//            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
//                    .get(photoUrl, new ImageLoader.ImageListener() {
//                        @Override
//                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
//                            Bitmap bitmap = imageContainer.getBitmap();
//                            if (bitmap != null) {
//                                if(!mColorMap.containsKey(mItemId))
//                                    Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
//                                        @Override
//                                        public void onGenerated(Palette palette) {
//                                            mMutedColor = palette.getDarkMutedColor(0xFF333333);
//                                            mHolder.gradientBackground.setColors(new int[]{mMutedColor, 0x00000000});
//                                            mColorMap.put(mItemId, mMutedColor);
//                                            ArticleDetailActivity.updateStatusBarColorMap(mItemId, mMutedColor);
//                                            updateStatusBarColor();
//                                        }
//                                    });
//                                startPostponedEnterTransition();
//                                mHolder.photo.setImageBitmap(bitmap);
//                            }
//                        }
//
//                        @Override
//                        public void onErrorResponse(VolleyError volleyError) {
//                            startPostponedEnterTransition();
//                        }
//                    });
        } else {
            mRootView.setVisibility(View.GONE);
            mHolder.title.setText("N/A");
            mHolder.authorDate.setText("N/A");
            mHolder.body.setText("N/A");
        }
    }

    private ArticleDetailActivity getActivityCast(){
        return (ArticleDetailActivity) getActivity();
    }

    public void updateStatusBarColor(){
        ArticleDetailActivity activity = getActivityCast();
        if(activity != null) activity.updateStatusBarColor(mItemId);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
//        getActivityCast().scheduleStartPostponedTransition(mHolder.photo);

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
