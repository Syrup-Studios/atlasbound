package net.syrupstudios.atlasbound;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.antique_atlas.gui.core.ScreenState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.rasanovum.rosetta.registry.ModRegistrar;
import net.rasanovum.rosetta.registry.RegistrationContext;
import net.rasanovum.rosetta.registry.RegistryHandle;
import net.syrupstudios.atlasbound.backend.AtlasMapBackend;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import net.syrupstudios.atlasbound.map.AtlasPlayer;
import net.syrupstudios.atlasbound.network.AtlasNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Atlasbound {
    public static final String MOD_ID = "atlasbound";
    // Keep the old namespace so resource packs can keep using their existing IDs.
    public static final String LEGACY_RESOURCE_NAMESPACE = "antique_atlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ModRegistrar REGISTRAR = new ModRegistrar(MOD_ID);
    public static final RegistryHandle<Item> ATLAS = REGISTRAR.item("atlas", Item::new,
            new Item.Properties().stacksTo(1));

    private Atlasbound() {}

    public static ResourceLocation legacyId(String path) {
        return path.contains(":") ? ResourceLocation.tryParse(path) : ResourceLocation.fromNamespaceAndPath(LEGACY_RESOURCE_NAMESPACE, path);
    }

    public static void initialize(String loader, RegistrationContext context) {
        REGISTRAR.register(context);
        AtlasMapBackend.initialize();
        AtlasNetworking.initialize();
        LOGGER.info("Atlasbound initialized for {}", loader);
    }

    // This nested holder keeps client classes out of common initialization on dedicated servers.
    public static final class Client {
        public static final AtlasboundConfig CONFIG = new AtlasboundConfig();
        public static final ScreenState<AtlasScreen> lastState = new ScreenState<>();

        private Client() {}

        public static void initialize() {}

        public static ItemStack getHandheldAtlas() {
            ItemStack stack = ATLAS.get().getDefaultInstance().copy();
            stack.set(DataComponents.ITEM_NAME, Component.translatable("item.antique_atlas.atlas"));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.translatable("item.antique_atlas.atlas.lore").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)),
                    Component.translatable("item.antique_atlas.atlas.hint", Component.translatable("item.antique_atlas.atlas"))
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
            )));
            return stack;
        }

        public static AtlasScreen openAtlasScreen() {
            if (!AtlasClientState.allowScreenOpen()) {
                AtlasClientState.requestOpen();
                return null;
            }
            Minecraft client = Minecraft.getInstance();
            if (client.screen == null && (!CONFIG.requireItem.get() || (client.player != null && hasHandheldAtlas(client.player)))) {
                AtlasScreen screen = new AtlasScreen();
                screen.init();
                screen.prepareToOpen();
                screen.tick();
                client.setScreen(screen);
                return screen;
            }
            return null;
        }

        public static boolean hasHandheldAtlas(Player player) {
            return AtlasClientState.hasAtlas(player);
        }

        public static Map<UUID, AtlasPlayer> getVisiblePlayers() {
            Minecraft client = Minecraft.getInstance();
            Map<UUID, AtlasPlayer> players = new LinkedHashMap<>();
            if (client.level == null) return players;
            client.level.players().stream()
                    .filter(player -> client.player == null || !player.getUUID().equals(client.player.getUUID()))
                    .forEach(player -> players.put(player.getUUID(), AtlasPlayer.from(player)));
            if (client.player != null) players.put(client.player.getUUID(), AtlasPlayer.from(client.player));
            return players;
        }
    }
}
