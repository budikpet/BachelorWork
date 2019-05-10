package cz.budikpet.bachelorwork.screens.calendarListView

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import cz.budikpet.bachelorwork.R
import cz.budikpet.bachelorwork.util.toDp


class CalendarsListSwipeDelete(private val context: Context, private val listener: Callback) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private var icon = ContextCompat.getDrawable(context, R.drawable.ic_delete_black_24dp)!!
    private var background = ColorDrawable(Color.RED)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        p2: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        listener.onSwipeDelete(position)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(
            c, recyclerView, viewHolder, dX,
            dY, actionState, isCurrentlyActive
        )
        val itemView = viewHolder.itemView
        val backgroundCornerOffset = 20

        val iconMargin = 16
        val iconTop = (itemView.top + (itemView.height - icon.intrinsicHeight) / 2)
        val iconBottom = iconTop + icon.intrinsicHeight

        when {
            dX < 0 -> { // Swiping to the left
                val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                val iconRight = itemView.right - iconMargin
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                background.setBounds(
                    itemView.right + dX.toInt() - backgroundCornerOffset,
                    itemView.top, itemView.right, itemView.bottom
                )
            }
            else -> // view is unSwiped
                background.setBounds(0, 0, 0, 0)
        }

        background.draw(c)
        icon.draw(c)
    }

    interface Callback {
        fun onSwipeDelete(position: Int)
    }

}