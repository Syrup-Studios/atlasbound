package net.syrupstudios.atlasbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.syrupstudios.atlasbound.network.AtlasPackets;

public final class AtlasClientPacketHandlers {
    private AtlasClientPacketHandlers() {}

    public static void selection(AtlasPackets.Selection packet, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.begin(packet));
    }

    public static void region(AtlasPackets.Region packet, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.region(packet));
    }

    public static void ready(AtlasPackets.Ready packet, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.ready(packet));
    }

    public static void marker(AtlasPackets.Marker packet, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.marker(packet));
    }

    public static void carried(AtlasPackets.Carried packet, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.carried(packet.atlases()));
    }
}
