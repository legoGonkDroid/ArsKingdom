package fun.dungeon.debauchery.arskingdom.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.job.JobArcanist;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import fun.dungeon.debauchery.arskingdom.job.JobSourceKeeper;
import fun.dungeon.debauchery.arskingdom.registry.AKBlocks;
import fun.dungeon.debauchery.arskingdom.registry.AKEquipmentTypes;
import fun.dungeon.debauchery.arskingdom.building.modules.ArcanistCraftingModule;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hiring and firing in our huts, through MineColonies' own modules, in a real (throwaway) colony. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class HiringTests {
    private static final String EMPTY = "gametest/empty";

    private HiringTests() {
    }

    private static IBuilding hut(GameTestHelper helper, IColony colony, BlockPos rel, Block block) {
        helper.setBlock(rel, block);
        AbstractTileEntityColonyBuilding be = (AbstractTileEntityColonyBuilding) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        be.setColony(colony);
        IBuilding building = colony.getServerBuildingManager().addNewBuilding(be, helper.getLevel());
        helper.assertTrue(helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                be.getBlockPos(), null) != null, block + ": the hut exposes no item handler capability");
        building.setBuildingLevel(1);
        markBuilt(building);
        return building;
    }

    /** As if the builder finished it (MineColonies only auto-hires into built huts). */
    private static void markBuilt(IBuilding building) {
        for (Class<?> c = building.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var field = c.getDeclaredField("isBuilt");
                field.setAccessible(true);
                field.setBoolean(building, true);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no isBuilt field");
    }

    private static ICitizenData adult(IColony colony) {
        ICitizenData citizen = (ICitizenData) colony.getCitizenManager().createAndRegisterCivilianData();
        citizen.setIsChild(false);
        return citizen;
    }

    private static void hireFire(GameTestHelper helper, IColony colony, IBuilding building, Predicate<Object> jobType, String what) {
        IAssignsJob module = building.getFirstModuleOccurance(IAssignsJob.class);
        helper.assertTrue(module != null, what + ": no hiring module");
        ICitizenData citizen = adult(colony);

        helper.assertTrue(module.assignCitizen(citizen), what + ": hire refused");
        helper.assertTrue(jobType.test(citizen.getJob()), what + ": hired citizen has job " + citizen.getJob());
        helper.assertTrue(citizen.getWorkBuilding() == building, what + ": hired citizen's work building " + citizen.getWorkBuilding());
        helper.assertTrue(module.getAssignedCitizen().contains(citizen), what + ": module doesn't list the hire");

        helper.assertTrue(module.removeCitizen(citizen), what + ": fire refused");
        helper.assertTrue(citizen.getJob() == null, what + ": fired citizen still has job " + citizen.getJob());
        helper.assertTrue(citizen.getWorkBuilding() == null, what + ": fired citizen still works at " + citizen.getWorkBuilding());
        helper.assertTrue(!module.getAssignedCitizen().contains(citizen), what + ": module still lists the fired citizen");

        // Auto-hire (the colony's default): a colony tick fills the empty slot with a jobless adult.
        ((ITickingModule) module).onColonyTick(colony);
        helper.assertTrue(module.getAssignedCitizen().size() == 1, what + ": auto-hire filled " + module.getAssignedCitizen().size() + " slots");
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hireAndFire(GameTestHelper helper) {
        var level = helper.getLevel();
        IColony colony = IColonyManager.getInstance().createColony(level, helper.absolutePos(new BlockPos(1, 1, 1)),
                FakePlayerFactory.getMinecraft(level), "ak_hiring_test", "Ars Kingdom (Archwood Collective)");
        helper.assertTrue(colony != null, "colony not created");
        try {
            hireFire(helper, colony, hut(helper, colony, new BlockPos(1, 1, 1), AKBlocks.ARCANIST_WORKSHOP.get()),
                    j -> j instanceof JobArcanist, "Arcanist Workshop");
            hireFire(helper, colony, hut(helper, colony, new BlockPos(3, 1, 1), AKBlocks.SOURCE_SANCTUM.get()),
                    j -> j instanceof JobSourceKeeper, "Source Sanctum");
            hireFire(helper, colony, hut(helper, colony, new BlockPos(5, 1, 1), AKBlocks.MAGE_TOWERS.get(MageSchool.RED).get()),
                    j -> j instanceof JobBattlemage<?>, "Red Mage Tower");
        } finally {
            IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, level);
        }
        helper.succeed();
    }

    private static IColony colony(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        IColony colony = IColonyManager.getInstance().createColony(level, helper.absolutePos(new BlockPos(1, 1, 1)),
                FakePlayerFactory.getMinecraft(level), name, "Ars Kingdom (Archwood Collective)");
        helper.assertTrue(colony != null, "colony not created");
        return colony;
    }

    /** A workshop with an Arcanist and its custom recipes loaded, as after a colony tick. */
    private static IBuilding workshop(GameTestHelper helper, IColony colony, BlockPos rel) {
        IBuilding workshop = hut(helper, colony, rel, AKBlocks.ARCANIST_WORKSHOP.get());
        workshop.getFirstModuleOccurance(IAssignsJob.class).assignCitizen(adult(colony));
        ArcanistCraftingModule crafting = workshop.getFirstModuleOccurance(ArcanistCraftingModule.class);
        crafting.checkForWorkerSpecificRecipes();
        helper.assertTrue(crafting.getFirstRecipe(new Tool(AKEquipmentTypes.SPELLBOOK.get(), 1, 1)::matches) != null,
                "the workshop has no recipe matching a tier-1 spellbook request; recipes: " + crafting.getRecipes().size());
        return workshop;
    }

    private static String resolverOf(IColony colony, IToken<?> token) {
        return colony.getRequestManager().getResolverForRequest(token).getClass().getSimpleName();
    }

    /** A Mage Tower's spellbook request (an equipment request, as the battlemage posts) goes to the Arcanist. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void spellbookRequestGoesToArcanist(GameTestHelper helper) {
        IColony colony = colony(helper, "ak_request_test");
        try {
            IBuilding workshop = workshop(helper, colony, new BlockPos(1, 1, 1));
            IBuilding tower = hut(helper, colony, new BlockPos(5, 1, 1), AKBlocks.MAGE_TOWERS.get(MageSchool.RED).get());
            IToken<?> token = colony.getRequestManager().createAndAssignRequest(tower.getRequester(),
                    new Tool(AKEquipmentTypes.SPELLBOOK.get(), 1, 1));
            String resolver = resolverOf(colony, token);
            ArsKingdom.LOGGER.info("Spellbook request (workshop first) resolved by {}", resolver);
            helper.assertTrue(resolver.contains("Crafting"), "spellbook request went to " + resolver + ", not the Arcanist");
            // The crafting resolver posts a "craft" child request, which the production resolver gives the Arcanist.
            var request = colony.getRequestManager().getRequestForToken(token);
            ArsKingdom.LOGGER.info("Spellbook request state {} children {}", request.getState(), request.getChildren().size());
            for (IToken<?> child : request.getChildren()) {
                var c = colony.getRequestManager().getRequestForToken(child);
                ArsKingdom.LOGGER.info("  child {} {} state {} resolver {}", c.getRequest().getClass().getSimpleName(),
                        c.getShortDisplayString().getString(), c.getState(), resolverOf(colony, child));
                for (IToken<?> grand : c.getChildren()) {
                    var gc = colony.getRequestManager().getRequestForToken(grand);
                    ArsKingdom.LOGGER.info("    ingredient {} state {} resolver {}", gc.getShortDisplayString().getString(),
                            gc.getState(), resolverOf(colony, grand));
                }
            }
            JobArcanist job = (JobArcanist) workshop.getFirstModuleOccurance(IAssignsJob.class).getAssignedCitizen().get(0).getJob();
            ArsKingdom.LOGGER.info("Arcanist tasks: queue {} assigned {}", job.getTaskQueue().size(), job.getAssignedTasks().size());
            helper.assertTrue(!job.getTaskQueue().isEmpty() || !job.getAssignedTasks().isEmpty(), "the Arcanist got no crafting task");
        } finally {
            IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, helper.getLevel());
        }
        helper.succeed();
    }

    /** A request posted before the workshop existed is picked up once the workshop is built and has its recipes. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void earlierSpellbookRequestMovesToArcanist(GameTestHelper helper) {
        IColony colony = colony(helper, "ak_request_test2");
        try {
            IBuilding tower = hut(helper, colony, new BlockPos(5, 1, 1), AKBlocks.MAGE_TOWERS.get(MageSchool.RED).get());
            IToken<?> token = colony.getRequestManager().createAndAssignRequest(tower.getRequester(),
                    new Tool(AKEquipmentTypes.SPELLBOOK.get(), 1, 1));
            String before = resolverOf(colony, token);
            workshop(helper, colony, new BlockPos(1, 1, 1));
            colony.getRequestManager().onColonyUpdate(r -> true);
            String after = resolverOf(colony, token);
            ArsKingdom.LOGGER.info("Spellbook request (request first) resolved by {} then {}", before, after);
            helper.assertTrue(after.contains("Crafting"), "after the workshop was built the request is still with " + after);
        } finally {
            IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, helper.getLevel());
        }
        helper.succeed();
    }

    /**
     * A real Arcanist (spawned citizen) with the ingredients in its hut crafts a tower's spellbook request.
     * Logs the AI state while it works, to see where it stops.
     */
    @GameTest(template = EMPTY, timeoutTicks = 2400)
    public static void arcanistCraftsSpellbook(GameTestHelper helper) {
        var level = helper.getLevel();
        level.setDayTime(1000);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        IColony colony = colony(helper, "ak_craft_test");
        IBuilding workshop = workshop(helper, colony, new BlockPos(2, 1, 2));
        workshop.setCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(4, 3, 4)));
        helper.setBlock(new BlockPos(0, 1, 0), BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar")));
        ((com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile) helper.getBlockEntity(new BlockPos(0, 1, 0))).setSource(10000);
        IBuilding tower = hut(helper, colony, new BlockPos(6, 1, 6), AKBlocks.MAGE_TOWERS.get(MageSchool.RED).get());
        var hutInv = workshop.getTileEntity().getItemHandlerCap((net.minecraft.core.Direction) null);
        for (String id : new String[]{"book", "iron_shovel", "iron_pickaxe", "iron_axe", "iron_sword"}) {
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(hutInv,
                    new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace(id))), false);
        }
        var tile = workshop.getTileEntity();
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> isBook = st -> st.is(net.minecraft.world.item.Items.BOOK);
        ArsKingdom.LOGGER.info("hut tile {} hasItemStack(book)={} chestWithBook={} containers={} inProvider={}",
                tile.getClass().getSimpleName(), tile.hasItemStack(isBook), tile.getPositionOfChestWithItemStack(isBook),
                workshop.getContainers(), com.minecolonies.api.util.InventoryUtils.hasItemInProvider(workshop, isBook));
        ICitizenData arcanist = workshop.getFirstModuleOccurance(IAssignsJob.class).getAssignedCitizen().get(0);
        colony.getCitizenManager().spawnOrCreateCitizen(arcanist, level, helper.absolutePos(new BlockPos(4, 1, 4)));
        IToken<?> token = colony.getRequestManager().createAndAssignRequest(tower.getRequester(),
                new Tool(AKEquipmentTypes.SPELLBOOK.get(), 1, 1));
        JobArcanist job = (JobArcanist) arcanist.getJob();
        int[] last = {-1};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            if (t % 40 == 0) {
                var entity = arcanist.getEntity().orElse(null);
                try {
                    var f = com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic.class.getDeclaredField("needsCurrently");
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var needs = (com.minecolonies.api.util.Tuple<java.util.function.Predicate<net.minecraft.world.item.ItemStack>, Integer>) f.get(job.getWorkerAI());
                    if (needs != null) {
                        StringBuilder m = new StringBuilder();
                        for (String id : new String[]{"book", "iron_shovel", "iron_pickaxe", "iron_axe", "iron_sword"}) {
                            var st = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(id)));
                            if (needs.getA().test(st)) {
                                m.append(id).append(' ');
                            }
                        }
                        ArsKingdom.LOGGER.info("  needs {} x{}", m, needs.getB());
                    }
                } catch (ReflectiveOperationException e) {
                    ArsKingdom.LOGGER.info("  needs ? {}", e.toString());
                }
                ArsKingdom.LOGGER.info("t={} state={} status={} queue={} assigned={} progress={} inv={} pos={}", t,
                        job.getWorkerAI() == null ? null : job.getWorkerAI().getState(), arcanist.getStatus(),
                        job.getTaskQueue().size(), job.getAssignedTasks().size(), job.getProgress(),
                        entity == null ? "no entity" : describe(entity.getInventoryCitizen()), entity == null ? null : entity.blockPosition());
            }
        });
        helper.succeedWhen(() -> {
            var jar = (com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile) helper.getBlockEntity(new BlockPos(0, 1, 0));
            helper.assertTrue(jar.getSource() == 10000 - 500, "the novice book's 500 source not paid yet: " + jar.getSource());
            // The book is crafted into the workshop; a courier (none in this test colony) would carry it to the tower.
            helper.assertTrue(com.minecolonies.api.util.InventoryUtils.hasItemInProvider(workshop,
                    st -> st.is(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ars_nouveau", "novice_spell_book")))),
                    "no novice spell book in the workshop yet");
            IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, level);
        });
    }

    private static String describe(com.minecolonies.api.inventory.InventoryCitizen inv) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < inv.getSlots(); i++) {
            var st = inv.getStackInSlot(i);
            if (!st.isEmpty()) {
                sb.append(st.getCount()).append(' ').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath()).append(", ");
            }
        }
        return sb.append(']').toString();
    }
}
