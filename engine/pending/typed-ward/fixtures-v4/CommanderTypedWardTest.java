package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPayment;
import forge.game.cost.Cost;
import forge.game.keyword.Keyword;
import forge.game.trigger.Trigger;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Full typed ward costs: independent native casts and nonspending affordability forecasts. */
public class CommanderTypedWardTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("typed-0")).findFirst().orElseThrow();
    }

    private boolean paid(String mode) {
        return !List.of("waterShort", "lifeShortMana", "lifePolicyShort", "multiLifePolicy", "altNone", "altKeepForest", "varPolicy", "sacShort", "doubledWaterShort").contains(mode);
    }

    private int initialLife(String mode) {
        return mode.equals("varPaid") ? 11 : mode.equals("varPolicy") ? 10 : mode.equals("lifePolicyShort") ? 5 : mode.equals("multiLifePolicy") ? 7
                : mode.equals("multiLifePaid") ? 8 : 100;
    }

    private int artifacts(String mode) {
        return mode.equals("doubledWaterPaid") ? 9 : mode.equals("doubledWaterShort") ? 8 : mode.equals("uncounterable") ? 0 : mode.equals("sacPaid") ? 3 : mode.equals("sacShort") ? 2 : mode.equals("waterPaid") ? 5 : mode.startsWith("water") ? 4 : mode.startsWith("multi") ? 2 : 1;
    }

    private int forests(String mode) {
        return mode.equals("waterMixed") || mode.equals("lifeShortMana") ? 1
                : mode.startsWith("water") || mode.startsWith("var") || mode.startsWith("sac") || mode.startsWith("doubled") || mode.equals("uncounterable") || mode.equals("altKeepForest") || mode.equals("altDiscard") || mode.equals("altNone") ? 0
                : mode.startsWith("multi") ? 4 : 2;
    }

    private Game setup(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("typed-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Full typed ward costs"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        actor(game).setLife(initialLife(mode), null);
        addCardToZone("Crashing Wave", actor(game), ZoneType.Hand);
        if (mode.equals("altDiscard")) addCardToZone("Squee, Goblin Nabob", actor(game), ZoneType.Hand);
        if (mode.equals("altKeepForest")) addCardToZone("Forest", actor(game), ZoneType.Hand);
        for (int index = 0; index < 2; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        for (int index = 0; index < forests(mode); index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        for (int index = 0; index < artifacts(mode); index++) addCardToZone("Welding Jar", actor(game), ZoneType.Battlefield);
        Player opponent = game.getPlayers().get(1);
        addCardToZone(mode.startsWith("water") || mode.startsWith("doubled") || mode.equals("uncounterable") ? "The Unagi of Kyoshi Island"
                : mode.startsWith("alt") ? "Titania, Rugged Rumbler" : mode.startsWith("var") ? "Phyrexian Fleshgorger"
                : mode.startsWith("sac") ? "Valgavoth, Terror Eater" : mode.equals("pureMana") ? "Tolarian Terror" : "Gisa, the Hellraiser", opponent, ZoneType.Battlefield);
        if (mode.startsWith("multi")) addCardToZone("Captain Howler, Sea Scourge", opponent, ZoneType.Battlefield);
        if (mode.startsWith("doubled")) {
            Card throne = addCardToZone("Roaming Throne", opponent, ZoneType.Battlefield);
            throne.setChosenType("Serpent");
        }
        if (mode.equals("uncounterable")) {
            Card land = addCardToZone("Boseiju, Who Shelters All", actor(game), ZoneType.Battlefield);
            land.setTapped(false); // An already untapped permanent, not a claim about its entry replacement.
            actor(game).setLife(102, null);
            SpellAbility mana = land.getManaAbilities().get(0);
            mana.setActivatingPlayer(actor(game));
            assertTrue(new CostPayment(mana.getPayCosts(), mana).payComputerCosts(new AiCostDecision(actor(game), mana, false, true)));
            game.getStack().addAndUnfreeze(mana);
            assertEquals(actor(game).getLife(), 100);
            assertEquals(actor(game).getManaPool().totalMana(), 1);
        }
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) result.put(card.getId(), card.getZone().getZoneType() + ":" + card.isTapped() + ":" + card.isUsedToPay());
        for (Player player : game.getPlayers()) result.put(-player.getId(), "life=" + player.getLife() + ":pool=" + player.getManaPool().totalMana());
        return result;
    }

    private void check(String mode, int depth, boolean ordered) {
        Game original = setup(mode);
        Map<Integer, String> untouched = state(original);
        Game game = snapshot(original, depth);
        Player payer = actor(game);
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Crashing Wave"))
                .findFirst().orElseThrow().getFirstSpellAbility();
        spell.setActivatingPlayer(payer);
        int chosen = mode.startsWith("multi") ? 2 : 1;
        spell.setXManaCostPaid(chosen);
        List<Card> targets = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).filter(c -> !c.getName().equals("Roaming Throne")).toList();
        assertEquals(targets.size(), chosen);
        for (Card card : targets) spell.getTargets().add(card);
        assertTrue(spell.isTargetNumberValid());
        Map<Integer, String> before = state(game);
        if (!ordered) {
            assertEquals(ComputerUtilCost.canPayCost(spell, payer, false), paid(mode),
                    "Forecast every full ward cost using independent resources and the existing four-life policy");
            assertEquals(state(game), before);
            assertEquals(spell.getXManaCostPaid(), Integer.valueOf(chosen));
        } else {
            // Casting without the AI preflight is legal even if a later ward payment fails.
            Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            assertEquals(spell.getPayingMana().size(), mode.equals("uncounterable") ? 3 : 2);
            assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isArtifact() && c.isTapped()).count(), mode.equals("uncounterable") ? 0L : (long) chosen);
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().add(spell);
            GameSimulator.resolveStack(game, payer);
            assertEquals(targets.stream().allMatch(Card::isTapped), paid(mode), "Native paid/countered result must agree with the full typed cost");
            assertEquals(targets.stream().mapToInt(c -> c.getCounters(CounterEnumType.STUN)).sum(), paid(mode) ? 3 : 0);
            int expectedLife = initialLife(mode) - (mode.equals("lifePaid") ? 2
                    : mode.equals("multiLifePaid") ? 4 : mode.equals("multiLifePolicy") ? 2 : mode.equals("varPaid") ? 7 : 0);
            assertEquals(payer.getLife(), expectedLife);
            long expectedForestTaps = mode.equals("waterMixed") && paid(mode) ? 1
                    : mode.equals("lifePaid") || mode.equals("multiLifePolicy") || mode.equals("altMana") ? 2
                    : mode.equals("multiLifePaid") ? 4 : 0;
            assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest") && c.isTapped()).count(), expectedForestTaps);
            if (mode.startsWith("water") || mode.startsWith("doubled")) assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(c -> c.isArtifact() && c.isTapped()).count(), paid(mode) ? (long) artifacts(mode) : mode.equals("doubledWaterShort") ? 5L : (long) chosen);
            if (mode.equals("altDiscard")) assertEquals(payer.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals("Squee, Goblin Nabob")).count(), 1L);
            if (mode.startsWith("sac")) assertEquals(payer.getCardsIn(ZoneType.Exile).stream().filter(c -> c.getName().equals("Welding Jar")).count(), paid(mode) ? 3L : 0L);
            assertEquals(payer.getManaPool().totalMana(), 0);
        }
        if (depth > 0) assertEquals(state(original), untouched);
    }

    private void duplicateTargets(int depth, boolean ordered) {
        Game original = setup("lifePaid");
        addCardToZone("Plains", actor(original), ZoneType.Battlefield);
        addCardToZone("Seeds of Strength", actor(original), ZoneType.Hand);
        Game game = snapshot(original, depth);
        Player payer = actor(game);
        Card target = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).findFirst().orElseThrow();
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Seeds of Strength"))
                .findFirst().orElseThrow().getFirstSpellAbility();
        spell.setActivatingPlayer(payer);
        int groups = 0;
        for (SpellAbility part = spell; part != null; part = part.getSubAbility()) {
            if (part.usesTargeting()) { part.getTargets().add(target); groups++; }
        }
        assertEquals(groups, 3);
        Map<Integer, String> before = state(game);
        if (!ordered) {
            assertTrue(ComputerUtilCost.canPayCost(spell, payer, false), "One object targeted three times produces one ward trigger");
            assertEquals(state(game), before);
            return;
        }
        Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        spell.setHostCard(onStack);
        assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(spell);
        GameSimulator.resolveStack(game, payer);
        assertEquals(payer.getLife(), 98, "Pay the mana/life ward once");
        assertEquals(target.getNetPower(), 7);
        assertEquals(target.getNetToughness(), 7);
        assertEquals(payer.getManaPool().totalMana(), 0);
    }

    private Map<String, Integer> histories(Game game) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) {
            for (Trigger trigger : card.getTriggers()) {
                if (trigger.isKeyword(Keyword.WARD)) {
                    result.put(card.getId() + ":" + trigger.getId() + ":turn", trigger.getActivationsThisTurn());
                    result.put(card.getId() + ":" + trigger.getId() + ":game", trigger.getActivationsThisGame());
                }
            }
        }
        return result;
    }

    private void previewIsolation(int depth, String mode) {
        Game original = setup(mode.equals("held") ? "lifePaid" : mode.equals("lethal") ? "pureMana" : mode);
        if (mode.equals("lethal")) {
            actor(original).setLife(1, null);
            actor(original).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest")).findFirst().orElseThrow().setTapped(true);
            addCardToZone("City of Brass", actor(original), ZoneType.Battlefield);
        }
        Game game = snapshot(original, depth);
        Player payer = actor(game);
        Card held = mode.equals("held") ? payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest")).findFirst().orElseThrow() : null;
        if (held != null) AiCardMemory.rememberCard(payer, held, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Crashing Wave")).findFirst().orElseThrow().getFirstSpellAbility();
        spell.setActivatingPlayer(payer);
        spell.setXManaCostPaid(1);
        spell.getTargets().add(game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature)
                .filter(c -> !c.getName().equals("Roaming Throne")).findFirst().orElseThrow());
        Map<Integer, String> before = state(game);
        Map<String, Integer> beforeHistory = histories(game);
        Integer maximum = spell.getMaxWaterbend();
        long seed = 6200L + depth;
        java.util.Random reference = new java.util.Random(seed);
        forge.util.MyRandom.withRandom(new java.util.Random(seed), () -> AiCache.withIsolatedCache(() -> {
            Object token = new Object();
            String cached = AiCache.getCached("typed-ward-isolation", () -> "kept", null, token);
            java.util.Random provider = forge.util.MyRandom.getRandom();
            long calls = forge.util.MyRandom.callCount();
            for (int repeat = 0; repeat < 3; repeat++) {
                try {
                    boolean found = (Boolean) ComputerUtilMana.class.getMethod("canPayManaCostWithWardCosts", Cost.class, SpellAbility.class,
                            Player.class, int.class, boolean.class).invoke(null, spell.getPayCosts(), spell, payer, 0, false);
                    assertEquals(found, mode.equals("waterPaid") || mode.equals("doubledWaterPaid"));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Typed ward forecast API must be available", error);
                }
                assertEquals(state(game), before);
                assertEquals(histories(game), beforeHistory, "Copied triggerRun must not consume original ward histories");
                assertEquals(spell.getXManaCostPaid(), Integer.valueOf(1));
                assertEquals(spell.getMaxWaterbend(), maximum);
                if (held != null) assertTrue(AiCardMemory.isRememberedCard(payer, held, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL));
                assertSame(forge.util.MyRandom.getRandom(), provider);
                assertEquals(forge.util.MyRandom.callCount(), calls);
                assertEquals(AiCache.getCached("typed-ward-isolation", () -> "lost", null, token), cached);
            }
            assertEquals(provider.nextLong(), reference.nextLong());
            return null;
        }));
    }

    @Test
    public void waterPaidPreviewDepth0() {
        check("waterPaid", 0, false);
    }

    @Test
    public void waterPaidPreviewDepth1() {
        check("waterPaid", 1, false);
    }

    @Test
    public void waterPaidPreviewDepth2() {
        check("waterPaid", 2, false);
    }

    @Test
    public void waterPaidNativeDepth0() {
        check("waterPaid", 0, true);
    }

    @Test
    public void waterPaidNativeDepth1() {
        check("waterPaid", 1, true);
    }

    @Test
    public void waterPaidNativeDepth2() {
        check("waterPaid", 2, true);
    }

    @Test
    public void waterShortPreviewDepth0() {
        check("waterShort", 0, false);
    }

    @Test
    public void waterShortPreviewDepth1() {
        check("waterShort", 1, false);
    }

    @Test
    public void waterShortPreviewDepth2() {
        check("waterShort", 2, false);
    }

    @Test
    public void waterShortNativeDepth0() {
        check("waterShort", 0, true);
    }

    @Test
    public void waterShortNativeDepth1() {
        check("waterShort", 1, true);
    }

    @Test
    public void waterShortNativeDepth2() {
        check("waterShort", 2, true);
    }

    @Test
    public void waterMixedPreviewDepth0() {
        check("waterMixed", 0, false);
    }

    @Test
    public void waterMixedPreviewDepth1() {
        check("waterMixed", 1, false);
    }

    @Test
    public void waterMixedPreviewDepth2() {
        check("waterMixed", 2, false);
    }

    @Test
    public void waterMixedNativeDepth0() {
        check("waterMixed", 0, true);
    }

    @Test
    public void waterMixedNativeDepth1() {
        check("waterMixed", 1, true);
    }

    @Test
    public void waterMixedNativeDepth2() {
        check("waterMixed", 2, true);
    }

    @Test
    public void lifePaidPreviewDepth0() {
        check("lifePaid", 0, false);
    }

    @Test
    public void lifePaidPreviewDepth1() {
        check("lifePaid", 1, false);
    }

    @Test
    public void lifePaidPreviewDepth2() {
        check("lifePaid", 2, false);
    }

    @Test
    public void lifePaidNativeDepth0() {
        check("lifePaid", 0, true);
    }

    @Test
    public void lifePaidNativeDepth1() {
        check("lifePaid", 1, true);
    }

    @Test
    public void lifePaidNativeDepth2() {
        check("lifePaid", 2, true);
    }

    @Test
    public void lifeShortManaPreviewDepth0() {
        check("lifeShortMana", 0, false);
    }

    @Test
    public void lifeShortManaPreviewDepth1() {
        check("lifeShortMana", 1, false);
    }

    @Test
    public void lifeShortManaPreviewDepth2() {
        check("lifeShortMana", 2, false);
    }

    @Test
    public void lifeShortManaNativeDepth0() {
        check("lifeShortMana", 0, true);
    }

    @Test
    public void lifeShortManaNativeDepth1() {
        check("lifeShortMana", 1, true);
    }

    @Test
    public void lifeShortManaNativeDepth2() {
        check("lifeShortMana", 2, true);
    }

    @Test
    public void lifePolicyShortPreviewDepth0() {
        check("lifePolicyShort", 0, false);
    }

    @Test
    public void lifePolicyShortPreviewDepth1() {
        check("lifePolicyShort", 1, false);
    }

    @Test
    public void lifePolicyShortPreviewDepth2() {
        check("lifePolicyShort", 2, false);
    }

    @Test
    public void lifePolicyShortNativeDepth0() {
        check("lifePolicyShort", 0, true);
    }

    @Test
    public void lifePolicyShortNativeDepth1() {
        check("lifePolicyShort", 1, true);
    }

    @Test
    public void lifePolicyShortNativeDepth2() {
        check("lifePolicyShort", 2, true);
    }

    @Test
    public void multiLifePaidPreviewDepth0() {
        check("multiLifePaid", 0, false);
    }

    @Test
    public void multiLifePaidPreviewDepth1() {
        check("multiLifePaid", 1, false);
    }

    @Test
    public void multiLifePaidPreviewDepth2() {
        check("multiLifePaid", 2, false);
    }

    @Test
    public void multiLifePaidNativeDepth0() {
        check("multiLifePaid", 0, true);
    }

    @Test
    public void multiLifePaidNativeDepth1() {
        check("multiLifePaid", 1, true);
    }

    @Test
    public void multiLifePaidNativeDepth2() {
        check("multiLifePaid", 2, true);
    }

    @Test
    public void multiLifePolicyPreviewDepth0() {
        check("multiLifePolicy", 0, false);
    }

    @Test
    public void multiLifePolicyPreviewDepth1() {
        check("multiLifePolicy", 1, false);
    }

    @Test
    public void multiLifePolicyPreviewDepth2() {
        check("multiLifePolicy", 2, false);
    }

    @Test
    public void multiLifePolicyNativeDepth0() {
        check("multiLifePolicy", 0, true);
    }

    @Test
    public void multiLifePolicyNativeDepth1() {
        check("multiLifePolicy", 1, true);
    }

    @Test
    public void multiLifePolicyNativeDepth2() {
        check("multiLifePolicy", 2, true);
    }

    @Test
    public void altManaPreviewDepth0() {
        check("altMana", 0, false);
    }

    @Test
    public void altManaPreviewDepth1() {
        check("altMana", 1, false);
    }

    @Test
    public void altManaPreviewDepth2() {
        check("altMana", 2, false);
    }

    @Test
    public void altManaNativeDepth0() {
        check("altMana", 0, true);
    }

    @Test
    public void altManaNativeDepth1() {
        check("altMana", 1, true);
    }

    @Test
    public void altManaNativeDepth2() {
        check("altMana", 2, true);
    }

    @Test
    public void altDiscardPreviewDepth0() {
        check("altDiscard", 0, false);
    }

    @Test
    public void altDiscardPreviewDepth1() {
        check("altDiscard", 1, false);
    }

    @Test
    public void altDiscardPreviewDepth2() {
        check("altDiscard", 2, false);
    }

    @Test
    public void altDiscardNativeDepth0() {
        check("altDiscard", 0, true);
    }

    @Test
    public void altDiscardNativeDepth1() {
        check("altDiscard", 1, true);
    }

    @Test
    public void altDiscardNativeDepth2() {
        check("altDiscard", 2, true);
    }

    @Test
    public void altNonePreviewDepth0() {
        check("altNone", 0, false);
    }

    @Test
    public void altNonePreviewDepth1() {
        check("altNone", 1, false);
    }

    @Test
    public void altNonePreviewDepth2() {
        check("altNone", 2, false);
    }

    @Test
    public void altNoneNativeDepth0() {
        check("altNone", 0, true);
    }

    @Test
    public void altNoneNativeDepth1() {
        check("altNone", 1, true);
    }

    @Test
    public void altNoneNativeDepth2() {
        check("altNone", 2, true);
    }

    @Test
    public void altKeepForestPreviewDepth0() {
        check("altKeepForest", 0, false);
    }

    @Test
    public void altKeepForestPreviewDepth1() {
        check("altKeepForest", 1, false);
    }

    @Test
    public void altKeepForestPreviewDepth2() {
        check("altKeepForest", 2, false);
    }

    @Test
    public void altKeepForestNativeDepth0() {
        check("altKeepForest", 0, true);
    }

    @Test
    public void altKeepForestNativeDepth1() {
        check("altKeepForest", 1, true);
    }

    @Test
    public void altKeepForestNativeDepth2() {
        check("altKeepForest", 2, true);
    }

    @Test
    public void varPaidPreviewDepth0() {
        check("varPaid", 0, false);
    }

    @Test
    public void varPaidPreviewDepth1() {
        check("varPaid", 1, false);
    }

    @Test
    public void varPaidPreviewDepth2() {
        check("varPaid", 2, false);
    }

    @Test
    public void varPaidNativeDepth0() {
        check("varPaid", 0, true);
    }

    @Test
    public void varPaidNativeDepth1() {
        check("varPaid", 1, true);
    }

    @Test
    public void varPaidNativeDepth2() {
        check("varPaid", 2, true);
    }

    @Test
    public void varPolicyPreviewDepth0() {
        check("varPolicy", 0, false);
    }

    @Test
    public void varPolicyPreviewDepth1() {
        check("varPolicy", 1, false);
    }

    @Test
    public void varPolicyPreviewDepth2() {
        check("varPolicy", 2, false);
    }

    @Test
    public void varPolicyNativeDepth0() {
        check("varPolicy", 0, true);
    }

    @Test
    public void varPolicyNativeDepth1() {
        check("varPolicy", 1, true);
    }

    @Test
    public void varPolicyNativeDepth2() {
        check("varPolicy", 2, true);
    }

    @Test
    public void sacPaidPreviewDepth0() {
        check("sacPaid", 0, false);
    }

    @Test
    public void sacPaidPreviewDepth1() {
        check("sacPaid", 1, false);
    }

    @Test
    public void sacPaidPreviewDepth2() {
        check("sacPaid", 2, false);
    }

    @Test
    public void sacPaidNativeDepth0() {
        check("sacPaid", 0, true);
    }

    @Test
    public void sacPaidNativeDepth1() {
        check("sacPaid", 1, true);
    }

    @Test
    public void sacPaidNativeDepth2() {
        check("sacPaid", 2, true);
    }

    @Test
    public void sacShortPreviewDepth0() {
        check("sacShort", 0, false);
    }

    @Test
    public void sacShortPreviewDepth1() {
        check("sacShort", 1, false);
    }

    @Test
    public void sacShortPreviewDepth2() {
        check("sacShort", 2, false);
    }

    @Test
    public void sacShortNativeDepth0() {
        check("sacShort", 0, true);
    }

    @Test
    public void sacShortNativeDepth1() {
        check("sacShort", 1, true);
    }

    @Test
    public void sacShortNativeDepth2() {
        check("sacShort", 2, true);
    }

    @Test
    public void doubledWaterPaidPreviewDepth0() {
        check("doubledWaterPaid", 0, false);
    }

    @Test
    public void doubledWaterPaidPreviewDepth1() {
        check("doubledWaterPaid", 1, false);
    }

    @Test
    public void doubledWaterPaidPreviewDepth2() {
        check("doubledWaterPaid", 2, false);
    }

    @Test
    public void doubledWaterPaidNativeDepth0() {
        check("doubledWaterPaid", 0, true);
    }

    @Test
    public void doubledWaterPaidNativeDepth1() {
        check("doubledWaterPaid", 1, true);
    }

    @Test
    public void doubledWaterPaidNativeDepth2() {
        check("doubledWaterPaid", 2, true);
    }

    @Test
    public void doubledWaterShortPreviewDepth0() {
        check("doubledWaterShort", 0, false);
    }

    @Test
    public void doubledWaterShortPreviewDepth1() {
        check("doubledWaterShort", 1, false);
    }

    @Test
    public void doubledWaterShortPreviewDepth2() {
        check("doubledWaterShort", 2, false);
    }

    @Test
    public void doubledWaterShortNativeDepth0() {
        check("doubledWaterShort", 0, true);
    }

    @Test
    public void doubledWaterShortNativeDepth1() {
        check("doubledWaterShort", 1, true);
    }

    @Test
    public void doubledWaterShortNativeDepth2() {
        check("doubledWaterShort", 2, true);
    }

    @Test
    public void uncounterablePreviewDepth0() {
        check("uncounterable", 0, false);
    }

    @Test
    public void uncounterablePreviewDepth1() {
        check("uncounterable", 1, false);
    }

    @Test
    public void uncounterablePreviewDepth2() {
        check("uncounterable", 2, false);
    }

    @Test
    public void uncounterableNativeDepth0() {
        check("uncounterable", 0, true);
    }

    @Test
    public void uncounterableNativeDepth1() {
        check("uncounterable", 1, true);
    }

    @Test
    public void uncounterableNativeDepth2() {
        check("uncounterable", 2, true);
    }

    @Test
    public void duplicateTargetsPreviewDepth0() {
        duplicateTargets(0, false);
    }

    @Test
    public void duplicateTargetsPreviewDepth1() {
        duplicateTargets(1, false);
    }

    @Test
    public void duplicateTargetsPreviewDepth2() {
        duplicateTargets(2, false);
    }

    @Test
    public void duplicateTargetsNativeDepth0() {
        duplicateTargets(0, true);
    }

    @Test
    public void duplicateTargetsNativeDepth1() {
        duplicateTargets(1, true);
    }

    @Test
    public void duplicateTargetsNativeDepth2() {
        duplicateTargets(2, true);
    }

    @Test
    public void isolationWaterpaidDepth0() {
        previewIsolation(0, "waterPaid");
    }

    @Test
    public void isolationWaterpaidDepth1() {
        previewIsolation(1, "waterPaid");
    }

    @Test
    public void isolationWaterpaidDepth2() {
        previewIsolation(2, "waterPaid");
    }

    @Test
    public void isolationVarpolicyDepth0() {
        previewIsolation(0, "varPolicy");
    }

    @Test
    public void isolationVarpolicyDepth1() {
        previewIsolation(1, "varPolicy");
    }

    @Test
    public void isolationVarpolicyDepth2() {
        previewIsolation(2, "varPolicy");
    }

    @Test
    public void isolationDoubledwaterpaidDepth0() {
        previewIsolation(0, "doubledWaterPaid");
    }

    @Test
    public void isolationDoubledwaterpaidDepth1() {
        previewIsolation(1, "doubledWaterPaid");
    }

    @Test
    public void isolationDoubledwaterpaidDepth2() {
        previewIsolation(2, "doubledWaterPaid");
    }

    @Test
    public void isolationHeldDepth0() {
        previewIsolation(0, "held");
    }

    @Test
    public void isolationHeldDepth1() {
        previewIsolation(1, "held");
    }

    @Test
    public void isolationHeldDepth2() {
        previewIsolation(2, "held");
    }

    @Test
    public void isolationLethalDepth0() {
        previewIsolation(0, "lethal");
    }

    @Test
    public void isolationLethalDepth1() {
        previewIsolation(1, "lethal");
    }

    @Test
    public void isolationLethalDepth2() {
        previewIsolation(2, "lethal");
    }
}
