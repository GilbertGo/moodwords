package com.example.moodwords

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 시계 화면을 옆으로 넘기면 나오는 타일: 누르면 바로 감정 알아보기(측정)부터 시작.
 * 마지막으로 기록한 감정과 지금까지 쓴 단어 수를 함께 보여줌.
 */
class MoodTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val store = MoodStore(this)
        val last = store.entries().firstOrNull()
        val count = store.distinctWords()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(last, count)))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(2)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    private fun spacer(h: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    private fun layout(last: MoodEntry?, count: Int): LayoutElementBuilders.LayoutElement {
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("start")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .addKeyToExtraMapping(EXTRA_START, ActionBuilders.booleanExtra(true))
                            .build()
                    )
                    .build()
            )
            .build()

        val button = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFFB9AEFF.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(24f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(18f)).setEnd(dp(18f)).setTop(dp(10f)).setBottom(dp(10f))
                            .build()
                    )
                    .build()
            )
            .addContent(text("감정 알아보기", 15f, 0xFF000000.toInt(), bold = true))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("지금 마음은?", 13f, 0xFFAAAAAA.toInt()))
            .addContent(spacer(6f))
            .addContent(
                if (last != null) text(last.words, 20f, EmotionWords.argbOf(last.valence).toInt(), bold = true)
                else text("감정단어", 20f, 0xFFFFFFFF.toInt(), bold = true)
            )
            .addContent(spacer(4f))
            .addContent(text(if (count > 0) "쓴 감정 단어 ${count}개" else "첫 감정을 기록해 보세요", 11f, 0xFF888888.toInt()))
            .addContent(spacer(12f))
            .addContent(button)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    companion object {
        const val EXTRA_START = "start_check"
        private const val RES_VERSION = "1"

        /** 기록한 뒤 타일 내용을 새로 고침 */
        fun refresh(context: Context) {
            runCatching { getUpdater(context).requestUpdate(MoodTileService::class.java) }
        }
    }
}
