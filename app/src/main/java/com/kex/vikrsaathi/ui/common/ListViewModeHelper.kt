package com.kex.vikrsaathi.ui.common

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kex.vikrsaathi.util.ListViewMode

fun RecyclerView.applyListViewMode(mode: ListViewMode) {
    val firstVisible = when (val lm = layoutManager) {
        is LinearLayoutManager -> lm.findFirstVisibleItemPosition()
        is GridLayoutManager -> lm.findFirstVisibleItemPosition()
        else -> 0
    }
    layoutManager = when (mode) {
        ListViewMode.COMPACT -> GridLayoutManager(context, 2)
        ListViewMode.COMFORTABLE, ListViewMode.DETAILS -> LinearLayoutManager(context)
    }
    if (firstVisible >= 0) {
        post { layoutManager?.scrollToPosition(firstVisible) }
    }
}
