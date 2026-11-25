package fun.dungeon.debauchery.arskingdom.arcanist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The source an Arcanist pays per crafted item, from {@code data/<namespace>/arcanist_source_costs/*.json}:
 * <pre>{"costs": {"ars_nouveau:apprentice_spell_book": 1000}}</pre>
 * Files are merged; a later pack overrides an item's cost. Items without a cost are free.
 */
public final class ArcanistSourceCosts extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "arcanist_source_costs";

    private static volatile Map<Item, Integer> costs = Map.of();

    private ArcanistSourceCosts() {
        super(new com.google.gson.Gson(), FOLDER);
    }

    /** Source for one item. */
    public static int cost(Item item) {
        return costs.getOrDefault(item, 0);
    }

    /** Source for a whole output stack. */
    public static int cost(ItemStack stack) {
        return stack.isEmpty() ? 0 : cost(stack.getItem()) * stack.getCount();
    }

    /** Parses one file's {@code costs} object into {@code into}; unknown items are skipped with a warning. */
    public static void parse(ResourceLocation file, JsonObject json, Map<Item, Integer> into) {
        JsonObject entries = GsonHelper.getAsJsonObject(json, "costs");
        for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                ArsKingdom.LOGGER.warn("{}: unknown item {}", file, e.getKey());
                continue;
            }
            into.put(BuiltInRegistries.ITEM.get(id), Math.max(0, e.getValue().getAsInt()));
        }
    }

    /** Replaces the table (tests and reloads). */
    public static void set(Map<Item, Integer> table) {
        costs = Map.copyOf(table);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, Integer> table = new HashMap<>();
        files.forEach((file, json) -> {
            try {
                parse(file, json.getAsJsonObject(), table);
            } catch (RuntimeException ex) {
                ArsKingdom.LOGGER.error("Bad Arcanist source cost file {}: {}", file, ex.getMessage());
            }
        });
        set(table);
        ArsKingdom.LOGGER.info("Loaded {} Arcanist source costs", table.size());
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ArcanistSourceCosts());
    }
}
