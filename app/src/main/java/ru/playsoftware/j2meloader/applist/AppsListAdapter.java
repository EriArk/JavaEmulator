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

import android.graphics.Bitmap;
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
import java.util.Comparator;
import java.util.List;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.databinding.ListRowJarBinding;

public class AppsListAdapter extends RecyclerView.Adapter<AppsListAdapter.ViewHolder> implements Filterable {
	public static final int MODE_GALLERY = 0;
	public static final int MODE_LIST = 1;
	public static final int MODE_GRID = 2;
	public static final int CATEGORY_LIBRARY = 0;
	public static final int CATEGORY_RECENT = 1;
	public static final int CATEGORY_FAVORITES = 2;

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
	private int category = CATEGORY_LIBRARY;
	private int availableHeight = Integer.MAX_VALUE;
	private boolean compact;
	private String focusedPath;

	public void setCompact(boolean value) {
		if (compact != value) { compact = value; notifyDataSetChanged(); }
	}

	public void setAvailableHeight(int height) {
		int bounded = Math.max(64, Math.min(154, height - 74));
		if (availableHeight != bounded) {
			availableHeight = bounded;
			notifyDataSetChanged();
		}
	}

	public AppsListAdapter(Listener listener) {
		this.listener = new Listener() {
			public void onAppClicked(AppItem item) { listener.onAppClicked(item); }
			public void onAppFocused(AppItem item) {
				focusedPath = item == null ? null : item.getPath();
				listener.onAppFocused(item);
			}
			public void onAppActionsRequested(View anchor, AppItem item) { listener.onAppActionsRequested(anchor, item); }
		};
		setHasStableIds(true);
	}

	@Override public long getItemId(int position) { return filteredList.get(position).getId(); }

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

	public void setCategory(int category) {
		this.category = category;
		appFilter.filter(filterConstraint);
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
		holder.bind(item, displayMode, availableHeight, compact, listener);
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
			binding.rowRoot.setFocusableInTouchMode(true);
		}

		private void bind(AppItem item, int displayMode, int availableHeight, boolean compact, Listener listener) {
			applyMode(displayMode, availableHeight, compact);
			Bitmap icon = IconArtUtils.loadLargeIcon(item.getImagePathExt(), iconBitmapSize(displayMode));
			Drawable cover = Drawable.createFromPath(item.getCoverPathExt());
			if (displayMode == MODE_GALLERY && cover != null) {
				cover.setFilterBitmap(false);
				binding.cover.setVisibility(View.VISIBLE);
				binding.cover.setImageDrawable(cover);
			} else if (displayMode == MODE_GALLERY) {
				binding.cover.setVisibility(View.VISIBLE);
				binding.cover.setImageBitmap(IconArtUtils.createFallback(item.getTitle(),
						dp(360), dp(200), icon));
			} else {
				binding.cover.setImageDrawable(null);
				binding.cover.setVisibility(View.GONE);
			}
			if (icon != null) {
				binding.icon.setImageBitmap(icon);
			} else {
				binding.icon.setImageBitmap(IconArtUtils.createFallback(item.getTitle(),
						iconBitmapSize(displayMode), iconBitmapSize(displayMode), null));
			}
			binding.icon.setVisibility(displayMode == MODE_GALLERY ? View.GONE : View.VISIBLE);
			binding.name.setText(item.getTitle());
			binding.name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
			binding.name.setTextSize(15);
			binding.name.setMinLines(displayMode == MODE_LIST ? 1 : 2);
			binding.author.setVisibility(displayMode == MODE_LIST ? View.VISIBLE : View.GONE);
			binding.appVersion.setVisibility(View.GONE);
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

		private void applyMode(int mode, int availableHeight, boolean compact) {
			LinearLayout root = binding.rowRoot;
			FrameLayout art = binding.artContainer;
			LinearLayout text = binding.textColumn;
			int pad = dp(compact && mode == MODE_LIST ? 4 : 7);
			root.setPadding(pad, pad, pad, pad);
			ViewGroup.MarginLayoutParams itemLp = getMarginLayoutParams(root);
			itemLp.setMargins(dp(4), dp(4), dp(4), dp(4));
			root.setLayoutParams(itemLp);
			if (mode == MODE_LIST) {
				root.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(compact ? 52 : 72), dp(compact ? 52 : 72));
				artLp.setMargins(0, 0, dp(10), 0);
				art.setLayoutParams(artLp);
				LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
						ViewGroup.LayoutParams.WRAP_CONTENT, 1);
				textLp.setMargins(0, 0, 0, 0);
				text.setLayoutParams(textLp);
				binding.icon.setPadding(dp(3), dp(3), dp(3), dp(3));
				binding.icon.setLayoutParams(centerIconParams(dp(compact ? 48 : 64)));
				binding.name.setMaxLines(1);
			} else {
				root.setOrientation(LinearLayout.VERTICAL);
				int artHeight = dp(Math.min(mode == MODE_GALLERY ? 154 : 116, availableHeight));
				LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, artHeight);
				artLp.setMargins(0, 0, 0, 0);
				art.setLayoutParams(artLp);
				LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				textLp.setMargins(0, dp(8), 0, 0);
				text.setLayoutParams(textLp);
				binding.icon.setPadding(dp(3), dp(3), dp(3), dp(3));
				binding.icon.setLayoutParams(centerIconParams(Math.min(dp(108), artHeight - dp(8))));
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

		private int iconBitmapSize(int mode) {
			if (mode == MODE_LIST) {
				return dp(58);
			}
			return dp(108);
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
			ArrayList<AppItem> resultList = new ArrayList<>();
			String needle = constraint == null ? "" : constraint.toString().toLowerCase();
			for (AppItem item : list) {
				if (category == CATEGORY_RECENT && item.getLastPlayedAt() == 0) {
					continue;
				}
				if (category == CATEGORY_FAVORITES && !item.isFavorite()) {
					continue;
				}
				if (!TextUtils.isEmpty(needle)) {
					String title = item.getTitle() == null ? "" : item.getTitle();
					String author = item.getAuthor() == null ? "" : item.getAuthor();
					if (!title.toLowerCase().contains(needle)
							&& !author.toLowerCase().contains(needle)) {
						continue;
					}
				}
				resultList.add(item);
			}
			if (category == CATEGORY_RECENT) {
				resultList.sort(Comparator.comparingLong(AppItem::getLastPlayedAt).reversed());
			}
			results.count = resultList.size();
			results.values = resultList;
			return results;
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			filterConstraint = constraint;
			if (results.values != null) {
				//noinspection unchecked
				filteredList = (List<AppItem>) results.values;
				AppItem selected = getFirstItem();
				for (AppItem item : filteredList) {
					if (item.getPath().equals(focusedPath)) { selected = item; break; }
				}
				notifyDataSetChanged();
				listener.onAppFocused(selected);
			} else {
				notifyDataSetChanged();
			}
		}
	}
}
