/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.applist;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ListRowJarBinding;

public class AppsListAdapter extends RecyclerView.Adapter<AppsListAdapter.ViewHolder> implements Filterable {
	public static final int MODE_GALLERY = 0;
	public static final int MODE_LIST = 1;
	public static final int MODE_GRID = 2;

	interface Listener {
		void onAppClicked(AppItem item);
		void onAppFocused(AppItem item);
		void onAppActionsRequested(View anchor, AppItem item);
	}

	private final Listener listener;
	private List<AppItem> list = new ArrayList<>();
	private List<AppItem> filteredList = new ArrayList<>();
	private final AppFilter appFilter = new AppFilter();
	private CharSequence filterConstraint;
	private int displayMode = MODE_GALLERY;

	public AppsListAdapter(Listener listener) {
		this.listener = listener;
	}

	@Override
	public int getItemCount() {
		return filteredList.size();
	}

	public AppItem getItem(int position) {
		return filteredList.get(position);
	}

	public AppItem getFirstItem() {
		return filteredList.isEmpty() ? null : filteredList.get(0);
	}

	public void setDisplayMode(int displayMode) {
		if (this.displayMode == displayMode) {
			return;
		}
		this.displayMode = displayMode;
		notifyDataSetChanged();
	}

	public int getDisplayMode() {
		return displayMode;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		ListRowJarBinding binding = ListRowJarBinding.inflate(
				LayoutInflater.from(parent.getContext()), parent, false);
		return new ViewHolder(binding);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		AppItem item = filteredList.get(position);
		holder.bind(item, displayMode, listener);
	}

	public void setItems(List<AppItem> items) {
		list = items;
		appFilter.filter(filterConstraint);
	}

	@Override
	public Filter getFilter() {
		return appFilter;
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		private final ListRowJarBinding binding;

		private ViewHolder(ListRowJarBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
		}

		private void bind(AppItem item, int displayMode, Listener listener) {
			applyMode(displayMode);
			Drawable cover = Drawable.createFromPath(item.getCoverPathExt());
			if (cover != null) {
				cover.setFilterBitmap(false);
				binding.cover.setVisibility(View.VISIBLE);
				binding.cover.setImageDrawable(cover);
			} else {
				binding.cover.setImageDrawable(null);
				binding.cover.setVisibility(View.GONE);
			}
			Drawable icon = Drawable.createFromPath(item.getImagePathExt());
			if (icon != null) {
				icon.setFilterBitmap(false);
				binding.icon.setImageDrawable(icon);
			} else {
				binding.icon.setImageResource(R.mipmap.ic_launcher);
			}
			binding.name.setText(item.getTitle());
			binding.author.setText(item.getAuthor() == null ? "" : item.getAuthor());
			binding.appVersion.setText(item.getVersion() == null ? "" : item.getVersion());
			binding.getRoot().setOnClickListener(v -> listener.onAppClicked(item));
			binding.getRoot().setOnLongClickListener(v -> {
				listener.onAppActionsRequested(v, item);
				return true;
			});
			binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
				if (hasFocus) {
					listener.onAppFocused(item);
				}
			});
		}

		private void applyMode(int mode) {
			LinearLayout root = binding.rowRoot;
			FrameLayout art = binding.artContainer;
			LinearLayout text = binding.textColumn;
			int pad = dp(7);
			root.setPadding(pad, pad, pad, pad);
			ViewGroup.MarginLayoutParams itemLp = getMarginLayoutParams(root);
			itemLp.setMargins(dp(4), dp(4), dp(4), dp(4));
			root.setLayoutParams(itemLp);
			if (mode == MODE_LIST) {
				root.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(58), dp(58));
				artLp.setMargins(0, 0, dp(10), 0);
				art.setLayoutParams(artLp);
				LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
						ViewGroup.LayoutParams.WRAP_CONTENT, 1);
				textLp.setMargins(0, 0, 0, 0);
				text.setLayoutParams(textLp);
				binding.icon.setLayoutParams(centerIconParams(dp(38)));
				binding.name.setMaxLines(1);
			} else {
				root.setOrientation(LinearLayout.VERTICAL);
				int artHeight = mode == MODE_GALLERY ? dp(118) : dp(86);
				LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, artHeight);
				artLp.setMargins(0, 0, 0, 0);
				art.setLayoutParams(artLp);
				LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				textLp.setMargins(0, dp(8), 0, 0);
				text.setLayoutParams(textLp);
				binding.icon.setLayoutParams(cornerIconParams(mode == MODE_GALLERY ? dp(40) : dp(32)));
				binding.name.setMaxLines(2);
			}
		}

		private ViewGroup.MarginLayoutParams getMarginLayoutParams(View view) {
			ViewGroup.LayoutParams lp = view.getLayoutParams();
			if (lp instanceof ViewGroup.MarginLayoutParams) {
				return (ViewGroup.MarginLayoutParams) lp;
			}
			return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
		}

		private FrameLayout.LayoutParams centerIconParams(int size) {
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
			lp.gravity = android.view.Gravity.CENTER;
			return lp;
		}

		private FrameLayout.LayoutParams cornerIconParams(int size) {
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
			lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
			lp.setMargins(dp(8), dp(8), dp(8), dp(8));
			return lp;
		}

		private int dp(int value) {
			float density = binding.getRoot().getResources().getDisplayMetrics().density;
			return Math.round(value * density);
		}
	}

	private class AppFilter extends Filter {

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();
			if (TextUtils.isEmpty(constraint)) {
				results.count = list.size();
				results.values = list;
			} else {
				ArrayList<AppItem> resultList = new ArrayList<>();
				String needle = constraint.toString().toLowerCase();
				for (AppItem item : list) {
					String title = item.getTitle() == null ? "" : item.getTitle();
					String author = item.getAuthor() == null ? "" : item.getAuthor();
					if (title.toLowerCase().contains(needle)
							|| author.toLowerCase().contains(needle)) {
						resultList.add(item);
					}
				}
				results.count = resultList.size();
				results.values = resultList;
			}
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			filterConstraint = constraint;
			if (results.values != null) {
				//noinspection unchecked
				filteredList = (List<AppItem>) results.values;
				notifyDataSetChanged();
				listener.onAppFocused(getFirstItem());
			} else {
				notifyDataSetChanged();
			}
		}
	}
}
