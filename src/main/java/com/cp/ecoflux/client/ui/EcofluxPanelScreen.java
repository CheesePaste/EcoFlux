package com.cp.ecoflux.client.ui;

import com.cp.ecoflux.client.key.EcofluxPanelClientEvents;
import com.cp.ecoflux.client.ui.map.ChunkClickMapper;
import com.cp.ecoflux.client.ui.map.SatelliteMapRenderer;
import com.cp.ecoflux.network.PanelDataPayload;
import com.cp.ecoflux.network.RequestPanelDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EcofluxPanelScreen extends Screen {

    // ── Layout ───────────────────────────────────────────────────────

    static final int PANEL_WIDTH = 300;
    static final int PANEL_HEIGHT = 270;

    // Tab bar
    private static final int TAB_BAR_Y = 12;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_WIDTH = 70;
    private static final int TAB_GAP = 4;
    private static final int TAB_SEPARATOR_Y = TAB_BAR_Y + TAB_HEIGHT + 3;
    private static final int CONTENT_TOP = TAB_SEPARATOR_Y + 4;

    // Map
    private static final int MAP_SIZE = 200;
    private static final int CHUNK_GRID = SatelliteMapRenderer.CHUNK_GRID;
    private static final int CHUNK_PX = MAP_SIZE / CHUNK_GRID;

    // ── I18n ─────────────────────────────────────────────────────────

    private static final Component TITLE = Component.translatable("ecoflux.panel.title");
    private static final Component TAB_OVERVIEW = Component.translatable("ecoflux.panel.tab.overview");
    private static final Component TAB_MAP = Component.translatable("ecoflux.panel.tab.map");
    private static final Component PLACEHOLDER = Component.translatable("ecoflux.panel.placeholder.overview");
    private static final Component DIRECTION_LABEL = Component.literal("N ↑");

    private static final Component[] TABS = {TAB_OVERVIEW, TAB_MAP};

    // ── Colors ───────────────────────────────────────────────────────

    private static final int BG_OVERLAY   = 0x80000000;
    private static final int PANEL_BG     = 0xC0101010;
    private static final int TAB_ACTIVE   = 0xFF3A3A3A;
    private static final int TAB_INACTIVE = 0xFF1E1E1E;
    private static final int TAB_HOVER    = 0xFF4A4A4A;
    private static final int TXT_PRIMARY  = 0xFFFFFF;
    private static final int TXT_SECONDARY = 0xFFAAAAAA;
    private static final int TXT_HIGHLIGHT = 0xFF55FF55;
    private static final int TXT_WARN      = 0xFFFF5555;
    private static final int TXT_MUTED     = 0xFF888888;
    private static final int SEPARATOR     = 0xFF555555;
    private static final int BAR_BG        = 0xFF333333;
    private static final int BAR_FILL_POS  = 0xFF55AA55;
    private static final int BAR_FILL_NEG  = 0xFFAA5555;

    // ── Map ──────────────────────────────────────────────────────────

    private final SatelliteMapRenderer mapRenderer = new SatelliteMapRenderer();
    private int mapLeft, mapTop;
    private ChunkPos hoveredChunk;

    // ── State ────────────────────────────────────────────────────────

    private int activeTab;
    private int hoveredTab = -1;
    private int panelLeft, panelTop;

    public EcofluxPanelScreen() {
        super(TITLE);
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop  = (this.height - PANEL_HEIGHT) / 2;
        mapLeft   = panelLeft + (PANEL_WIDTH - MAP_SIZE) / 2;
        mapTop    = panelTop + CONTENT_TOP + 10;
        PacketDistributor.sendToServer(new RequestPanelDataPayload(true));
    }

    @Override
    public void tick() {
        mapRenderer.tick();
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new RequestPanelDataPayload(false));
        mapRenderer.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Render ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG_OVERLAY);
        g.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_BG);

        g.drawCenteredString(this.font, TITLE,
                panelLeft + PANEL_WIDTH / 2, panelTop + 4, TXT_PRIMARY);

        renderTabBar(g, mouseX, mouseY);

        g.fill(panelLeft + 6, panelTop + TAB_SEPARATOR_Y,
                panelLeft + PANEL_WIDTH - 6, panelTop + TAB_SEPARATOR_Y + 1, SEPARATOR);

        if (activeTab == 0) {
            renderTabOverview(g);
        } else {
            renderTabMap(g, mouseX, mouseY);
        }
    }

    // ── Tab bar ──────────────────────────────────────────────────────

    private void renderTabBar(GuiGraphics g, int mouseX, int mouseY) {
        hoveredTab = -1;
        int tabsW = TABS.length * TAB_WIDTH + (TABS.length - 1) * TAB_GAP;
        int startX = panelLeft + (PANEL_WIDTH - tabsW) / 2;

        for (int i = 0; i < TABS.length; i++) {
            int l = startX + i * (TAB_WIDTH + TAB_GAP);
            int t = panelTop + TAB_BAR_Y;
            boolean hit = mouseX >= l && mouseX < l + TAB_WIDTH
                    && mouseY >= t && mouseY < t + TAB_HEIGHT;
            int bg = (i == activeTab) ? TAB_ACTIVE : hit ? TAB_HOVER : TAB_INACTIVE;
            if (hit) hoveredTab = i;
            g.fill(l, t, l + TAB_WIDTH, t + TAB_HEIGHT, bg);
            g.drawCenteredString(this.font, TABS[i], l + TAB_WIDTH / 2, t + 7,
                    (i == activeTab) ? TXT_PRIMARY : TXT_SECONDARY);
        }
    }

    // ── Tab 1: Succession overview ───────────────────────────────────

    private void renderTabOverview(GuiGraphics g) {
        PanelDataPayload d = EcofluxPanelClientEvents.getLatestPanelData();
        int x = panelLeft + 14;
        int y = panelTop + CONTENT_TOP;
        final int rowH = 14;

        if (d == null) {
            g.drawString(this.font, PLACEHOLDER, x, y, TXT_SECONDARY);
            return;
        }

        // ── Global average ────────────────────────────────────────────
        g.drawString(this.font, Component.literal("全局平均进度"), x, y, TXT_PRIMARY);
        double globalAvg = d.globalAvgProgress();
        if (!Double.isNaN(globalAvg)) {
            int pct = progressToPercent(globalAvg);
            drawProgressBar(g, x, y + 12, 260, 10, globalAvg);
            g.drawString(this.font, pct + "%", x + 266, y + 13, TXT_SECONDARY);
            y += 26;
        } else {
            y += rowH;
        }
        y += 4;

        // separator
        g.fill(panelLeft + 10, y, panelLeft + PANEL_WIDTH - 10, y + 1, 0x33FFFFFF);
        y += 8;

        // ── Chunk locale ──────────────────────────────────────────────
        String chunkLabel = "区块: (" + d.chunkX() + ", " + d.chunkZ() + ")";
        if (d.successionDisabled()) chunkLabel += "  [已排除]";
        g.drawString(this.font, Component.literal(chunkLabel), x, y, TXT_PRIMARY);
        y += rowH + 2;

        // Biome line
        String cb = d.currentBiome() != null ? pathLast(d.currentBiome()) : "?";
        String tb = d.targetBiome() != null ? pathLast(d.targetBiome()) : "?";
        if (d.currentBiome() != null) {
            g.drawString(this.font, Component.literal("群系: " + cb + " → " + tb), x, y, TXT_SECONDARY);
            y += rowH;
        }

        // Path
        if (d.activePathId() != null) {
            g.drawString(this.font, Component.literal("路径: " + pathLast(d.activePathId())),
                    x, y, TXT_MUTED);
            y += rowH;
        }
        y += 2;

        // ── Chunk progress bar ────────────────────────────────────────
        g.drawString(this.font, Component.literal("演替进度"), x, y, TXT_PRIMARY);
        y += rowH;
        int chunkPct = progressToPercent(d.progress());
        drawProgressBar(g, x, y, 260, 12, d.progress());
        g.drawString(this.font, chunkPct + "%", x + 266, y + 2, TXT_SECONDARY);
        y += 16;

        // ── Stats ─────────────────────────────────────────────────────
        boolean hasEval = d.consumingValue() > 0;
        if (hasEval) {
            g.drawString(this.font, Component.literal(
                    "贡献积分: " + d.contributingPoints() + "/" + d.consumingValue()),
                    x, y, TXT_SECONDARY);
        } else {
            g.drawString(this.font, Component.literal(
                    "贡献积分: " + d.contributingPoints() + " (阈值未设置)"), x, y, TXT_MUTED);
        }
        y += rowH;

        g.drawString(this.font, Component.literal(
                "贡献植被: " + d.contributingCount() + "株   总追踪: " + d.totalVegetationCount() + "株"),
                x, y, TXT_SECONDARY);
        y += rowH + 4;

        // ── Status ────────────────────────────────────────────────────
        String status;
        int statusColor;
        if (d.successionDisabled()) {
            status = "状态: 已排除（不参与演替）";
            statusColor = TXT_MUTED;
        } else if (d.progress() >= 1.0) {
            status = "状态: 即将转换群系";
            statusColor = TXT_HIGHLIGHT;
        } else if (d.progress() > 0) {
            status = "状态: 正向演替中";
            statusColor = TXT_HIGHLIGHT;
        } else if (d.progress() < 0) {
            status = "状态: 退化中";
            statusColor = TXT_WARN;
        } else {
            status = "状态: 等待植被成长";
            statusColor = TXT_MUTED;
        }
        g.drawString(this.font, Component.literal(status), x, y, statusColor);
    }

    // ── Tab 2: Satellite map ────────────────────────────────────────

    private void renderTabMap(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, DIRECTION_LABEL,
                panelLeft + PANEL_WIDTH / 2, panelTop + CONTENT_TOP, TXT_SECONDARY);

        int border = 1;
        g.fill(mapLeft - border, mapTop - border,
                mapLeft + MAP_SIZE + border, mapTop + MAP_SIZE + border, SEPARATOR);

        g.flush();
        mapRenderer.render(mapLeft, mapTop, MAP_SIZE);

        for (int i = 0; i <= CHUNK_GRID; i++) {
            int px = mapLeft + i * CHUNK_PX;
            int py = mapTop  + i * CHUNK_PX;
            g.fill(px, mapTop, px + 1, mapTop + MAP_SIZE, 0x55FFFFFF);
            g.fill(mapLeft, py, mapLeft + MAP_SIZE, py + 1, 0x55FFFFFF);
        }

        hoveredChunk = resolveHoveredChunk(mouseX, mouseY);
        if (hoveredChunk != null) {
            g.renderTooltip(this.font,
                    Component.literal("Chunk(" + hoveredChunk.x + ", " + hoveredChunk.z + ")"),
                    mouseX, mouseY);
        }

        ChunkPos c = getPlayerChunk();
        String info = c != null
                ? "中心: " + c.x + ", " + c.z + "  ·  点击切换排除状态"
                : "点击切换排除状态";
        g.drawCenteredString(this.font, Component.literal(info),
                panelLeft + PANEL_WIDTH / 2, mapTop + MAP_SIZE + 5, TXT_SECONDARY);
    }

    // ── Input ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mX, double mY, int button) {
        if (button != 0) return super.mouseClicked(mX, mY, button);

        int tabsW = TABS.length * TAB_WIDTH + (TABS.length - 1) * TAB_GAP;
        int startX = panelLeft + (PANEL_WIDTH - tabsW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int l = startX + i * (TAB_WIDTH + TAB_GAP);
            int t = panelTop + TAB_BAR_Y;
            if (mX >= l && mX < l + TAB_WIDTH && mY >= t && mY < t + TAB_HEIGHT) {
                activeTab = i;
                return true;
            }
        }

        if (activeTab == 1) {
            ChunkPos clicked = resolveHoveredChunk(mX, mY);
            if (clicked != null) {
                // TODO: ToggleChunkExclusionPayload
                return true;
            }
        }
        return super.mouseClicked(mX, mY, button);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private ChunkPos resolveHoveredChunk(double mX, double mY) {
        ChunkPos c = getPlayerChunk();
        if (c == null) return null;
        return ChunkClickMapper.screenToChunk(mX, mY, mapLeft, mapTop, MAP_SIZE, c);
    }

    private static ChunkPos getPlayerChunk() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return new ChunkPos(mc.player.blockPosition());
    }

    private static int progressToPercent(double progress) {
        return (int) Math.round((progress + 1.0) * 50.0); // [-1, +1] → [0, 100]
    }

    private static void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, double progress) {
        g.fill(x, y, x + w, y + h, BAR_BG);
        double frac = (progress + 1.0) / 2.0;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        int mid = x + (int) (w * 0.5);
        if (progress >= 0) {
            int fillW = (int) ((w * 0.5) * frac);
            g.fill(mid, y, mid + fillW, y + h, BAR_FILL_POS);
        } else {
            int fillW = (int) ((w * 0.5) * (1.0 - frac));
            g.fill(mid - fillW, y, mid, y + h, BAR_FILL_NEG);
        }
    }

    private static String pathLast(ResourceLocation rl) {
        String s = rl.getPath();
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }
}
