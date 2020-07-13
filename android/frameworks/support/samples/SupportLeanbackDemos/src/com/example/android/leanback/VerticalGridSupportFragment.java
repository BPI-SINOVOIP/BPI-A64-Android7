/* This file is auto-generated from VerticalGridFragment.  DO NOT MODIFY. */

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
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridPresenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class VerticalGridSupportFragment extends android.support.v17.leanback.app.VerticalGridSupportFragment {
    private static final String TAG = "leanback.VerticalGridSupportFragment";

    private static final int NUM_COLUMNS = 3;
    private static final int NUM_ITEMS = 50;
    private static final int HEIGHT = 200;
    private static final boolean TEST_ENTRANCE_TRANSITION = true;

    private static class Adapter extends ArrayObjectAdapter {
        public Adapter(StringPresenter presenter) {
            super(presenter);
        }
        public void callNotifyChanged() {
            super.notifyChanged();
        }
    }
    private Adapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.ic_title));
        setTitle("Leanback Vertical Grid Demo");

        setupFragment();
        if (TEST_ENTRANCE_TRANSITION) {
            // don't run entrance transition if fragment is restored.
            if (savedInstanceState == null) {
                prepareEntranceTransition();
            }
        }
        // simulates in a real world use case  data being loaded two seconds later
        new Handler().postDelayed(new Runnable() {
            public void run() {
                loadData();
                startEntranceTransition();
            }
        }, 2000);
    }

    private void loadData() {
        for (int i = 0; i < NUM_ITEMS; i++) {
            mAdapter.add(Integer.toString(i));
        }
    }

    private void setupFragment() {
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(NUM_COLUMNS);
        setGridPresenter(gridPresenter);

        mAdapter = new Adapter(new StringPresenter());
        setAdapter(mAdapter);

        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemSelected: " + item + " row " + row);
            }
        });

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemClicked: " + item + " row " + row);
                mAdapter.callNotifyChanged();
            }
        });
        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchSupportActivity.class);
                startActivity(intent);
            }
        });
    }
}
