package fun.dungeon.debauchery.arskingdom.arcanist;

import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import fun.dungeon.debauchery.arskingdom.building.SourceKeeping;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Paying for an Arcanist craft from the source jars inside the workshop: all or nothing, fullest jars first. */
public final class ArcanistSource {
    private ArcanistSource() {
    }

    /** Total source in the jars inside the box. */
    public static int available(ServerLevel level, BlockPos cornerA, BlockPos cornerB) {
        return SourceKeeping.jars(level, cornerA, cornerB, false).stream().mapToInt(SourceJarTile::getSource).sum();
    }

    /** Takes {@code amount} from the jars inside the box, or nothing if they hold less. */
    public static boolean take(ServerLevel level, BlockPos cornerA, BlockPos cornerB, int amount) {
        if (amount <= 0) {
            return true;
        }
        List<SourceJarTile> jars = SourceKeeping.jars(level, cornerA, cornerB, false);
        if (jars.stream().mapToInt(SourceJarTile::getSource).sum() < amount) {
            return false;
        }
        jars.sort(Comparator.comparingInt(SourceJarTile::getSource).reversed());
        int left = amount;
        for (SourceJarTile jar : jars) {
            int before = jar.getSource();
            jar.removeSource(Math.min(left, before));     // returns what's left in the jar, not what was taken
            left -= before - jar.getSource();
            if (left <= 0) {
                break;
            }
        }
        return true;
    }
}
