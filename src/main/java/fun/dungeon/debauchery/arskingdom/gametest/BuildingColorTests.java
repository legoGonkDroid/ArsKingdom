package fun.dungeon.debauchery.arskingdom.gametest;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.modules.RulesSettingsModule;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors.Zone;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Colored buildings: Mage Towers keep their school color, toggled per type in the Sanctum. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class BuildingColorTests {
    private static final String EMPTY = "gametest/empty";

    private BuildingColorTests() {
    }

    @GameTest(template = EMPTY)
    public static void zonePicking(GameTestHelper helper) {
        var entry = BuildingColors.MAGE_TOWERS;
        Zone outer = new Zone(new BoundingBox(0, 0, 0, 20, 10, 20), DyeColor.BLUE, entry);
        Zone inner = new Zone(new BoundingBox(5, 0, 5, 9, 10, 9), DyeColor.RED, entry);
        List<Zone> zones = List.of(outer, inner);
        helper.assertTrue(BuildingColors.pick(zones, new BlockPos(30, 1, 30)).isEmpty(), "outside every zone: no building color");
        helper.assertTrue(BuildingColors.pick(zones, new BlockPos(1, 1, 1)).map(Zone::dye).equals(Optional.of(DyeColor.BLUE)), "outer zone");
        helper.assertTrue(BuildingColors.pick(zones, new BlockPos(7, 1, 7)).map(Zone::dye).equals(Optional.of(DyeColor.RED)),
                "nested zones: the smaller building wins");
        helper.assertTrue(BuildingColors.pick(List.of(), BlockPos.ZERO).isEmpty(), "no zones");
        helper.assertTrue(BuildingColors.zoneAt(null, BlockPos.ZERO).isEmpty(), "no colony: no zone");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mageTowersRegisteredAndOnByDefault(GameTestHelper helper) {
        var entry = BuildingColors.MAGE_TOWERS;
        helper.assertTrue(BuildingColors.all().contains(entry), "mage towers are in the list");
        helper.assertTrue(entry.id().equals(ArsKingdom.id("mage_towers")), "id " + entry.id());
        helper.assertTrue(entry.settingKey().getUniqueId().equals(ArsKingdom.id("building_color/mage_towers")),
                "setting key " + entry.settingKey().getUniqueId());
        helper.assertTrue(AKConfig.buildingColorDefault(entry), "mage towers keep their color by default");
        helper.assertTrue(ColonyRules.buildingColor(null, entry), "no Sanctum: the config default applies");

        RulesSettingsModule module = new RulesSettingsModule();
        for (var e : BuildingColors.all()) {
            helper.assertTrue(module.get(e.settingKey(), !AKConfig.buildingColorDefault(e)) == AKConfig.buildingColorDefault(e),
                    "the Sanctum has a toggle for " + e.id() + ", seeded from the config");
        }
        helper.succeed();
    }

    /** The list is open: a new type gets its own key and toggle, and is off unless the config lists it. */
    @GameTest(template = EMPTY)
    public static void listIsExtendable(GameTestHelper helper) {
        var id = ArsKingdom.id("gametest_colored_hut");
        var entry = BuildingColors.all().stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseGet(() -> BuildingColors.register(id, b -> Optional.of(DyeColor.LIME)));
        helper.assertTrue(BuildingColors.all().contains(entry), "registered types are listed");
        helper.assertTrue(entry.settingKey().getUniqueId().equals(ArsKingdom.id("building_color/gametest_colored_hut")), "its own key");
        helper.assertTrue(!AKConfig.buildingColorDefault(entry), "a type the config doesn't list starts off");
        RulesSettingsModule module = new RulesSettingsModule();
        helper.assertTrue(!module.get(entry.settingKey(), true), "a new Sanctum gets a toggle for it");
        boolean threw = false;
        try {
            BuildingColors.register(id, b -> Optional.empty());
        } catch (IllegalStateException e) {
            threw = true;
        }
        helper.assertTrue(threw, "a type can't be registered twice");
        helper.succeed();
    }
}
