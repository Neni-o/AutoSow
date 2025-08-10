package com.nenio.autosow;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONSUME_ITEM;
    public static final ForgeConfigSpec.BooleanValue DIRECT_TO_INVENTORY;

    public static final ForgeConfigSpec.BooleanValue ALLOW_WHEAT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CARROTS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_POTATOES;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BEETROOTS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_TORCHFLOWER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NETHER_WART;
    public static final ForgeConfigSpec.BooleanValue ALLOW_COCOA;

    public static final ForgeConfigSpec.BooleanValue WHOLE_MELON_DROP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("General");
        ENABLED = builder.comment("Enable or disable the AutoSow mod").define("enabled", true);
        CONSUME_ITEM = builder.comment("Consume one seed/bean/wart when replanting").define("consumeItem", true);
        DIRECT_TO_INVENTORY = builder.comment("Harvested drops go directly to player's inventory").define("directToInventory", false);
        builder.pop();

        builder.push("Allowed Crops");
        ALLOW_WHEAT = builder.define("allowWheat", true);
        ALLOW_CARROTS = builder.define("allowCarrots", true);
        ALLOW_POTATOES = builder.define("allowPotatoes", true);
        ALLOW_BEETROOTS = builder.define("allowBeetroots", true);
        ALLOW_TORCHFLOWER = builder.define("allowTorchflower", true);
        ALLOW_NETHER_WART = builder.define("allowNetherWart", true);
        ALLOW_COCOA = builder.define("allowCocoa", true);
        builder.pop();

        builder.push("Melon Settings");
        WHOLE_MELON_DROP = builder.comment("If true, dispenser with shears drops whole melon; if false, slices").define("wholeMelonDrop", false);
        builder.pop();

        SPEC = builder.build();
    }
}
