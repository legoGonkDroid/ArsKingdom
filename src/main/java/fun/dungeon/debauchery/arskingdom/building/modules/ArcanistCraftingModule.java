package fun.dungeon.debauchery.arskingdom.building.modules;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;

/** The Arcanist's recipes: custom recipes only, keyed {@code arcanist_custom} (data/.../crafterrecipes). */
public class ArcanistCraftingModule extends AbstractCraftingBuildingModule.Custom {
    public ArcanistCraftingModule(JobEntry job) {
        super(job);
    }
}
