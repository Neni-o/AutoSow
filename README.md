# 🌱 AutoSow

**AutoSow** is a lightweight and performance-friendly **Forge mod** for **Minecraft 1.21.x** that streamlines farming by **automatically harvesting and replanting fully grown crops** when you right-click them with the correct planting item. No more tedious replanting after every harvest — just click, collect, and keep farming!

---

## ✨ Features

* 🌾 **Supported crops**: Wheat, Carrots, Potatoes, Beetroots, Torchflower, Nether Wart, Cocoa Beans
* ⚙ **Per-crop configuration** – enable or disable any supported crop individually in the config file
* 🌱 **Seed consumption toggle** – choose whether replanting uses up seeds/planting items or not
* 🤖 **Dispenser support** – use dispenser(with shears) to harvest melons, pumpkins and sweet berries
* 🎯 **Direct-to-inventory mode** *(optional)* – harvested items can go straight into your inventory instead of dropping on the ground

---
## 📂​ Download 

CurseForge: https://www.curseforge.com/minecraft/mc-mods/autosow-by-neni-o

Modrinth: https://modrinth.com/mod/autosow-by-neni-o

---

## ⚙ Configuration

After the first run, a configuration file will be generated in:

```
/config/autosow-common.toml
```

### 🔧 Configuration Options (default values in brackets)

| Setting               | Description                                                            | Default |
| --------------------- | ---------------------------------------------------------------------- | ------- |
| **enabled**           | Master switch for the mod. If false, the mod is completely disabled.   | `true`  |
| **consumeItem**       | If `true`, harvesting consumes one seed/planting item when replanting. | `true`  |
| **directToInventory** | If `true`, harvested items go directly into the player’s inventory.    | `false` |
| **dispenserEnabled**  | Enables dispenser automation for supported crops.                      | `true`  |
| **allowWheat**        | Allow automatic harvesting/replanting of wheat.                        | `true`  |
| **allowCarrots**      | Allow automatic harvesting/replanting of carrots.                      | `true`  |
| **allowPotatoes**     | Allow automatic harvesting/replanting of potatoes.                     | `true`  |
| **allowBeetroots**    | Allow automatic harvesting/replanting of beetroots.                    | `true`  |
| **allowTorchflower**  | Allow automatic harvesting/replanting of torchflowers.                 | `true`  |
| **allowNetherWart**   | Allow automatic harvesting/replanting of nether wart.                  | `true`  |
| **allowCocoa**        | Allow automatic harvesting/replanting of cocoa beans.                  | `true`  |

---

## 🛠 How It Works

1. **Manual Harvesting**

   * Hold the correct seed or planting item in your hand.
   * Right-click a **fully grown crop**.
   * The crop is instantly harvested and replanted at growth stage 0.
   * If *Direct-to-inventory* is enabled, the drops go straight to your inventory.

2. **Dispenser Harvester**

* Place a dispenser facing a fully grown melon block.
* Load it with shears.
* When powered by redstone, the dispenser will automatically harvest the melon:
  - If `WHOLE_MELON_DROP` is set to `true` in the config → drops a whole melon.
  - If set to `false` → drops several melon slices (3–7).
* Shears lose 1 durability point for each harvest.
* The same mechanic works for:
  - Pumpkins → always drops a whole pumpkin.
  - Sweet Berries → drops 2–3 berries depending on bush age.


3. **Special Cases**

   * **Cocoa Beans** must be attached to jungle wood to be replanted.
   * **Nether Wart** requires Soul Sand beneath it.
   * If the supporting block for a crop is invalid, replanting will be skipped.

---

## 📜 License

This mod is licensed under the **MIT License**, meaning you’re free to use, modify, and distribute it, provided the original license and credit are included.

