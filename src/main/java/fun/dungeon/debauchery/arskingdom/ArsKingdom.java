package fun.dungeon.debauchery.arskingdom;

import com.mojang.logging.LogUtils;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.ColonyDye;
import fun.dungeon.debauchery.arskingdom.colony.ColonyLookup;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.registry.AKBlockEntities;
import fun.dungeon.debauchery.arskingdom.registry.AKBlocks;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;
import fun.dungeon.debauchery.arskingdom.registry.AKEquipmentTypes;
import fun.dungeon.debauchery.arskingdom.registry.AKGuardTypes;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageStatus;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageSpellGuard;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageUpkeep;
import fun.dungeon.debauchery.arskingdom.battlemage.SchoolSpells;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageEquipment;
import fun.dungeon.debauchery.arskingdom.ai.EntityAIBattlemage;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import net.minecraft.network.chat.Component;
import fun.dungeon.debauchery.arskingdom.registry.AKItems;
import fun.dungeon.debauchery.arskingdom.registry.AKJobs;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyeEvents;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyeSweeps;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeSweepQueue;
import fun.dungeon.debauchery.arskingdom.command.AKCommands;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Ars Kingdom: ties Ars Nouveau into MineColonies. See PLAN.md for the milestones. */
@Mod(ArsKingdom.MODID)
public final class ArsKingdom {
    public static final String MODID = "ars_kingdom";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsKingdom(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, AKConfig.SPEC);
        AKBlocks.BLOCKS.register(modBus);
        AKItems.ITEMS.register(modBus);
        AKItems.TABS.register(modBus);
        AKBlockEntities.BLOCK_ENTITIES.register(modBus);
        AKBuildings.BUILDINGS.register(modBus);
        AKJobs.JOBS.register(modBus);
        AKEquipmentTypes.EQUIPMENT_TYPES.register(modBus);
        AKGuardTypes.GUARD_TYPES.register(modBus);
        BattlemageStatus.init();
        BuildingColors.init();
        modBus.addListener(ArsKingdom::onConfig);
        modBus.addListener(ArsKingdom::onConfigReload);
        modBus.addListener(ArsKingdom::onCommonSetup);

        NeoForge.EVENT_BUS.register(ColonyLookup.class);
        NeoForge.EVENT_BUS.register(ColonyRules.class);
        NeoForge.EVENT_BUS.register(BuildingColors.class);
        NeoForge.EVENT_BUS.register(AKCommands.class);
        NeoForge.EVENT_BUS.register(ColonyDyeEvents.class);
        NeoForge.EVENT_BUS.register(DyeSweepQueue.class);
        NeoForge.EVENT_BUS.register(ColonyDyeSweeps.class);
        NeoForge.EVENT_BUS.register(BattlemageUpkeep.class);
        NeoForge.EVENT_BUS.register(SchoolSpells.class);
        NeoForge.EVENT_BUS.register(BattlemageSpellGuard.class);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ColonyDyeSweeps::subscribeMineColonies);
        event.enqueueWork(AKJobs::registerCitizenSounds);
        // The battlemage's "not allowed to work" message clears itself once it is fully equipped.
        event.enqueueWork(() -> InteractionValidatorRegistry.registerStandardPredicate(
                Component.translatable(EntityAIBattlemage.NOT_ALLOWED_KEY), BattlemageEquipment::isUnequippedBattlemage));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AKConfig.SPEC) {
            ColonyDye.reload();
        }
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AKConfig.SPEC) {
            ColonyDye.reload();
            // Reloads arrive on the config watcher thread: re-sweep from the server thread.
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> ColonyDyeSweeps.applyConfigChanges(server));
            }
        }
    }
}
