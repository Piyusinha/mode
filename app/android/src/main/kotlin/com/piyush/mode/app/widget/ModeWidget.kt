package com.piyush.mode.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.piyush.mode.app.MainActivity
import com.piyush.mode.app.ModeApplication
import com.piyush.mode.app.R
import com.piyush.mode.app.shell.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home screen widget (board "Home screen widget"): tapping it opens Mode, the mic starts talking mode, and
 * the two suggestions (tasks done often, routines Mode learned) run straight away in a new chat.
 */
class ModeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every Mode widget (after a task, so the suggestions keep up). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ModeWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Main).launch { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val suggestions = suggestions(context)
            val views = RemoteViews(context.packageName, R.layout.widget_mode)
            views.setOnClickPendingIntent(R.id.widget_root, link(context, "${DeepLink.SCHEME}://open"))
            views.setOnClickPendingIntent(R.id.widget_mic, link(context, "${DeepLink.SCHEME}://listen"))
            views.setViewVisibility(R.id.widget_suggestions, if (suggestions.isEmpty()) View.GONE else View.VISIBLE)
            listOf(R.id.widget_suggestion_1, R.id.widget_suggestion_2).forEachIndexed { i, id ->
                val text = suggestions.getOrNull(i)
                views.setViewVisibility(id, if (text == null) View.GONE else View.VISIBLE)
                if (text != null) {
                    views.setTextViewText(id, text)
                    views.setOnClickPendingIntent(id, link(context, DeepLink.run(text)))
                }
            }
            manager.updateAppWidget(ids, views)
        }

        /** Home's suggestions, read once (they come from past tasks, so they may take a moment). */
        private suspend fun suggestions(context: Context): List<String> {
            val presenter = ModeApplication.graph(context).screens.home()
            return try {
                withTimeoutOrNull(WAIT_MS) { presenter.state.first { it.suggestions.isNotEmpty() } }?.suggestions?.map { it.text }?.take(SHOWN).orEmpty()
            } finally {
                presenter.close()
            }
        }

        private fun link(context: Context, uri: String): PendingIntent = PendingIntent.getActivity(
            context, uri.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private const val WAIT_MS = 2_000L
        private const val SHOWN = 2
    }
}
