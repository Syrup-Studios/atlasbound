package net.syrupstudios.atlasbound;

import net.rasanovum.rosetta.entrypoint.RosettaClientEntrypoint;
import net.rasanovum.rosetta.entrypoint.RosettaCommonEntrypoint;
import net.rasanovum.rosetta.event.ServerHooks;
import net.syrupstudios.atlasbound.client.AtlasboundClient;
import net.syrupstudios.atlasbound.network.AtlasNetworking;

public final class AtlasboundEntrypoint implements RosettaCommonEntrypoint {
    @Override
    public ServerHooks.Callbacks serverHooks() {
        return new ServerHooks.Callbacks() {
            @Override public void onPlayerLeave(net.minecraft.server.level.ServerPlayer player) {
                AtlasNetworking.disconnect(player);
            }

            @Override public void onServerLevelTick(net.minecraft.server.level.ServerLevel level) {
                if (level == level.getServer().overworld()) {
                    AtlasManager.tick(level.getServer());
                }
            }


            @Override public void onServerStopping(net.minecraft.server.MinecraftServer server) {
                AtlasNetworking.serverStopped(server);
            }
        };
    }

    @Override public RosettaClientEntrypoint clientEntrypoint() { return new AtlasboundClient(); }
}
