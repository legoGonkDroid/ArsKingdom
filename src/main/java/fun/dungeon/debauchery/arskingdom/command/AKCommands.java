package fun.dungeon.debauchery.arskingdom.command;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.ColonyDye;
import fun.dungeon.debauchery.arskingdom.colony.ColonyLookup;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyeSweeps;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeSweepQueue;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /arskingdom ...} debug commands. */
public final class AKCommands {
    private AKCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arskingdom")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("where").executes(AKCommands::where))
                .then(Commands.literal("sweep").executes(AKCommands::sweep)));
    }

    /**
     * Prints the colony and colony dye at the caller's position, the building color if it stands in a colored building,
     * plus the Ars Deco state of the block below.
     */
    private static int where(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());

        IColony colony = ColonyLookup.colony(level, pos);
        if (colony == null) {
            src.sendSuccess(() -> Component.literal("Not in a colony (colony id " + ColonyLookup.colonyId(level, pos) + ")"), false);
        } else {
            Optional<DyeColor> dye = ColonyDye.colonyDye(colony);
            String dyeText = dye.map(DyeColor::getName).orElse("none (undyed: native Ars purple)");
            src.sendSuccess(() -> Component.literal("Colony #" + colony.getID() + " \"" + colony.getName() + "\", team color "
                    + colony.getTeamColonyColor().getName() + ", colony dye: " + dyeText), false);
            BuildingColors.zoneAt(colony, pos).ifPresent(zone -> src.sendSuccess(() -> Component.literal(
                    "Inside a colored building (" + zone.entry().id() + "): dyed " + zone.dye().getName()), false));
        }

        BlockPos below = pos.below();
        ArsDecoBridge.category(level.getBlockState(below).getBlock()).ifPresent(cat -> {
            String color = ArsDecoBridge.getColor(level, below).map(DyeColor::getName).orElse("undyed");
            src.sendSuccess(() -> Component.literal("Block below: Ars Deco " + cat.name().toLowerCase() + " target, " + color), false);
        });
        return colony == null ? 0 : colony.getID();
    }

    /** Force-sweeps every loaded chunk of the colony at the caller's position. */
    private static int sweep(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        IColony colony = ColonyLookup.colony(src.getLevel(), BlockPos.containing(src.getPosition()));
        if (colony == null) {
            src.sendFailure(Component.literal("Not in a colony"));
            return 0;
        }
        ColonyDyeSweeps.queueColony(colony, true);
        int pending = DyeSweepQueue.pending();
        src.sendSuccess(() -> Component.literal("Queued a dye sweep of colony #" + colony.getID() + " (" + pending + " chunks pending)"), true);
        return pending;
    }
}
