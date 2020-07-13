/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.android.leanback;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class DetailsFragment extends android.support.v17.leanback.app.DetailsFragment {
    private static final String TAG = "leanback.DetailsFragment";
    private static final String ITEM = "item";

    private static final int NUM_ROWS = 3;
    private ArrayObjectAdapter mRowsAdapter;
    private PhotoItem mPhotoItem;
    final CardPresenter cardPresenter = new CardPresenter();
    private BackgroundHelper mBackgroundHelper = new BackgroundHelper();

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_RENT = 2;
    private static final int ACTION_BUY = 3;

    private static final boolean TEST_SHARED_ELEMENT_TRANSITION = true;
    private static final boolean TEST_ENTRANCE_TRANSITION = true;

    private static final long TIME_TO_LOAD_OVERVIEW_ROW_MS = 1000;
    private static final long TIME_TO_LOAD_RELATED_ROWS_MS = 2000;

    private Action mActionPlay;
    private Action mActionRent;
    private Action mActionBuy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.ic_title));
        setTitle("Leanback Sample App");
        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        mActionPlay = new Action(ACTION_PLAY, "Play");
        mActionRent = new Action(ACTION_RENT, "Rent", "$3.99",
                getResources().getDrawable(R.drawable.ic_action_a));
        mActionBuy = new Action(ACTION_BUY, "Buy $9.99");

        ClassPresenterSelector ps = new ClassPresenterSelector();
        DetailsOverviewRowPresenter dorPresenter =
                new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        dorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                Toast.makeText(getActivity(), action.toString(), Toast.LENGTH_SHORT).show();
                DetailsOverviewRow dor = (DetailsOverviewRow) mRowsAdapter.get(0);
                if (action.getId() == ACTION_BUY) {
                    // on the UI thread, we can modify actions adapter directly
                    SparseArrayObjectAdapter actions = (SparseArrayObjectAdapter)
                            dor.getActionsAdapter();
                    actions.set(ACTION_PLAY, mActionPlay);
                    actions.clear(ACTION_RENT);
                    actions.clear(ACTION_BUY);
                    dor.setItem(mPhotoItem.getTitle() + "(Owned)");
                    dor.setImageDrawable(getResources().getDrawable(R.drawable.details_img_16x9));
                } else if (action.getId() == ACTION_RENT) {
                    // on the UI thread, we can modify actions adapter directly
                    SparseArrayObjectAdapter actions = (SparseArrayObjectAdapter)
                            dor.getActionsAdapter();
                    actions.set(ACTION_PLAY, mActionPlay);
                    actions.clear(ACTION_RENT);
                    dor.setItem(mPhotoItem.getTitle() + "(Rented)");
                } else if (action.getId() == ACTION_PLAY) {
                    Intent intent = new Intent(getActivity(), PlaybackOverlayActivity.class);
                    getActivity().startActivity(intent);
                }
            }
        });

        ps.addClassPresenter(DetailsOverviewRow.class, dorPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());

        mRowsAdapter = new ArrayObjectAdapter(ps);

        PhotoItem item = (PhotoItem) (savedInstanceState != null ?
                savedInstanceState.getParcelable(ITEM) : null);
        if (item != null) {
            setItem(item);
        }

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemClicked: " + item + " row " + row);
                if (item instanceof PhotoItem){
                    Intent intent = new Intent(getActivity(), DetailsActivity.class);
                    intent.putExtra(DetailsActivity.EXTRA_ITEM, (PhotoItem) item);

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView)itemViewHolder.view).getMainImageView(),
                            DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    getActivity().startActivity(intent, bundle);
                }
            }
        });
        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemSelected: " + item + " row " + row);
            }
        });

        if (TEST_SHARED_ELEMENT_TRANSITION) {
            dorPresenter.setSharedElementEnterTransition(getActivity(),
                    DetailsActivity.SHARED_ELEMENT_NAME);
        }
        if (TEST_ENTRANCE_TRANSITION) {
            // don't run entrance transition if Activity is restored.
            if (savedInstanceState == null) {
                prepareEntranceTransition();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ITEM, mPhotoItem);
    }

    public void setItem(PhotoItem photoItem) {
        mPhotoItem = photoItem;

        mRowsAdapter.clear();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                Resources res = getActivity().getResources();
                DetailsOverviewRow dor = new DetailsOverviewRow(mPhotoItem.getTitle());
                dor.setImageDrawable(res.getDrawable(mPhotoItem.getImageResourceId()));
                SparseArrayObjectAdapter adapter = new SparseArrayObjectAdapter();
                adapter.set(ACTION_RENT, mActionRent);
                adapter.set(ACTION_BUY, mActionBuy);
                dor.setActionsAdapter(adapter);
                mRowsAdapter.add(0, dor);
                setSelectedPosition(0, false);
            }
        }, TIME_TO_LOAD_OVERVIEW_ROW_MS);

        new Handler().postDelayed(new Runnable() {
            public void run() {
                for (int i = 0; i < NUM_ROWS; ++i) {
                    ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
                    listRowAdapter.add(new PhotoItem("Hello world", R.drawable.gallery_photo_1));
                    listRowAdapter.add(new PhotoItem("This is a test", R.drawable.gallery_photo_2));
                    listRowAdapter.add(new PhotoItem("Android TV", R.drawable.gallery_photo_3));
                    listRowAdapter.add(new PhotoItem("Leanback", R.drawable.gallery_photo_4));
                    HeaderItem header = new HeaderItem(i, "Row " + i);
                    mRowsAdapter.add(new ListRow(header, listRowAdapter));
                }
                if (TEST_ENTRANCE_TRANSITION) {
                    startEntranceTransition();
                }
            }
        }, TIME_TO_LOAD_RELATED_ROWS_MS);
        setAdapter(mRowsAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mPhotoItem != null) {
            mBackgroundHelper.setBackground(
                    getActivity(), mPhotoItem.getImageResourceId());
        }
    }

}
