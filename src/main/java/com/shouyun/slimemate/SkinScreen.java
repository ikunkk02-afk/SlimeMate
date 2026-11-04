package com.shouyun.slimemate;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.network.PacketDistributor;

final class SkinScreen extends Screen {
    private static final int PANEL_HEIGHT = 216;
    private static final int PREVIEW_WIDTH = 100;
    private final int slot;
    private final ItemStack original;
    private ItemStack preview;
    private UUID selectedId;
    private PlayerInfo selectedPlayer;
    private String query = "";
    private String pixels;
    private boolean slim;
    private Page page = Page.ONLINE;
    private int refreshTicks;
    private Component pngStatus = label("drop_png");
    private Component lookupStatus = label("lookup_hint");
    private String lookupName = "";
    private int lookupTicks;
    private ResolvableProfile lookupProfile;
    private OnlinePlayers players;
    private Button applyPlayer;
    private Button applyName;
    private Button searchName;
    private Button savePng;

    private enum Page { ONLINE, EXTERNAL, PNG }

    SkinScreen(int slot, ItemStack doll) {
        super(label("title"));
        this.slot = slot;
        original = doll.copy();
        preview = original;
        ImportedSkin imported = original.get(SlimeMate.IMPORTED_SKIN.get());
        if (imported != null) {
            pixels = imported.pixels();
            slim = imported.slim();
            pngStatus = label("preview_ready");
        }
        ResolvableProfile profile = original.get(DataComponents.PROFILE);
        if (profile != null) {
            selectedId = profile.id().orElse(null);
        }
    }

    @Override
    protected void init() {
        int left = panelLeft() + 12;
        int top = panelTop();
        int formWidth = formWidth();
        int tabWidth = (formWidth - 8) / 3;
        addTab(Page.ONLINE, "online_tab", left, top, tabWidth);
        addTab(Page.EXTERNAL, "external_tab", left + tabWidth + 4, top, tabWidth);
        addTab(Page.PNG, "png_tab", left + 2 * tabWidth + 8, top, formWidth - 2 * tabWidth - 8);

        switch (page) {
            case ONLINE -> initOnline(left, top, formWidth);
            case EXTERNAL -> initExternal(left, top, formWidth);
            case PNG -> initPng(left, top, formWidth);
        }
        updatePreview();

        int footerWidth = (panelWidth() - 28) / 2;
        addRenderableWidget(Button.builder(label("reset"), button -> submit(SkinEditPayload.RESET, ""))
                .bounds(left, top + 187, footerWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + footerWidth + 4, top + 187, footerWidth, 20).build());
    }

    private void addTab(Page tab, String key, int x, int top, int width) {
        Button button = addRenderableWidget(Button.builder(label(key), widget -> showPage(tab))
                .bounds(x, top + 28, width, 20).build());
        button.active = page != tab;
    }

    private void initOnline(int left, int top, int formWidth) {
        EditBox search = new EditBox(font, left, top + 54, formWidth, 20, label("search"));
        search.setMaxLength(16);
        search.setHint(label("search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refreshPlayers(true);
        });
        addRenderableWidget(search);
        players = addRenderableWidget(new OnlinePlayers(left, top + 79, formWidth));
        applyPlayer = addRenderableWidget(Button.builder(label("apply_player"), button -> {
            if (selectedPlayer != null) {
                submitOnline(selectedPlayer);
            }
        }).bounds(left, top + 159, formWidth, 20).build());
        refreshPlayers(true);
    }

    private void initExternal(int left, int top, int formWidth) {
        EditBox username = new EditBox(font, left, top + 54, formWidth, 20, label("lookup_name"));
        username.setMaxLength(16);
        username.setHint(label("lookup_name"));
        username.setValue(lookupName);
        username.setResponder(value -> {
            lookupName = value;
            lookupTicks = 0;
            lookupProfile = null;
            lookupStatus = label("lookup_hint");
            updateLookupButtons();
            updatePreview();
        });
        addRenderableWidget(username);
        searchName = addRenderableWidget(Button.builder(label("fetch"), button -> startLookup())
                .bounds(left, top + 80, formWidth, 20).build());
        applyName = addRenderableWidget(Button.builder(label("apply_player"),
                        button -> submit(SkinEditPayload.PROFILE, lookupName.trim()))
                .bounds(left, top + 159, formWidth, 20).build());
        updateLookupButtons();
    }

