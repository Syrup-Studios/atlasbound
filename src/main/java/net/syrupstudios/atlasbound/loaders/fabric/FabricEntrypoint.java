//? if fabric {
package net.syrupstudios.atlasbound.loaders.fabric;

import net.fabricmc.api.ModInitializer;
import net.rasanovum.rosetta.entrypoint.RosettaEntrypoints;
import net.rasanovum.rosetta.registry.RegistrationContext;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.AtlasboundEntrypoint;

public final class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        Atlasbound.initialize("Fabric", RegistrationContext.create());
        RosettaEntrypoints.register(new AtlasboundEntrypoint());
    }
}
//?}
