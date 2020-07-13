/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.supportv7.widget;

import android.graphics.Color;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.android.supportv7.Cheeses;
import com.example.android.supportv7.R;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A sample nested RecyclerView activity.
 */
public class NestedRecyclerViewActivity extends BaseLayoutManagerActivity<LinearLayoutManager> {
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    }

    @Override
    protected void onRecyclerViewInit(RecyclerView recyclerView) {
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, mLayoutManager.getOrientation()));
    }

    static class InnerAdapter extends RecyclerView.Adapter<InnerAdapter.ViewHolder> {
        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView mTextView;

            public ViewHolder(TextView itemView) {
                super(itemView);
                mTextView = itemView;
            }
        }

        private String[] mData;

        public InnerAdapter(String[] data) {
            mData = data;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            TextView textView = (TextView) inflater.inflate(R.layout.nested_item, parent, false);
            textView.setMinimumWidth(300);
            textView.setMinimumHeight(300);
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.mTextView.setText(mData[position]);
            boolean even = position % 2 == 0;
            holder.mTextView.setBackgroundColor(even ? Color.TRANSPARENT : 0x2fffffff);
        }

        @Override
        public int getItemCount() {
            return mData.length;
        }
    }

    static class OuterAdapter extends RecyclerView.Adapter<OuterAdapter.ViewHolder> {
        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final RecyclerView mRecyclerView;
            public ViewHolder(RecyclerView itemView) {
                super(itemView);
                mRecyclerView = itemView;
            }
        }

        ArrayList<InnerAdapter> mAdapters = new ArrayList<>();
        RecyclerView.RecycledViewPool mSharedPool = new RecyclerView.RecycledViewPool();

        public OuterAdapter(String[] data) {
            int currentCharIndex = 0;
            char currentChar = data[0].charAt(0);
            for (int i = 1; i <= data.length; i++) {
                if (i == data.length || data[i].charAt(0) != currentChar) {
                    mAdapters.add(new InnerAdapter(
                            Arrays.copyOfRange(data, currentCharIndex, i - 1)));
                    if (i < data.length) {
                        currentChar = data[i].charAt(0);
                        currentCharIndex = i;
                    }
                }
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView rv = new RecyclerView(parent.getContext());
            rv.setLayoutManager(new LinearLayoutManager(parent.getContext(),
                    LinearLayoutManager.HORIZONTAL, false));
            rv.setRecycledViewPool(mSharedPool);
            return new ViewHolder(rv);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.mRecyclerView.setAdapter(mAdapters.get(position));
        }

        @Override
        public int getItemCount() {
            return mAdapters.size();
        }
    }

    protected RecyclerView.Adapter createAdapter() {
        return new OuterAdapter(Cheeses.sCheeseStrings);
    }
}
