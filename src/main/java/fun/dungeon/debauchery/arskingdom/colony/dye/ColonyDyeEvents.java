package fun.dungeon.debauchery.arskingdom.colony.dye;

import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.compat.ArsLightBridge;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Event-driven colony dye (Chunk 2): placements and drops, for block dye and light colors (M1d). Builder placements, color changes and
 * chunk loads are handled by the sweeps (Chunk 3).
 */
public final class ColonyDyeEvents {
    private ColonyDyeEvents() {
    }

    /**
     * LOW so it runs after Ars Deco: a dyed item's color is already on the block by now (Ars Deco applies
     * it when {@code BlockItem.place} returns), so a pre-dyed item counts as a player's choice.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos();
            if (ArsDecoBridge.isTarget(event.getPlacedBlock().getBlock()) || isLight(level, pos)) {
                ColonyDyer.process(level, pos);
            }
        }
    }

    /**
     * LOWEST so it runs after Ars Deco has copied the block's color onto the drop. Colony dye stays in the
     * colony: drops of blocks we dyed come out undyed. Player-chosen colors keep travelling with the item.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        // Light colors live in the block entity, not the item: just drop our record.
        AutoDyedData.lights(level).remove(pos);
        AutoDyedData data = AutoDyedData.get(level);
        var recorded = data.get(pos);
        if (recorded.isEmpty()) {
            return;
        }
        data.remove(pos);
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.get(DataComponents.BASE_COLOR) == recorded.get()
                    && ArsDecoBridge.isTarget(event.getState().getBlock())) {
                stack.remove(DataComponents.BASE_COLOR);
                drop.setItem(stack);
            }
        }
    }

    private static boolean isLight(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && ArsLightBridge.isLight(be);
    }
}
