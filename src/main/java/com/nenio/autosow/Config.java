package com.nenio.autosow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "autosow.json";

    // ====== pola konfiguracyjne (publiczne do łatwego użycia w kodzie) ======
    public static boolean ENABLED;
    public static boolean CONSUME_ITEM;
    public static boolean DIRECT_TO_INVENTORY;

    public static boolean ALLOW_WHEAT;
    public static boolean ALLOW_CARROTS;
    public static boolean ALLOW_POTATOES;
    public static boolean ALLOW_BEETROOTS;
    public static boolean ALLOW_TORCHFLOWER;
    public static boolean ALLOW_NETHER_WART;
    public static boolean ALLOW_COCOA;

    public static boolean WHOLE_MELON_DROP;

    // ====== nośnik do JSON ======
    private static class Data {
        boolean enabled = true;
        boolean consumeItem = true;
        boolean directToInventory = false;

        boolean allowWheat = true;
        boolean allowCarrots = true;
        boolean allowPotatoes = true;
        boolean allowBeetroots = true;
        boolean allowTorchflower = true;
        boolean allowNetherWart = true;
        boolean allowCocoa = true;

        boolean wholeMelonDrop = false;
    }

    public static void load() {
        Path cfgDir = FabricLoader.getInstance().getConfigDir();
        Path file = cfgDir.resolve(FILE_NAME);

        Data data;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                data = GSON.fromJson(r, Data.class);
            } catch (IOException e) {
                e.printStackTrace();
                data = new Data(); // fallback
            }
        } else {
            data = new Data();
            try {
                Files.createDirectories(cfgDir);
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(data, w);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ENABLED = data.enabled;
        CONSUME_ITEM = data.consumeItem;
        DIRECT_TO_INVENTORY = data.directToInventory;

        ALLOW_WHEAT = data.allowWheat;
        ALLOW_CARROTS = data.allowCarrots;
        ALLOW_POTATOES = data.allowPotatoes;
        ALLOW_BEETROOTS = data.allowBeetroots;
        ALLOW_TORCHFLOWER = data.allowTorchflower;
        ALLOW_NETHER_WART = data.allowNetherWart;
        ALLOW_COCOA = data.allowCocoa;

        WHOLE_MELON_DROP = data.wholeMelonDrop;
    }
}
