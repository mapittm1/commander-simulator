package forge.card;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/** isSaga must remain equivalent to the canonical subtype predicate. */
public class CommanderSagaTypeTest {
    private void equivalent(CardType type) {
        assertEquals(type.isSaga(), type.hasSubtype("Saga"));
    }

    private void check(int core, boolean explicit, boolean all, boolean excluded) {
        boolean originalCatalogMembership = CardType.Constant.CREATURE_TYPES.contains("Saga");
        try {
            // Exercise both catalog states; every case restores the shared catalog.
            for (boolean catalogMembership : new boolean[]{false, true}) {
                if (catalogMembership) CardType.Constant.CREATURE_TYPES.add("Saga");
                else CardType.Constant.CREATURE_TYPES.remove("Saga");
                CardType type = new CardType(true);
                if (core == 1 || core == 4 || core == 7) type.add("Creature");
                if (core == 2 || core == 7) type.add("Kindred");
                if (core == 3 || core == 4) type.add("Enchantment");
                if (core == 5) type.add("Artifact");
                if (core == 6) type.add("Instant");
                if (explicit) type.add("Saga");
                type.allCreatureTypes = all;
                if (excluded) type.excludedCreatureSubtypes.add("Saga");
                boolean expected = explicit || (all && catalogMembership
                        && (type.isCreature() || type.isKindred()) && !excluded);
                assertEquals(type.isSaga(), expected);
                equivalent(type);
                CardType copy = new CardType(type);
                equivalent(copy);
                copy.add("Saga");
                assertEquals(copy.isSaga(), true);
                equivalent(copy);
                copy.remove("Saga");
                equivalent(copy);
                copy.clear();
                equivalent(copy);
                assertEquals(type.isSaga(), expected, "Copy mutation changed the original type");
            }
        } finally {
            if (originalCatalogMembership) CardType.Constant.CREATURE_TYPES.add("Saga");
            else CardType.Constant.CREATURE_TYPES.remove("Saga");
        }
        assertEquals(CardType.Constant.CREATURE_TYPES.contains("Saga"), originalCatalogMembership);
    }
    @Test public void core0AbsentLimitedIncluded() { check(0, false, false, false); }
    @Test public void core0AbsentLimitedExcluded() { check(0, false, false, true); }
    @Test public void core0AbsentAllIncluded() { check(0, false, true, false); }
    @Test public void core0AbsentAllExcluded() { check(0, false, true, true); }
    @Test public void core0ExplicitLimitedIncluded() { check(0, true, false, false); }
    @Test public void core0ExplicitLimitedExcluded() { check(0, true, false, true); }
    @Test public void core0ExplicitAllIncluded() { check(0, true, true, false); }
    @Test public void core0ExplicitAllExcluded() { check(0, true, true, true); }
    @Test public void core1AbsentLimitedIncluded() { check(1, false, false, false); }
    @Test public void core1AbsentLimitedExcluded() { check(1, false, false, true); }
    @Test public void core1AbsentAllIncluded() { check(1, false, true, false); }
    @Test public void core1AbsentAllExcluded() { check(1, false, true, true); }
    @Test public void core1ExplicitLimitedIncluded() { check(1, true, false, false); }
    @Test public void core1ExplicitLimitedExcluded() { check(1, true, false, true); }
    @Test public void core1ExplicitAllIncluded() { check(1, true, true, false); }
    @Test public void core1ExplicitAllExcluded() { check(1, true, true, true); }
    @Test public void core2AbsentLimitedIncluded() { check(2, false, false, false); }
    @Test public void core2AbsentLimitedExcluded() { check(2, false, false, true); }
    @Test public void core2AbsentAllIncluded() { check(2, false, true, false); }
    @Test public void core2AbsentAllExcluded() { check(2, false, true, true); }
    @Test public void core2ExplicitLimitedIncluded() { check(2, true, false, false); }
    @Test public void core2ExplicitLimitedExcluded() { check(2, true, false, true); }
    @Test public void core2ExplicitAllIncluded() { check(2, true, true, false); }
    @Test public void core2ExplicitAllExcluded() { check(2, true, true, true); }
    @Test public void core3AbsentLimitedIncluded() { check(3, false, false, false); }
    @Test public void core3AbsentLimitedExcluded() { check(3, false, false, true); }
    @Test public void core3AbsentAllIncluded() { check(3, false, true, false); }
    @Test public void core3AbsentAllExcluded() { check(3, false, true, true); }
    @Test public void core3ExplicitLimitedIncluded() { check(3, true, false, false); }
    @Test public void core3ExplicitLimitedExcluded() { check(3, true, false, true); }
    @Test public void core3ExplicitAllIncluded() { check(3, true, true, false); }
    @Test public void core3ExplicitAllExcluded() { check(3, true, true, true); }
    @Test public void core4AbsentLimitedIncluded() { check(4, false, false, false); }
    @Test public void core4AbsentLimitedExcluded() { check(4, false, false, true); }
    @Test public void core4AbsentAllIncluded() { check(4, false, true, false); }
    @Test public void core4AbsentAllExcluded() { check(4, false, true, true); }
    @Test public void core4ExplicitLimitedIncluded() { check(4, true, false, false); }
    @Test public void core4ExplicitLimitedExcluded() { check(4, true, false, true); }
    @Test public void core4ExplicitAllIncluded() { check(4, true, true, false); }
    @Test public void core4ExplicitAllExcluded() { check(4, true, true, true); }
    @Test public void core5AbsentLimitedIncluded() { check(5, false, false, false); }
    @Test public void core5AbsentLimitedExcluded() { check(5, false, false, true); }
    @Test public void core5AbsentAllIncluded() { check(5, false, true, false); }
    @Test public void core5AbsentAllExcluded() { check(5, false, true, true); }
    @Test public void core5ExplicitLimitedIncluded() { check(5, true, false, false); }
    @Test public void core5ExplicitLimitedExcluded() { check(5, true, false, true); }
    @Test public void core5ExplicitAllIncluded() { check(5, true, true, false); }
    @Test public void core5ExplicitAllExcluded() { check(5, true, true, true); }
    @Test public void core6AbsentLimitedIncluded() { check(6, false, false, false); }
    @Test public void core6AbsentLimitedExcluded() { check(6, false, false, true); }
    @Test public void core6AbsentAllIncluded() { check(6, false, true, false); }
    @Test public void core6AbsentAllExcluded() { check(6, false, true, true); }
    @Test public void core6ExplicitLimitedIncluded() { check(6, true, false, false); }
    @Test public void core6ExplicitLimitedExcluded() { check(6, true, false, true); }
    @Test public void core6ExplicitAllIncluded() { check(6, true, true, false); }
    @Test public void core6ExplicitAllExcluded() { check(6, true, true, true); }
    @Test public void core7AbsentLimitedIncluded() { check(7, false, false, false); }
    @Test public void core7AbsentLimitedExcluded() { check(7, false, false, true); }
    @Test public void core7AbsentAllIncluded() { check(7, false, true, false); }
    @Test public void core7AbsentAllExcluded() { check(7, false, true, true); }
    @Test public void core7ExplicitLimitedIncluded() { check(7, true, false, false); }
    @Test public void core7ExplicitLimitedExcluded() { check(7, true, false, true); }
    @Test public void core7ExplicitAllIncluded() { check(7, true, true, false); }
    @Test public void core7ExplicitAllExcluded() { check(7, true, true, true); }
}
