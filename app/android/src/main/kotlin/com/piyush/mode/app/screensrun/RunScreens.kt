package com.piyush.mode.app.screensrun

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.run.RunScreensPresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeChip
import com.piyush.mode.design.ModeEmptyState
import com.piyush.mode.design.ModeFixStepForm
import com.piyush.mode.design.ModeGlassSwitch
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeIconButton
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModeRadius
import com.piyush.mode.design.ModeScreenCell
import com.piyush.mode.design.ModeScreensHeader
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeTabItem
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.MoMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Shot(val step: Int, val number: String, val label: String, val image: ImageBitmap?)
enum class ScreensMode { Grid, Play }

/** All screens, as on its board: every screen Mode saw for a task, in a grid or played back. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RunScreensScreen(
    title: String,
    subtitle: String,
    shots: List<Shot>,
    mode: ScreensMode,
    onMode: (ScreensMode) -> Unit,
    playIndex: Int,
    onPlayIndex: (Int) -> Unit,
    savingFix: Boolean,
    fixReply: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpen: (Shot) -> Unit,
    onWrong: (Shot) -> Unit,
    onFixSeen: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ModePageBar(stringResource(R.string.common_back), onBack) {
                ModeIconButton(ModeIcon.Share, stringResource(R.string.conversation_share), onShare)
            }
            if (savingFix) {
                Row(Modifier.fillMaxWidth().padding(ModeSpacing.s), Arrangement.spacedBy(ModeSpacing.s, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                    ModeSpinner()
                    ModeText(stringResource(R.string.screens_saving_fix), ModeTextStyle.Subheadline, color = Mode.colors.textSecondary)
                }
            } else if (fixReply != null) {
                ModeChip(fixReply, Modifier.padding(ModeSpacing.s).align(Alignment.CenterHorizontally), icon = ModeIcon.Memory, action = stringResource(R.string.screens_ok), onAction = onFixSeen)
            }
            val header = @Composable { ModeScreensHeader(MoMood.Done, title, subtitle) }
            when {
                shots.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    ModeEmptyState(ModeIcon.Screens, stringResource(R.string.screens_empty_title), stringResource(R.string.screens_empty_message))
                }
                mode == ScreensMode.Grid -> LazyVerticalGrid(
                    GridCells.Fixed(COLUMNS), Modifier.weight(1f),
                    contentPadding = PaddingValues(start = ModeSpacing.screenEdge, end = ModeSpacing.screenEdge, top = ModeSpacing.s, bottom = ModeSize.tabBarClearance),
                    horizontalArrangement = Arrangement.spacedBy(ModeSize.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(horizontal = ModeSpacing.xs, vertical = ModeSpacing.s)) { header() } }
                    items(shots, key = { it.step }) { shot ->
                        Box(Modifier.combinedClickable(onClick = { onOpen(shot) }, onLongClick = { onWrong(shot) })) {
                            ModeScreenCell(shot.number, shot.label, shot.image, { onOpen(shot) })
                        }
                    }
                }
                else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                    Box(Modifier.padding(horizontal = ModeSpacing.screenEdge)) { header() }
                    val pager = rememberPagerState(initialPage = playIndex) { shots.size }
                    LaunchedEffect(playIndex) { if (pager.currentPage != playIndex) pager.animateScrollToPage(playIndex) }
                    LaunchedEffect(pager.settledPage) { if (pager.settledPage != playIndex) onPlayIndex(pager.settledPage) }
                    HorizontalPager(pager, Modifier.weight(1f).padding(bottom = ModeSize.tabBarClearance)) { i ->
                        val shot = shots[i]
                        Column(Modifier.fillMaxSize().padding(horizontal = ModeSpacing.xl), Arrangement.spacedBy(ModeSpacing.s), Alignment.CenterHorizontally) {
                            shot.image?.let { Image(it, null, Modifier.weight(1f).clip(RoundedCornerShape(ModeRadius.card)), contentScale = ContentScale.Fit) }
                            ModeText(shot.label, ModeTextStyle.Callout, color = Mode.colors.textSecondary)
                            ModeButton(stringResource(R.string.screens_wrong_step), { onWrong(shot) }, role = ModeButtonRole.Secondary, icon = ModeIcon.Warning)
                        }
                    }
                }
            }
        }
        if (shots.isNotEmpty()) {
            ModeGlassSwitch(
                listOf(ModeTabItem(ScreensMode.Grid, ModeIcon.Grid, stringResource(R.string.screens_grid)), ModeTabItem(ScreensMode.Play, ModeIcon.Play, stringResource(R.string.screens_play))),
                mode, onMode, Modifier.align(Alignment.BottomCenter).padding(bottom = ModeSpacing.l),
            )
        }
    }
}

/** Connects All screens to its presenter: screenshots decoded once, Play advancing every 1.2 s. */
@Composable
fun RunScreensRoute(graph: ModeGraph, taskId: String, close: () -> Unit) {
    val presenter: RunScreensPresenter = rememberPresenter("screens") { graph.screens.runScreens(taskId) }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(ScreensMode.Grid) }
    var playIndex by rememberSaveable { mutableStateOf(0) }
    var fixing by remember { mutableStateOf<Shot?>(null) }
    val shots by produceState(emptyList<Shot>(), ui.shots) {
        value = withContext(Dispatchers.IO) {
            ui.shots.map { s ->
                Shot(s.step, s.step.toString(), s.label.orEmpty(), presenter.image(s)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() })
            }
        }
    }
    LaunchedEffect(mode, shots.size) {
        while (mode == ScreensMode.Play && shots.isNotEmpty()) {
            delay(PLAY_MS)
            playIndex = (playIndex + 1) % shots.size
        }
    }
    val subtitle = listOf(ui.task, context.getString(R.string.tasks_steps, ui.shots.size)).joinToString(" · ")
    RunScreensScreen(
        stringResource(R.string.screens_title), subtitle, shots, mode, { mode = it }, playIndex, { playIndex = it },
        ui.fixing, ui.fixed, close,
        onShare = {
            val text = (listOf(subtitle) + shots.map { "${it.number}. ${it.label}" }).joinToString("\n")
            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null))
        },
        onOpen = { shot -> playIndex = shots.indexOf(shot).coerceAtLeast(0); mode = ScreensMode.Play },
        onWrong = { mode = ScreensMode.Grid; fixing = it },
        onFixSeen = presenter::fixedSeen,
    )
    fixing?.let { shot ->
        var text by rememberSaveable { mutableStateOf("") }
        var everyTime by rememberSaveable { mutableStateOf(true) }
        ModeSheet({ fixing = null }) {
            ModeFixStepForm(
                stringResource(R.string.screens_what_instead), stringResource(R.string.screens_step_line, shot.number, shot.label),
                stringResource(R.string.screens_what_instead_prompt), text, { text = it }, everyTime, { everyTime = it },
                stringResource(R.string.screens_just_this_time), stringResource(R.string.screens_every_time),
                stringResource(if (everyTime) R.string.screens_every_time_note else R.string.screens_just_this_time_note),
                stringResource(R.string.common_save),
            ) {
                presenter.fixStep(shot.step, shot.label, text, everyTime)
                fixing = null
            }
        }
    }
}

private const val COLUMNS = 3
private const val PLAY_MS = 1_200L
