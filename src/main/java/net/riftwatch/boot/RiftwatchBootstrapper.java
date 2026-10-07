package net.riftwatch.boot;

import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

public final class RiftwatchBootstrapper implements GraphicsBootstrapper {
    private static final String NEOFORGE_WINDOW = "fmlearlywindow";

    @Override
    public String name() {
        return RiftwatchWindow.NAME;
    }

    @Override
    public void bootstrap(String[] arguments) {
        if (NEOFORGE_WINDOW.equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) {
            FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER, RiftwatchWindow.NAME);
        }
    }
}
