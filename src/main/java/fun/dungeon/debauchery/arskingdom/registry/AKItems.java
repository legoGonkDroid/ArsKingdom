package fun.dungeon.debauchery.arskingdom.registry;

import com.minecolonies.api.items.ItemBlockHut;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AKItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArsKingdom.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArsKingdom.MODID);

    /** Hut items use MineColonies' ItemBlockHut, like every MineColonies hut. */
    public static final DeferredItem<ItemBlockHut> SOURCE_SANCTUM = ITEMS.register(AKBlocks.SOURCE_SANCTUM.getId().getPath(),
            () -> new ItemBlockHut(AKBlocks.SOURCE_SANCTUM.get(), new Item.Properties()));

    public static final DeferredItem<ItemBlockHut> ARCANIST_WORKSHOP = ITEMS.register(AKBlocks.ARCANIST_WORKSHOP.getId().getPath(),
            () -> new ItemBlockHut(AKBlocks.ARCANIST_WORKSHOP.get(), new Item.Properties()));

    public static final Map<MageSchool, DeferredItem<ItemBlockHut>> MAGE_TOWERS = new EnumMap<>(MageSchool.class);

    static {
        AKBlocks.MAGE_TOWERS.forEach((school, block) -> MAGE_TOWERS.put(school,
                ITEMS.register(block.getId().getPath(), () -> new ItemBlockHut(block.get(), new Item.Properties()))));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("ars_kingdom", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ars_kingdom"))
            .icon(() -> SOURCE_SANCTUM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(SOURCE_SANCTUM.get());
                output.accept(ARCANIST_WORKSHOP.get());
                MAGE_TOWERS.values().forEach(item -> output.accept(item.get()));
            })
            .build());

    private AKItems() {
    }
}