    private void initPng(int left, int top, int formWidth) {
        addRenderableWidget(Button.builder(label("open_png"), button -> openPng())
                .bounds(left, top + 101, formWidth, 20).build());
        addRenderableWidget(CycleButton.<Boolean>builder(value -> label(value ? "slim" : "classic"))
                .withValues(false, true).withInitialValue(slim).displayOnlyValue()
                .create(left, top + 128, formWidth, 20, label("model"), (button, value) -> {
                    slim = value;
                    updatePreview();
                }));
        savePng = addRenderableWidget(Button.builder(label("save_png"),
                        button -> submit(slim ? SkinEditPayload.SLIM_PNG : SkinEditPayload.CLASSIC_PNG, pixels))
                .bounds(left, top + 159, formWidth, 20).build());
        savePng.active = pixels != null;
    }

    private void showPage(Page next) {
        if (page != next) {
            page = next;
            rebuildWidgets();
        }
    }

    @Override
    public void tick() {
        if (page == Page.ONLINE && ++refreshTicks >= 10) {
            refreshTicks = 0;
            refreshPlayers(false);
        }
        if (lookupTicks > 0 && --lookupTicks == 0) {
            lookupStatus = label("lookup_retry");
            if (page == Page.EXTERNAL) {
                updateLookupButtons();
            }
        }
    }

