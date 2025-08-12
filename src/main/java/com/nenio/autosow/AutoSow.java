package com.nenio.autosow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

@Mod(AutoSow.MODID)
public class AutoSow {
    public static final String MODID = "autosow";

    public AutoSow(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modBus.addListener(AutoSow::onCommonSetup);

        NeoForge.EVENT_BUS.register(this);
    }

    /* ===================== Right-click handler (player) ===================== */

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return; // server side only
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // avoid off-hand double trigger
        if (!Config.ENABLED.get()) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.SWEET_BERRY_BUSH && state.hasProperty(BlockStateProperties.AGE_3)) {
            int age = state.getValue(BlockStateProperties.AGE_3);
            if (age >= 2 && age <= 3) {
                int min, max;
                switch (age) {
                    case 2 -> { min = 1; max = 2; }
                    default -> { min = 2; max = 3; }
                }
                int count = min + level.random.nextInt(max - min + 1);

                if (Config.DIRECT_TO_INVENTORY.get()) {
                    ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.SWEET_BERRIES, count));
                } else {
                    Block.popResource((ServerLevel) level, pos, new ItemStack(Items.SWEET_BERRIES, count));
                }

                level.setBlock(pos, state.setValue(BlockStateProperties.AGE_3, 1), Block.UPDATE_ALL);

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        // Standard flow for whitelisted crops (wheat, carrots, potatoes, beetroots, torchflower, nether wart, cocoa)
        if (!isWhitelistedBlock(block)) return;
        if (!isMature(state)) return;

        // Player must hold the matching seed/item
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !heldMatchesBlock(held, block)) return;

        // Resolve what to replant
        Block target = resolveTargetReplantBlock(block);
        if (target == Blocks.AIR) return;

        boolean directToInv = Config.DIRECT_TO_INVENTORY.get();

        if (directToInv) {
            // 1) Compute vanilla drops without actually breaking the block
            List<ItemStack> drops = Block.getDrops(state, (ServerLevel) level, pos, null, player, held);

            // 2) Remove the block without drops
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            // 3) Optionally consume one item (seeds/beans/wart; not used for berries)
            if (Config.CONSUME_ITEM.get() && !player.getAbilities().instabuild) {
                held.shrink(1);
            }

            // 4) Replant
            replant(level, pos, state, target);

            // 5) Give drops to player's inventory
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(player, drop.copy());
                }
            }

        } else {
            // Classic path: break the block and let drops fall on the ground
            boolean destroyed = level.destroyBlock(pos, true, player);
            if (!destroyed) return;

            if (Config.CONSUME_ITEM.get() && !player.getAbilities().instabuild) {
                held.shrink(1);
            }

            replant(level, pos, state, target);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Replant logic depending on target type. */
    private static void replant(Level level, BlockPos pos, BlockState previousState, Block target) {
        if (target instanceof CropBlock crop) {
            // Ensure farmland
            BlockPos soilPos = pos.below();
            if (!level.getBlockState(soilPos).is(Blocks.FARMLAND)) {
                level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);

        } else if (target == Blocks.NETHER_WART) {
            // Ensure soul sand
            BlockPos soilPos = pos.below();
            if (!level.getBlockState(soilPos).is(Blocks.SOUL_SAND)) {
                level.setBlock(soilPos, Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(pos, Blocks.NETHER_WART.defaultBlockState()
                    .setValue(BlockStateProperties.AGE_3, 0), Block.UPDATE_ALL);

        } else if (target == Blocks.COCOA) {
            // Reuse same facing; ensure jungle wood support
            Direction facing = previousState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? previousState.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    : Direction.NORTH;

            BlockPos supportPos = pos.relative(facing.getOpposite());
            BlockState support = level.getBlockState(supportPos);
            if (!isValidCocoaSupport(support.getBlock())) return;

            BlockState cocoa0 = Blocks.COCOA.defaultBlockState()
                    .setValue(BlockStateProperties.AGE_2, 0)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            level.setBlock(pos, cocoa0, Block.UPDATE_ALL);
        }
    }

    /* ===================== Dispenser behaviors (shears) ===================== */

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenseItemBehavior shearsBehavior = new DefaultDispenseItemBehavior() {
                @Override
                protected ItemStack execute(BlockSource source, ItemStack stack) {
                    Level level = source.level();
                    Direction dir = source.state().getValue(DispenserBlock.FACING);
                    BlockPos targetPos = source.pos().relative(dir);
                    BlockState state = level.getBlockState(targetPos);
                    Block block = state.getBlock();

                    if (!(level instanceof ServerLevel server)) {
                        return super.execute(source, stack);
                    }

                    // === MELON ===
                    if (block == Blocks.MELON) {
                        server.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        if (Config.WHOLE_MELON_DROP.get()) {
                            Block.popResource(server, targetPos, new ItemStack(Items.MELON));
                        } else {
                            List<ItemStack> drops = Block.getDrops(state, server, targetPos, null, null, ItemStack.EMPTY);
                            if (drops.isEmpty()) {
                                int count = 3 + server.random.nextInt(5); // 3–7 slices
                                drops = List.of(new ItemStack(Items.MELON_SLICE, count));
                            }
                            for (ItemStack drop : drops) {
                                if (!drop.isEmpty()) Block.popResource(server, targetPos, drop);
                            }
                        }
                        stack.hurtAndBreak(1, server, null, null);
                        return stack;
                    }

                    // === PUMPKIN === (always whole)
                    if (block == Blocks.PUMPKIN) {
                        server.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        Block.popResource(server, targetPos, new ItemStack(Items.PUMPKIN));
                        stack.hurtAndBreak(1, server, null, null);
                        return stack;
                    }

                    // === SWEET BERRY BUSH === (harvest when AGE_3 is 1..3; yield depends on age)
                    if (block == Blocks.SWEET_BERRY_BUSH && state.hasProperty(BlockStateProperties.AGE_3)) {
                        int age = state.getValue(BlockStateProperties.AGE_3); // 0..3
                        if (age >= 1 && age <= 3) {
                            int min, max;
                            switch (age) {
                                case 1 -> { min = 1; max = 1; }   // exactly 1
                                case 2 -> { min = 1; max = 2; }   // 1–2
                                default -> { min = 2; max = 3; }  // age==3 → 2–3
                            }
                            int count = min + server.random.nextInt(max - min + 1);

                            Block.popResource(server, targetPos, new ItemStack(Items.SWEET_BERRIES, count));
                            // Reset bush to age 1 (vanilla-like behavior)
                            server.setBlock(targetPos, state.setValue(BlockStateProperties.AGE_3, 1), Block.UPDATE_ALL);

                            // Damage shears by 1
                            stack.hurtAndBreak(1, server, null, null);
                            return stack;
                        }
                    }
                    return super.execute(source, stack);
                }
            };

            DispenserBlock.registerBehavior(Items.SHEARS, shearsBehavior);
        });
    }

    /* ===================== Helpers ===================== */

    private static boolean isWhitelistedBlock(Block block) {
        if (block == Blocks.WHEAT)            return Config.ALLOW_WHEAT.get();
        if (block == Blocks.CARROTS)          return Config.ALLOW_CARROTS.get();
        if (block == Blocks.POTATOES)         return Config.ALLOW_POTATOES.get();
        if (block == Blocks.BEETROOTS)        return Config.ALLOW_BEETROOTS.get();
        if (block == Blocks.TORCHFLOWER_CROP || block == Blocks.TORCHFLOWER)
            return Config.ALLOW_TORCHFLOWER.get();
        if (block == Blocks.NETHER_WART)      return Config.ALLOW_NETHER_WART.get();
        if (block == Blocks.COCOA)            return Config.ALLOW_COCOA.get();
        // Note: Sweet Berry Bush is handled above as a special case (no replant flow).
        return false;
    }

    private static boolean heldMatchesBlock(ItemStack stack, Block block) {
        if (block == Blocks.WHEAT)                 return stack.is(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS)               return stack.is(Items.CARROT);
        if (block == Blocks.POTATOES)              return stack.is(Items.POTATO);
        if (block == Blocks.BEETROOTS)             return stack.is(Items.BEETROOT_SEEDS);
        if (block == Blocks.TORCHFLOWER || block == Blocks.TORCHFLOWER_CROP)
            return stack.is(Items.TORCHFLOWER_SEEDS);
        if (block == Blocks.NETHER_WART)           return stack.is(Items.NETHER_WART);
        if (block == Blocks.COCOA)                 return stack.is(Items.COCOA_BEANS);
        return false;
    }

    private static boolean isMature(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(state);
        if (b == Blocks.TORCHFLOWER) return true; // fully grown torchflower
        if (b == Blocks.NETHER_WART) return state.getValue(BlockStateProperties.AGE_3) == 3;
        if (b == Blocks.COCOA)       return state.getValue(BlockStateProperties.AGE_2) == 2;
        return false;
    }

    private static Block resolveTargetReplantBlock(Block block) {
        if (block instanceof CropBlock) return block; // wheat/carrots/potatoes/beetroots/torchflower_crop mid-growth
        if (block == Blocks.TORCHFLOWER)   return Blocks.TORCHFLOWER_CROP;
        if (block == Blocks.NETHER_WART)   return Blocks.NETHER_WART;
        if (block == Blocks.COCOA)         return Blocks.COCOA;
        return Blocks.AIR;
    }

    private static boolean isValidCocoaSupport(Block b) {
        return b == Blocks.JUNGLE_LOG
                || b == Blocks.STRIPPED_JUNGLE_LOG
                || b == Blocks.JUNGLE_WOOD
                || b == Blocks.STRIPPED_JUNGLE_WOOD;
    }
}
