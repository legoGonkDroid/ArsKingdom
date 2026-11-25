package fun.dungeon.debauchery.arskingdom.battlemage;

import com.minecolonies.api.entity.citizen.Skill;
import java.util.Locale;
import net.minecraft.world.item.DyeColor;

/**
 * The four battlemage schools: the single source of truth for each school's colour, ids and skills.
 * A tower's school is fixed by its hut, and its colour is applied to the tower, the gear and the spells.
 */
public enum MageSchool {
    RED(DyeColor.RED),
    BLUE(DyeColor.BLUE),
    GREEN(DyeColor.GREEN),
    PURPLE(DyeColor.PURPLE);

    private final DyeColor dye;
    private final String id;

    MageSchool(DyeColor dye) {
        this.dye = dye;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** The school's colour, used for its hut, tower logs, robes, spellbook and spell particles. */
    public DyeColor dye() {
        return dye;
    }

    /** Lower-case id: {@code red}, {@code blue}, {@code green} or {@code purple}. */
    public String id() {
        return id;
    }

    /** Building id and blueprint name: {@code magetower<school>}. */
    public String hutId() {
        return "magetower" + id;
    }

    /** Guard type and job path: {@code battlemage_<school>}. */
    public String guardTypeId() {
        return "battlemage_" + id;
    }

    /** Worker module key: global across mods, so prefixed. */
    public String workModuleKey() {
        return "ars_kingdom_battlemage_" + id + "_work";
    }

    public Skill primarySkill() {
        return Skill.Mana;
    }

    public Skill secondarySkill() {
        return Skill.Focus;
    }

    /** The school with this colour, or null if no school uses it. */
    public static MageSchool byDye(DyeColor dye) {
        for (MageSchool school : values()) {
            if (school.dye == dye) {
                return school;
            }
        }
        return null;
    }
}