    private void refreshPlayers(boolean resetScroll) {
        ClientPacketListener connection = minecraft.getConnection();
        String search = query.trim().toLowerCase(Locale.ROOT);
        List<PlayerInfo> matches = connection == null ? List.of() : connection.getListedOnlinePlayers().stream()
                .filter(player -> player.getProfile().getName().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(player -> player.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        players.update(matches, selectedId, resetScroll, search);
    }

    private void startLookup() {
        lookupTicks = 200;
        lookupProfile = null;
        lookupStatus = label("lookup_pending");
        updateLookupButtons();
        updatePreview();
        PacketDistributor.sendToServer(new SkinEditPayload(slot, SkinEditPayload.LOOKUP_PROFILE, lookupName.trim()));
    }

    void acceptLookup(SkinLookupResult result) {
        if (lookupTicks <= 0 || result.slot() != slot || !result.name().equals(lookupName.trim())) {
            return;
        }
        lookupTicks = 0;
        lookupProfile = result.profile().orElse(null);
        lookupStatus = label(result.retry() ? "lookup_retry" : lookupProfile == null ? "lookup_missing" : "lookup_found");
        if (page == Page.EXTERNAL) {
            updateLookupButtons();
            updatePreview();
        }
    }

    private void updateLookupButtons() {
        String name = lookupName.trim();
        searchName.active = lookupTicks == 0 && !name.isEmpty() && StringUtil.isValidPlayerName(name);
        applyName.active = lookupProfile != null;
    }

    private void submitOnline(PlayerInfo player) {
        submit(SkinEditPayload.ONLINE_PLAYER, player.getProfile().getId().toString());
    }

    private void updatePreview() {
        preview = original.copy();
        if (page == Page.PNG && pixels != null) {
            preview.set(SlimeMate.IMPORTED_SKIN.get(), new ImportedSkin(pixels, slim));
            preview.remove(DataComponents.PROFILE);
        } else {
            ResolvableProfile profile = switch (page) {
                case ONLINE -> selectedPlayer == null ? null : new ResolvableProfile(selectedPlayer.getProfile());
                case EXTERNAL -> lookupProfile;
                case PNG -> null;
            };
            if (profile != null) {
                preview.set(DataComponents.PROFILE, profile);
                preview.remove(SlimeMate.IMPORTED_SKIN.get());
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = panelTop();
        int left = panelLeft();
        int right = left + panelWidth() - 12;
        int previewLeft = right - PREVIEW_WIDTH;
        int center = (previewLeft + right) / 2;

        renderTransparentBackground(graphics);
        graphics.fill(left, top, left + panelWidth(), top + PANEL_HEIGHT, 0xF0182325);
        graphics.renderOutline(left, top, panelWidth(), PANEL_HEIGHT, 0xFF54756C);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xF2FFF8);
        graphics.fill(previewLeft, top + 28, right, top + 179, 0xFF111A1C);
        graphics.renderOutline(previewLeft, top + 28, PREVIEW_WIDTH, 151, 0xFF3D5652);
        graphics.drawCenteredString(font, label("preview"), center, top + 38, 0xFFBFE7D8);

        graphics.enableScissor(previewLeft + 2, top + 51, right - 2, top + 151);
        graphics.pose().pushPose();
        graphics.pose().translate(center - 26, top + 63, 0);
        graphics.pose().scale(3.25F, 3.25F, 1);
        graphics.renderFakeItem(preview, 0, 0);
        graphics.pose().popPose();
        graphics.disableScissor();

        ResolvableProfile profile = preview.get(DataComponents.PROFILE);
        String source;
        if (preview.has(SlimeMate.IMPORTED_SKIN.get())) {
            source = label("png_tab").getString();
        } else {
            source = profile == null ? label("default_skin").getString() : profile.name().orElse("");
        }
        graphics.drawCenteredString(font, font.plainSubstrByWidth(source, PREVIEW_WIDTH - 10), center, top + 158, 0xFFC9DFDA);

        if (page != Page.ONLINE) {
            int y = top + (page == Page.PNG ? 57 : 112);
            Component status = page == Page.PNG ? pngStatus : lookupStatus;
            for (FormattedCharSequence line : font.split(status, formWidth())) {
                graphics.drawString(font, line, left + 12, y, 0xFFD1E8DE, false);
                y += 10;
            }
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        showPage(Page.PNG);
        if (paths.size() != 1) {
            pngStatus = label("invalid_png");
            return;
        }
        loadPng(paths.getFirst());
    }

    private void openPng() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String file = TinyFileDialogs.tinyfd_openFileDialog(title.getString(), "", stack.pointers(stack.UTF8("*.png")),
                    "Minecraft skin PNG", false);
            if (file != null) {
                loadPng(Path.of(file));
            }
        }
    }

    private void loadPng(Path path) {
        try {
            pixels = readSkin(path);
            updatePreview();
            savePng.active = true;
            pngStatus = label("preview_ready");
        } catch (IOException | RuntimeException ignored) {
            pngStatus = label("invalid_png");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void submit(int action, String value) {
        PacketDistributor.sendToServer(new SkinEditPayload(slot, action, value));
        onClose();
    }

    private int panelWidth() {
        return Math.min(344, width - 16);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private int formWidth() {
        return panelWidth() - PREVIEW_WIDTH - 34;
    }

    private static Component label(String key) {
        return Component.translatable("screen.slimemate." + key);
    }

    private final class OnlinePlayers extends ObjectSelectionList<OnlinePlayers.Entry> {
        private List<PlayerInfo> shown = List.of();

        OnlinePlayers(int x, int y, int width) {
            super(SkinScreen.this.minecraft, width, 74, y, 18);
            setX(x);
        }

        void update(List<PlayerInfo> matches, UUID previous, boolean resetScroll, String search) {
            if (!resetScroll && shown.equals(matches)) {
                return;
            }
            double scroll = getScrollAmount();
            shown = matches;
            List<Entry> entries = matches.stream().map(Entry::new).toList();
            replaceEntries(entries);
            setScrollAmount(resetScroll ? 0 : scroll);

            Entry next = null;
            for (Entry entry : entries) {
                if (entry.player.getProfile().getId().equals(previous)) {
                    next = entry;
                    break;
                }
            }
            if (next == null && !search.isEmpty()) {
                next = entries.stream().filter(entry -> entry.player.getProfile().getName().equalsIgnoreCase(search))
                        .findFirst().orElse(entries.size() == 1 ? entries.getFirst() : null);
            }
            setSelected(next);
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            selectedPlayer = entry == null ? null : entry.player;
            selectedId = selectedPlayer == null ? null : selectedPlayer.getProfile().getId();
            applyPlayer.active = selectedPlayer != null;
            updatePreview();
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 22;
        }

        @Override
        protected void renderListBackground(GuiGraphics graphics) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF111A1C);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF3D5652);
            if (shown.isEmpty()) {
                graphics.drawCenteredString(font, label("no_players"), getX() + getWidth() / 2, getY() + 30, 0xFFB6C8C5);
            }
        }

        @Override
        protected void renderListSeparators(GuiGraphics graphics) {}

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final PlayerInfo player;

            Entry(PlayerInfo player) {
                this.player = player;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {
                PlayerFaceRenderer.draw(graphics, player.getSkin(), left + 2, top + 1, 12);
                graphics.drawString(font, player.getProfile().getName(), left + 19, top + 3, 0xFFE6F5F0);
            }

            @Override
            public Component getNarration() {
                return Component.literal(player.getProfile().getName());
            }
        }
    }
    private static String readSkin(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 65536) {
            throw new IOException("Invalid skin file size");
        }
        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
        try (FileImageInputStream stream = new FileImageInputStream(path.toFile())) {
            reader.setInput(stream, true, true);
            int size = ImportedSkin.SIZE;
            if (reader.getWidth(0) != size || reader.getHeight(0) != size) {
                throw new IOException("Expected a 64 by 64 PNG");
            }
            BufferedImage image = reader.read(0);
            ByteBuffer pixels = ByteBuffer.allocate(ImportedSkin.PIXEL_BYTES);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int argb = image.getRGB(x, y);
                    if ((y < 16 && x < 32) || (y >= 16 && y < 32) || (y >= 48 && x >= 16 && x < 48)) {
                        argb |= 0xFF000000;
                    }
                    pixels.putInt(Integer.rotateLeft(argb, 8));
                }
            }
            return Base64.getEncoder().encodeToString(pixels.array());
        } finally {
            reader.dispose();
        }
    }
}
