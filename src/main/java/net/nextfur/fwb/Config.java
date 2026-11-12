package net.nextfur.fwb;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_API_INTEGRATION = BUILDER
            .comment("Enable NextFUR API integration and authentication")
            .define("enableApiIntegration", true);

    public static final ModConfigSpec.ConfigValue<String> API_ENDPOINT = BUILDER
            .comment("Base URL endpoint for the NextFUR API")
            .define("apiEndpoint", "https://api.nextfur.net");

    public static final ModConfigSpec SPEC = BUILDER.build();
}
