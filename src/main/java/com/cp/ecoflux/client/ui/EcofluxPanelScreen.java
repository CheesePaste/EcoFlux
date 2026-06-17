package com.cp.ecoflux.client.ui;

import com.cp.ecoflux.client.key.EcofluxPanelClientEvents;
import com.cp.ecoflux.client.ui.map.ChunkClickMapper;
import com.cp.ecoflux.client.ui.map.SatelliteMapRenderer;
import com.cp.ecoflux.network.ChunkDataEntry;
import com.cp.ecoflux.network.PanelDataPayload;
import com.cp.ecoflux.network.RequestPanelDataPayload;
import com.cp.ecoflux.network.ToggleChunkExclusionPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EcofluxPanelScreen extends Screen {

    // ── Layout ───────────────────────────────────────────────────────

    static final int PANEL_WIDTH = 300;
    static final int PANEL_HEIGHT = 280;

    private static final int TAB_Y = 12;
    private static final int TAB_H = 22;
    private static final int TAB_W = 70;
    private static final int TAB_GAP = 4;
    private static final int SEP_Y = TAB_Y + TAB_H + 3;
    private static final int CONTENT_Y = SEP_Y + 4;

    private static final int MAP_SIZE = 200;
    private static final int CHUNK_GRID = SatelliteMapRenderer.CHUNK_GRID;
    private static final int CHUNK_PX = MAP_SIZE / CHUNK_GRID;

    // ── I18n ─────────────────────────────────────────────────────────

    private static final Component TITLE = Component.translatable("ecoflux.panel.title");
    private static final Component TAB_OVERVIEW = Component.translatable("ecoflux.panel.tab.overview");
    private static final Component TAB_MAP = Component.translatable("ecoflux.panel.tab.map");
    private static final Component DIR_LABEL = Component.literal("N ↑");

    private static final Component[] TABS = {TAB_OVERVIEW, TAB_MAP};

    // ── Colors ───────────────────────────────────────────────────────

    private static final int BG_DIM     = 0x80000000;
    private static final int PANEL_BG   = 0xC0101010;
    private static final int TAB_ACT    = 0xFF3A3A3A;
    private static final int TAB_INACT  = 0xFF1E1E1E;
    private static final int TAB_HVR    = 0xFF4A4A4A;
    private static final int TXT_1      = 0xFFFFFF;
    private static final int TXT_2      = 0xFFAAAAAA;
    private static final int TXT_GRN    = 0xFF55FF55;
    private static final int TXT_RED    = 0xFFFF5555;
    private static final int TXT_MUT    = 0xFF888888;
    private static final int SEP        = 0xFF555555;
    private static final int BAR_BG     = 0xFF333333;
    private static final int BAR_GRN    = 0xFF55AA55;
    private static final int ROW_HL     = 0x1DFFFFFF;
    private static final int LIST_HEAD  = 0x33FFFFFF;
    private static final int EXCL_BORDER = 0xFFFF3333;

    private static final int ROW_H = 13;

    // ── Map ──────────────────────────────────────────────────────────

    private final SatelliteMapRenderer mapRenderer = new SatelliteMapRenderer();
    private int mapLeft, mapTop;
    private ChunkPos hoveredChunk;

    // ── State ────────────────────────────────────────────────────────

    private int activeTab;
    private int hoveredTab = -1;
    private int panelLeft, panelTop;
    private double scrollOffset;  // pixels scrolled down

    public EcofluxPanelScreen() { super(TITLE); }

    // ── Lifecycle ────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop  = (this.height - PANEL_HEIGHT) / 2;
        mapLeft   = panelLeft + (PANEL_WIDTH - MAP_SIZE) / 2;
        mapTop    = panelTop + CONTENT_Y + 10;
        PacketDistributor.sendToServer(new RequestPanelDataPayload(true));
    }

    @Override public void tick() { mapRenderer.tick(); }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new RequestPanelDataPayload(false));
        mapRenderer.close();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Render ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, BG_DIM);
        g.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_BG);
        g.drawCenteredString(this.font, TITLE, panelLeft + PANEL_WIDTH / 2, panelTop + 4, TXT_1);
        renderTabBar(g, mx, my);
        g.fill(panelLeft + 6, panelTop + SEP_Y,
                panelLeft + PANEL_WIDTH - 6, panelTop + SEP_Y + 1, SEP);
        if (activeTab == 0) renderTabOverview(g, mx, my);
        else renderTabMap(g, mx, my);
    }

    // ── Tab bar ──────────────────────────────────────────────────────

    private void renderTabBar(GuiGraphics g, int mx, int my) {
        hoveredTab = -1;
        int tabsW = TABS.length * TAB_W + (TABS.length - 1) * TAB_GAP;
        int sx = panelLeft + (PANEL_WIDTH - tabsW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int l = sx + i * (TAB_W + TAB_GAP);
            int t = panelTop + TAB_Y;
            boolean hit = mx >= l && mx < l + TAB_W && my >= t && my < t + TAB_H;
            int bg = (i == activeTab) ? TAB_ACT : hit ? TAB_HVR : TAB_INACT;
            if (hit) hoveredTab = i;
            g.fill(l, t, l + TAB_W, t + TAB_H, bg);
            g.drawCenteredString(this.font, TABS[i], l + TAB_W / 2, t + 7,
                    (i == activeTab) ? TXT_1 : TXT_2);
        }
    }

    // ── Tab 1: Overview ─────────────────────────────────────────────

    private void renderTabOverview(GuiGraphics g, int mx, int my) {
        PanelDataPayload d = EcofluxPanelClientEvents.getLatestPanelData();
        int x = panelLeft + 14;
        int cx = panelLeft + PANEL_WIDTH / 2;
        int y = panelTop + CONTENT_Y + 2;

        if (d == null) {
            g.drawString(this.font, Component.translatable("ecoflux.panel.overview.no_data"), x, y, TXT_MUT);
            return;
        }

        // ── Header: progress bar + summary + status ─────────────────
        boolean hasData = !Double.isNaN(d.globalAvgProgress());
        double avg = hasData ? d.globalAvgProgress() : 0.0;

        g.drawString(this.font, Component.literal("周边 (3×3) 演替进度"), x, y, TXT_1);
        y += 14;

        int barW = 270, barH = 16;
        if (!hasData) {
            g.fill(x, y, x + barW, y + barH, BAR_BG);
            g.drawCenteredString(this.font, Component.literal("无活跃演替区块"),
                    x + barW / 2, y + 3, TXT_MUT);
        } else {
            drawProgressBar(g, x, y, barW, barH, avg);
            g.drawCenteredString(this.font, Component.literal(progressToPercent(avg) + "%"),
                    x + barW / 2, y + 2, avg >= 0 ? TXT_1 : TXT_RED);
        }
        y += barH + 4;

        String sum = "已初始化 " + d.totalTrackedChunks() + "/9  ·  植被 "
                + d.totalVegetation() + "  ·  生成 " + (d.successionEnabled() ? "✓" : "✗");
        g.drawCenteredString(this.font, Component.literal(sum), cx, y,
                d.successionEnabled() ? TXT_2 : TXT_MUT);
        y += 16;

        String st; int sc;
        if (!hasData)         { st = "用 /ecoflux auto on 启动演替"; sc = TXT_MUT; }
        else if (avg >= 1.0)  { st = "↑ 演替饱和";  sc = TXT_GRN; }
        else if (avg > 0.3)   { st = "↑ 演替活跃";  sc = TXT_GRN; }
        else if (avg >= 0)    { st = "→ 缓慢推进";  sc = TXT_2; }
        else                  { st = "↓ 退化中";    sc = TXT_RED; }
        g.drawCenteredString(this.font, Component.literal(st), cx, y, sc);
        y += 14;

        // ── Scissor-clipped list area ───────────────────────────────
        int listTop = y + 2;
        int listBottom = panelTop + PANEL_HEIGHT - 6;
        int listHeight = listBottom - listTop;

        g.enableScissor(panelLeft + 4, listTop, panelLeft + PANEL_WIDTH - 4, listBottom);

        // list header
        g.fill(panelLeft + 8, y, panelLeft + PANEL_WIDTH - 8, y + 1, LIST_HEAD);
        y += 5;
        g.drawString(this.font, Component.literal("chunk     群系        进度      积分"), x, y, TXT_MUT);
        y += ROW_H + 1;

        // Per-chunk rows — offset by scroll
        y -= (int) scrollOffset;

        ChunkPos pc = getPlayerChunk();
        if (d.entries() != null) {
            for (ChunkDataEntry e : d.entries()) {
                boolean isPlayer = pc != null
                        && pc.x == e.chunkX() && pc.z == e.chunkZ();

                if (isPlayer) {
                    g.fill(panelLeft + 8, y - 1, panelLeft + PANEL_WIDTH - 8, y + ROW_H - 1, ROW_HL);
                }

                String pos = "(" + e.chunkX() + "," + e.chunkZ() + ")" + (isPlayer ? "*" : " ");
                g.drawString(this.font, Component.literal(pos), x, y,
                        isPlayer ? TXT_GRN : TXT_1);

                String bio;
                if (e.hasActivePath()) {
                    bio = e.currentBiome() != null ? pathLast(e.currentBiome()) : "?";
                    if (e.targetBiome() != null) bio += ">" + pathLast(e.targetBiome());
                } else bio = "—";
                g.drawString(this.font, Component.literal(bio), x + 58, y, TXT_2);

                String ps; int pc2;
                if (e.hasActivePath()) {
                    ps = progressToPercent(e.progress()) + "%";
                    pc2 = e.progress() >= 0 ? TXT_GRN : TXT_RED;
                } else { ps = "·"; pc2 = TXT_MUT; }
                g.drawString(this.font, Component.literal(ps), x + 162, y, pc2);

                String pts = e.hasActivePath()
                        ? e.contributingPoints() + "/" + e.consumingValue() : "—";
                g.drawString(this.font, Component.literal(pts), x + 205, y, TXT_MUT);

                y += ROW_H;
            }
        }

        g.disableScissor();

        // Scroll bar
        int totalRows = d.entries() != null ? d.entries().size() : 0;
        int totalContentH = totalRows * ROW_H;
        if (totalContentH > listHeight) {
            float visibleFrac = (float) listHeight / totalContentH;
            int sbH = Math.max(16, (int) (listHeight * visibleFrac));
            int sbY = listTop + (int) (scrollOffset * visibleFrac);
            int sbX = panelLeft + PANEL_WIDTH - 6;
            g.fill(sbX, sbY, sbX + 3, sbY + sbH, 0x88AAAAAA);
        }
    }

    // ── Tab 2: Map ──────────────────────────────────────────────────

    private void renderTabMap(GuiGraphics g, int mx, int my) {
        g.drawCenteredString(this.font, DIR_LABEL,
                panelLeft + PANEL_WIDTH / 2, panelTop + CONTENT_Y, TXT_2);
        int b = 1;
        g.fill(mapLeft - b, mapTop - b, mapLeft + MAP_SIZE + b, mapTop + MAP_SIZE + b, SEP);
        g.flush();
        mapRenderer.render(mapLeft, mapTop, MAP_SIZE);
        for (int i = 0; i <= CHUNK_GRID; i++) {
            int px = mapLeft + i * CHUNK_PX, py = mapTop + i * CHUNK_PX;
            g.fill(px, mapTop, px + 1, mapTop + MAP_SIZE, 0x55FFFFFF);
            g.fill(mapLeft, py, mapLeft + MAP_SIZE, py + 1, 0x55FFFFFF);
        }
        hoveredChunk = resolveHoveredChunk(mx, my);

        // Red border overlay for excluded chunks
        ChunkPos pc = getPlayerChunk();
        if (pc != null) {
            int halfGrid = CHUNK_GRID / 2;
            for (ChunkPos ex : List.copyOf(EcofluxPanelClientEvents.getKnownExcludedChunks())) {
                int col = ex.x - pc.x + halfGrid;
                int row = ex.z - pc.z + halfGrid;
                if (col >= 0 && col < CHUNK_GRID && row >= 0 && row < CHUNK_GRID) {
                    int l = mapLeft + col * CHUNK_PX;
                    int t = mapTop + row * CHUNK_PX;
                    int bw = 2;
                    g.fill(l, t, l + CHUNK_PX, t + bw, EXCL_BORDER);
                    g.fill(l, t + CHUNK_PX - bw, l + CHUNK_PX, t + CHUNK_PX, EXCL_BORDER);
                    g.fill(l, t, l + bw, t + CHUNK_PX, EXCL_BORDER);
                    g.fill(l + CHUNK_PX - bw, t, l + CHUNK_PX, t + CHUNK_PX, EXCL_BORDER);
                }
            }
        }

        if (hoveredChunk != null)
            g.renderTooltip(this.font,
                    Component.literal("Chunk(" + hoveredChunk.x + "," + hoveredChunk.z + ")"), mx, my);
        String info = pc != null
                ? "中心: " + pc.x + "," + pc.z + "  ·  点击切换排除"
                : "点击切换排除";
        g.drawCenteredString(this.font, Component.literal(info),
                panelLeft + PANEL_WIDTH / 2, mapTop + MAP_SIZE + 5, TXT_2);
    }

    // ── Input ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int tabsW = TABS.length * TAB_W + (TABS.length - 1) * TAB_GAP;
        int sx = panelLeft + (PANEL_WIDTH - tabsW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int l = sx + i * (TAB_W + TAB_GAP), t = panelTop + TAB_Y;
            if (mx >= l && mx < l + TAB_W && my >= t && my < t + TAB_H) {
                activeTab = i; return true;
            }
        }
        if (activeTab == 1) {
            ChunkPos ck = resolveHoveredChunk(mx, my);
            if (ck != null) {
                var excludedSet = EcofluxPanelClientEvents.getKnownExcludedChunks();
                boolean nowExcluded = !excludedSet.contains(ck);
                if (nowExcluded) excludedSet.add(ck);
                else excludedSet.remove(ck);
                PacketDistributor.sendToServer(
                        new ToggleChunkExclusionPayload(ck.x, ck.z, nowExcluded));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        PanelDataPayload d = EcofluxPanelClientEvents.getLatestPanelData();
        if (d == null || d.entries() == null) return false;

        int totalContentH = d.entries().size() * ROW_H;
        int listTop = panelTop + CONTENT_Y + 2  // start of content
                + 14 + 20 + 16 + 14 + 2;          // progress bar + summary + status + gap
        int listBottom = panelTop + PANEL_HEIGHT - 6;
        int listHeight = listBottom - listTop;
        int maxScroll = Math.max(0, totalContentH - listHeight);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 20));
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private ChunkPos resolveHoveredChunk(double mx, double my) {
        ChunkPos c = getPlayerChunk(); if (c == null) return null;
        return ChunkClickMapper.screenToChunk(mx, my, mapLeft, mapTop, MAP_SIZE, c);
    }

    private static ChunkPos getPlayerChunk() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? new ChunkPos(mc.player.blockPosition()) : null;
    }

    private static int progressToPercent(double p) {
        return (int) Math.round((Math.max(-1.0, Math.min(1.0, p)) + 1.0) * 50.0);
    }

    private static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, double p) {
        g.fill(x, y, x + w, y + h, BAR_BG);
        double c = Math.max(-1.0, Math.min(1.0, p));
        int mid = x + w / 2, half = w / 2;
        int fill = (int) (half * Math.abs(c));
        if (c >= 0) g.fill(mid, y + 1, mid + fill, y + h - 1, BAR_GRN);
        else        g.fill(mid - fill, y + 1, mid, y + h - 1, 0xFFAA5555);
    }

    private static String pathLast(net.minecraft.resources.ResourceLocation rl) {
        String s = rl.getPath();
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }
}
