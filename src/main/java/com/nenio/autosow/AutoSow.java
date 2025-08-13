package com.nenio.autosow;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.block.*;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public class AutoSow implements ModInitializer {
    public static final String MODID = "autosow";

    @Override
    public void onInitialize() {
        Config.load();
        registerRightClick();
        registerDispenserShears();
    }

    /* ===================== Right-click (player) ===================== */

    private void registerRightClick() {
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!Config.ENABLED) return ActionResult.PASS;

            ServerWorld server = (ServerWorld) world;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            // Sweet berries — zbiór bez sadzenia
            if (block == Blocks.SWEET_BERRY_BUSH && state.contains(Properties.AGE_3)) {
                int age = state.get(Properties.AGE_3);
                if (age >= 2) {
                    int min = (age == 2) ? 1 : 2;
                    int max = (age == 2) ? 2 : 3;
                    int count = min + server.random.nextInt(max - min + 1);

                    if (Config.DIRECT_TO_INVENTORY) {
                        player.giveItemStack(new ItemStack(Items.SWEET_BERRIES, count));
                    } else {
                        drop(server, pos, new ItemStack(Items.SWEET_BERRIES, count));
                    }
                    world.setBlockState(pos, state.with(Properties.AGE_3, 1), Block.NOTIFY_ALL);
                    return ActionResult.SUCCESS;
                }
            }

            // klasyczny flow
            if (!isWhitelistedBlock(block)) return ActionResult.PASS;
            if (!isMature(state)) return ActionResult.PASS;

            ItemStack held = player.getStackInHand(hand);
            if (held.isEmpty() || !heldMatchesBlock(held, block)) return ActionResult.PASS;

            Block target = resolveTargetReplantBlock(block);
            if (target == Blocks.AIR) return ActionResult.PASS;

            if (Config.DIRECT_TO_INVENTORY) {
                // 1) policz dropy
                List<ItemStack> drops = Block.getDroppedStacks(state, server, pos, null, player, held);
                // 2) usuń blok bez dropów
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                // 3) zużyj 1 nasiono jeśli trzeba
                if (Config.CONSUME_ITEM && !player.getAbilities().creativeMode) {
                    held.decrement(1);
                }
                // 4) zasadź
                replant(world, pos, state, target);
                // 5) daj dropy do eq
                for (ItemStack d : drops) if (!d.isEmpty()) player.giveItemStack(d.copy());
            } else {
                boolean destroyed = world.breakBlock(pos, true, player);
                if (!destroyed) return ActionResult.PASS;
                if (Config.CONSUME_ITEM && !player.getAbilities().creativeMode) {
                    held.decrement(1);
                }
                replant(world, pos, state, target);
            }

            return ActionResult.SUCCESS;
        });
    }

    /** Replant zależnie od typu. */
    private static void replant(World world, BlockPos pos, BlockState previousState, Block target) {
        if (target instanceof CropBlock crop) {
            BlockPos soilPos = pos.down();
            if (!world.getBlockState(soilPos).isOf(Blocks.FARMLAND)) {
                world.setBlockState(soilPos, Blocks.FARMLAND.getDefaultState(), Block.NOTIFY_ALL);
            }
            world.setBlockState(pos, crop.withAge(0), Block.NOTIFY_ALL);

        } else if (target == Blocks.NETHER_WART) {
            BlockPos soilPos = pos.down();
            if (!world.getBlockState(soilPos).isOf(Blocks.SOUL_SAND)) {
                world.setBlockState(soilPos, Blocks.SOUL_SAND.getDefaultState(), Block.NOTIFY_ALL);
            }
            world.setBlockState(pos, Blocks.NETHER_WART.getDefaultState().with(Properties.AGE_3, 0), Block.NOTIFY_ALL);

        } else if (target == Blocks.COCOA) {
            // 1) spróbuj wziąć facing ze starego stanu
            Direction facing = null;
            if (previousState.contains(Properties.HORIZONTAL_FACING)) {
                facing = previousState.get(Properties.HORIZONTAL_FACING);
            } else if (previousState.contains(Properties.FACING)) {
                Direction f = previousState.get(Properties.FACING);
                if (f.getAxis().isHorizontal()) facing = f;
            }

            // 2) jeśli brak - wykryj po sąsiednim dżunglowym drewnie
            if (facing == null) {
                for (Direction d : Direction.values()) {
                    if (!d.getAxis().isHorizontal()) continue;
                    BlockPos supportProbe = pos.offset(d.getOpposite());
                    if (isValidCocoaSupport(world.getBlockState(supportProbe).getBlock())) {
                        facing = d;
                        break;
                    }
                }
            }
            if (facing == null) return; // nie mamy poprawnego kierunku

            // 3) weryfikacja podpory
            BlockPos supportPos = pos.offset(facing.getOpposite());
            if (!isValidCocoaSupport(world.getBlockState(supportPos).getBlock())) return;

            // 4) sadzenie 0-age + kierunek (obsługa FACING/HORIZONTAL_FACING)
            BlockState cocoa0 = cocoaAge0State();
            if (cocoa0.contains(Properties.HORIZONTAL_FACING)) {
                cocoa0 = cocoa0.with(Properties.HORIZONTAL_FACING, facing);
            } else if (cocoa0.contains(Properties.FACING)) {
                cocoa0 = cocoa0.with(Properties.FACING, facing);
            }
            world.setBlockState(pos, cocoa0, Block.NOTIFY_ALL);
        }
    }

    /* ===================== Dispenser: nożyczki ===================== */

    private void registerDispenserShears() {
        DispenserBehavior shears = new ItemDispenserBehavior() {
            @Override
            protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
                ServerWorld server = pointer.world();
                BlockPos pos = pointer.pos();
                Direction dir = pointer.state().get(DispenserBlock.FACING);
                BlockPos targetPos = pos.offset(dir);
                BlockState state = server.getBlockState(targetPos);
                Block block = state.getBlock();

                // MELON
                if (block == Blocks.MELON) {
                    server.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    if (Config.WHOLE_MELON_DROP) {
                        drop(server, targetPos, new ItemStack(Items.MELON));
                    } else {
                        List<ItemStack> drops = Block.getDroppedStacks(state, server, targetPos, null);
                        if (drops.isEmpty()) {
                            int count = 3 + server.random.nextInt(5);
                            drops = List.of(new ItemStack(Items.MELON_SLICE, count));
                        }
                        for (ItemStack d : drops) drop(server, targetPos, d);
                    }
                    damageFromDispenser(stack, server);
                    return stack;
                }

                // PUMPKIN
                if (block == Blocks.PUMPKIN) {
                    server.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    drop(server, targetPos, new ItemStack(Items.PUMPKIN));
                    damageFromDispenser(stack, server);
                    return stack;
                }

                // SWEET BERRY BUSH
                if (block == Blocks.SWEET_BERRY_BUSH && state.contains(Properties.AGE_3)) {
                    int age = state.get(Properties.AGE_3);
                    if (age >= 1 && age <= 3) {
                        int min = (age == 1) ? 1 : (age == 2 ? 1 : 2);
                        int max = (age == 1) ? 1 : (age == 2 ? 2 : 3);
                        int count = min + server.random.nextInt(max - min + 1);

                        drop(server, targetPos, new ItemStack(Items.SWEET_BERRIES, count));
                        server.setBlockState(targetPos, state.with(Properties.AGE_3, 1), Block.NOTIFY_ALL);

                        damageFromDispenser(stack, server);
                        return stack;
                    }
                }

                return super.dispenseSilently(pointer, stack);
            }
        };

        DispenserBlock.registerBehavior(Items.SHEARS, shears);
    }

    private static void damageFromDispenser(ItemStack stack, ServerWorld server) {
        stack.damage(1, server, null, item -> {});
    }

    private static void drop(ServerWorld world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        world.spawnEntity(new ItemEntity(world, x, y, z, stack.copy()));
    }

    /* ===================== Helpers ===================== */

    private static boolean isWhitelistedBlock(Block block) {
        if (block == Blocks.WHEAT)            return Config.ALLOW_WHEAT;
        if (block == Blocks.CARROTS)          return Config.ALLOW_CARROTS;
        if (block == Blocks.POTATOES)         return Config.ALLOW_POTATOES;
        if (block == Blocks.BEETROOTS)        return Config.ALLOW_BEETROOTS;
        if (block == Blocks.TORCHFLOWER_CROP || block == Blocks.TORCHFLOWER)
            return Config.ALLOW_TORCHFLOWER;
        if (block == Blocks.NETHER_WART)      return Config.ALLOW_NETHER_WART;
        if (block == Blocks.COCOA)            return Config.ALLOW_COCOA;
        return false;
    }

    private static boolean heldMatchesBlock(ItemStack stack, Block block) {
        if (block == Blocks.WHEAT)                 return stack.isOf(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS)               return stack.isOf(Items.CARROT);
        if (block == Blocks.POTATOES)              return stack.isOf(Items.POTATO);
        if (block == Blocks.BEETROOTS)             return stack.isOf(Items.BEETROOT_SEEDS);
        if (block == Blocks.TORCHFLOWER || block == Blocks.TORCHFLOWER_CROP)
            return stack.isOf(Items.TORCHFLOWER_SEEDS);
        if (block == Blocks.NETHER_WART)           return stack.isOf(Items.NETHER_WART);
        if (block == Blocks.COCOA)                 return stack.isOf(Items.COCOA_BEANS);
        return false;
    }

    private static boolean isMature(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock crop) return crop.isMature(state);
        if (b == Blocks.TORCHFLOWER) return true; // w pełni wyrośnięty kwiat
        if (b == Blocks.NETHER_WART) return state.get(Properties.AGE_3) == 3;
        if (b == Blocks.COCOA) {
            if (state.contains(Properties.AGE_2)) return state.get(Properties.AGE_2) == 2; // 0..2
            return false;
        }
        return false;
    }

    private static Block resolveTargetReplantBlock(Block block) {
        if (block instanceof CropBlock) return block;
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

    private static BlockState cocoaAge0State() {
        BlockState s = Blocks.COCOA.getDefaultState();
        if (s.contains(Properties.AGE_2)) return s.with(Properties.AGE_2, 0);
        return s;
    }
}
