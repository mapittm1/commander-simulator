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
import forge.card.mana.ManaCost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.game.trigger.TriggerWaiting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Compare proposed-X affordability to independently paid native waterbend actions. */
public class CommanderWaterbendXTest extends AITest {
    private static final String KATARA = "Katara, Water Tribe's Hope";
    private static final String RESTORATION = "Waterbender's Restoration";
    private static final String VISIONS = "Foggy Swamp Visions";
    private static final String WAVE = "Crashing Wave";

    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("water-x-0")).findFirst().orElseThrow();
    }

    private Game setup(String name) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("water-x-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Variable waterbend payments"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        if (name.equals(KATARA)) {
            addCardToZone(name, actor(game), ZoneType.Battlefield);
            for (int index = 0; index < 2; index++) addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
        } else {
            addCardToZone(name, actor(game), ZoneType.Hand);
            if (name.equals(RESTORATION)) {
                addCardToZone("Grizzly Bears", actor(game), ZoneType.Battlefield);
                addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
                addCardToZone("Memnite", actor(game), ZoneType.Battlefield);
            } else {
                for (int index = 0; index < 3; index++) addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
            }
            if (name.equals(VISIONS)) {
                addCardToZone("Swamp", actor(game), ZoneType.Battlefield);
                addCardToZone("Swamp", actor(game), ZoneType.Battlefield);
                addCardToZone("Forest", actor(game), ZoneType.Battlefield);
                for (int index = 0; index < 2; index++) addCardToZone("Grizzly Bears", actor(game), ZoneType.Graveyard);
            } else {
                for (int index = 0; index < 2; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
            }
        }
        if (name.equals(WAVE)) {
            for (int index = 0; index < 2; index++) addCardToZone("Grizzly Bears", game.getPlayers().get(1), ZoneType.Battlefield);
        }
        assertEquals(eligible(game).size(), 3);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private List<Card> eligible(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isCreature() || c.isArtifact()).toList();
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

    private SpellAbility ability(Game game, String name) {
        Card card = actor(game).getCardsIn(name.equals(KATARA) ? ZoneType.Battlefield : ZoneType.Hand).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        SpellAbility result = name.equals(KATARA)
                ? card.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && !a.isManaAbility()).findFirst().orElseThrow()
                : card.getFirstSpellAbility();
        result.setActivatingPlayer(actor(game));
        return result;
    }

    private Map<Integer, String> board(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) {
            result.put(card.getId(), card.getZone().getZoneType() + ":" + card.isTapped() + ":" + card.isUsedToPay());
        }
        for (Player player : game.getPlayers()) {
            result.put(-player.getId(), "life=" + player.getLife() + ":pool=" + player.getManaPool().totalMana());
        }
        return result;
    }

    private void target(Game game, SpellAbility action, String name) {
        if (name.equals(KATARA)) return;
        List<Card> targets = name.equals(RESTORATION) ? actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList()
                : name.equals(VISIONS) ? actor(game).getCardsIn(ZoneType.Graveyard).stream().filter(Card::isCreature).toList()
                : game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList();
        int count = Math.min(action.getXManaCostPaid(), targets.size());
        for (int index = 0; index < count; index++) action.getTargets().add(targets.get(index));
    }

    private void check(String name, int depth, String mode) {
        Game original = setup(name);
        if (mode.equals("insufficientActual")) {
            if (name.equals(RESTORATION)) addCardToZone("Grizzly Bears", actor(original), ZoneType.Battlefield).setTapped(true);
            if (name.equals(VISIONS)) {
                for (int index = 0; index < 2; index++) addCardToZone("Grizzly Bears", actor(original), ZoneType.Graveyard);
            }
        }
        Map<Integer, String> untouched = board(original);
        Game game = snapshot(original, depth);
        SpellAbility action = ability(game, name);
        Map<Integer, String> before = board(game);
        if (mode.equals("requested") || mode.equals("maximum") || mode.equals("insufficientPreview")) {
            assertNull(action.getXManaCostPaid());
            if (mode.equals("maximum")) {
                assertEquals(ComputerUtilMana.determineLeftoverMana(action, actor(game), false), 3,
                        "Three independent eligible permanents pay X; normal printed mana remains payable separately");
            } else {
                boolean payable = ComputerUtilMana.canPayManaCost(action, actor(game), mode.equals("requested") ? 2 : 4, false);
                assertEquals(payable, mode.equals("requested"), "Forecast the proposed X, not an absent stored X");
            }
            assertNull(action.getXManaCostPaid(), "Forecasting must not retain a chosen X");
            assertEquals(board(game), before);
        } else if (mode.equals("requestedWithExistingX")) {
            action.setXManaCostPaid(4);
            Integer previousMaximum = action.getMaxWaterbend();
            assertTrue(ComputerUtilMana.canPayManaCost(action, actor(game), 2, false),
                    "The proposed X=2 supersedes a previously selected X=4 only within the forecast");
            assertEquals(action.getXManaCostPaid(), Integer.valueOf(4));
            assertEquals(action.getMaxWaterbend(), previousMaximum, "A proposal must not retain derived waterbend metadata");
            assertEquals(board(game), before);
        } else if (mode.equals("chosenPreview")) {
            action.setXManaCostPaid(2);
            assertTrue(ComputerUtilCost.canPayCost(action, actor(game), false));
            assertEquals(action.getXManaCostPaid(), Integer.valueOf(2));
            assertEquals(board(game), before);
        } else if (mode.equals("insufficientActual")) {
            action.setXManaCostPaid(4);
            target(game, action, name);
            assertFalse(ComputerUtilCost.canPayCost(action, actor(game), false));
            assertFalse(ComputerUtil.playStack(action, actor(game), game));
            assertTrue(game.getStack().isEmpty());
            assertEquals(board(game), before, "The preflight refusal must leave resources untouched");
            if (action.usesTargeting()) assertTrue(action.isTargetNumberValid(), "The negative payment witness needs a legal target count");
            CostPayment payment = new CostPayment(action.getPayCosts(), action);
            assertFalse(payment.payComputerCosts(new AiCostDecision(actor(game), action, false, true)));
            payment.refundPayment();
            assertEquals(board(game), before, "The full native payment/refund lifecycle restores reversible costs");
        } else {
            assertEquals(mode, "actual");
            action.setXManaCostPaid(2);
            target(game, action, name);
            assertTrue(action.canPlay());
            assertTrue(ComputerUtil.playStack(action, actor(game), game), "Pay chosen X with independent native costs");
            SpellAbility paid = game.getStack().peekAbility();
            assertEquals(paid.getXManaCostPaid(), Integer.valueOf(2));
            assertEquals(eligible(game).stream().filter(Card::isTapped).count(), 2L);
            assertEquals(paid.getPayingMana().size(), name.equals(KATARA) ? 0 : name.equals(VISIONS) ? 3 : 2);
            GameSimulator.resolveStack(game, actor(game));
            if (name.equals(KATARA)) {
                for (Card card : eligible(game)) {
                    assertEquals(card.getNetPower(), 2);
                    assertEquals(card.getNetToughness(), 2);
                }
            } else if (name.equals(RESTORATION)) {
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 2);
            } else if (name.equals(VISIONS)) {
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 2);
                assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 2L);
            } else {
                List<Card> opponents = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList();
                assertTrue(opponents.stream().allMatch(Card::isTapped));
                assertEquals(opponents.stream().mapToInt(c -> c.getCounters(CounterEnumType.STUN)).sum(), 3);
            }
        }
        if (depth > 0) assertEquals(board(original), untouched, "Copied-game payments must leave the original independent");
    }

    private void zeroKatara(int depth) {
        Game game = snapshot(setup(KATARA), depth);
        SpellAbility action = ability(game, KATARA);
        action.setXManaCostPaid(0);
        Map<Integer, String> before = board(game);
        assertFalse(ComputerUtil.playStack(action, actor(game), game), "Katara explicitly forbids choosing X=0");
        assertTrue(game.getStack().isEmpty());
        assertEquals(board(game), before);
    }

    private void directZeroKatara(int depth) {
        Game game = snapshot(setup(KATARA), depth);
        SpellAbility action = ability(game, KATARA);
        action.setXManaCostPaid(0);
        Map<Integer, String> before = board(game);
        assertFalse(new CostPayment(action.getPayCosts(), action).payComputerCosts(new AiCostDecision(actor(game), action, false, true)),
                "Native payment itself must reject an explicitly forbidden X=0");
        assertEquals(board(game), before);
    }

    private void legalZeroSpell(String name, int depth) {
        Game game = snapshot(setup(name), depth);
        SpellAbility action = ability(game, name);
        action.setXManaCostPaid(0);
        assertTrue(ComputerUtilCost.canPayCost(action, actor(game), false), "These cards permit X=0");
        assertTrue(ComputerUtil.playStack(action, actor(game), game));
        assertEquals(game.getStack().peekAbility().getXManaCostPaid(), Integer.valueOf(0));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), name.equals(VISIONS) ? 3 : 2);
        assertEquals(eligible(game).stream().filter(Card::isTapped).count(), 0L);
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 0);
        assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 0L);
    }

    private void otherMinimum(String name, int depth, int chosen) {
        Game original = setup(KATARA);
        addCardToZone(name, actor(original), name.equals("Endless One") ? ZoneType.Hand : ZoneType.Battlefield);
        for (int index = 0; index < 2; index++) addCardToZone(name.equals("Corpseweft") ? "Swamp" : "Forest", actor(original), ZoneType.Battlefield);
        if (name.equals("Corpseweft")) addCardToZone("Grizzly Bears", actor(original), ZoneType.Graveyard);
        Game game = snapshot(original, depth);
        Card host = actor(game).getCardsIn(name.equals("Endless One") ? ZoneType.Hand : ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        SpellAbility action = name.equals("Endless One") ? host.getFirstSpellAbility()
                : host.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && !a.isManaAbility()).findFirst().orElseThrow();
        action.setActivatingPlayer(actor(game));
        action.setXManaCostPaid(chosen);
        Map<Integer, String> before = board(game);
        if (chosen == 0 && !name.equals("Endless One")) {
            assertFalse(ComputerUtilCost.canPayCost(action, actor(game), false), "Respect the declared minimum before paying a mana or exile cost");
            assertFalse(new CostPayment(action.getPayCosts(), action).payComputerCosts(new AiCostDecision(actor(game), action, false, true)));
            assertEquals(board(game), before);
        } else {
            assertTrue(ComputerUtil.playStack(action, actor(game), game));
            assertEquals(game.getStack().peekAbility().getXManaCostPaid(), Integer.valueOf(chosen));
            GameSimulator.resolveStack(game, actor(game));
            if (name.equals("Lair of the Hydra")) {
                Card animated = game.getCardState(host);
                assertTrue(animated.isCreature());
                assertEquals(animated.getNetPower(), 1);
                assertEquals(animated.getNetToughness(), 1);
            } else if (name.equals("Corpseweft")) {
                List<Card> tokens = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
                assertEquals(tokens.size(), 1);
                assertEquals(tokens.get(0).getNetPower(), 2);
                assertEquals(tokens.get(0).getNetToughness(), 2);
                assertTrue(tokens.get(0).isTapped());
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 1);
            } else {
                assertTrue(game.getCardState(host).isInZone(ZoneType.Graveyard), "A legal zero-X creature cast still follows native state-based actions");
            }
        }
    }

    private void unannouncedMinimum(String name, int depth, boolean aiEntry) {
        Game original = setup(KATARA);
        if (!name.equals(KATARA)) {
            addCardToZone(name, actor(original), ZoneType.Battlefield);
            for (int index = 0; index < 2; index++) addCardToZone(name.equals("Corpseweft") ? "Swamp" : "Forest", actor(original), ZoneType.Battlefield);
            if (name.equals("Corpseweft")) addCardToZone("Grizzly Bears", actor(original), ZoneType.Graveyard);
        }
        Map<Integer, String> untouched = board(original);
        Game game = snapshot(original, depth);
        Card host = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        SpellAbility action = host.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && !a.isManaAbility()).findFirst().orElseThrow();
        action.setActivatingPlayer(actor(game));
        assertNull(action.getXManaCostPaid());
        Map<Integer, String> before = board(game);
        CostPayment payment = new CostPayment(action.getPayCosts(), action);
        boolean paid = aiEntry ? ComputerUtil.playStack(action, actor(game), game)
                : payment.payComputerCosts(new AiCostDecision(actor(game), action, false, true));
        if (paid) {
            SpellAbility completed = aiEntry ? game.getStack().peekAbility() : action;
            assertNotNull(completed.getXManaCostPaid(), "Successful native payment must finish the minimum-X choice");
            assertTrue(completed.getXManaCostPaid() >= 1, "A missing announcement cannot become a forbidden zero");
            int chosen = completed.getXManaCostPaid();
            if (name.equals(KATARA)) assertEquals(eligible(game).stream().filter(Card::isTapped).count(), (long) chosen);
            if (name.equals("Lair of the Hydra")) assertEquals(completed.getPayingMana().size(), chosen + 1);
            if (name.equals("Corpseweft")) {
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), chosen);
                assertEquals(completed.getPayingMana().size(), 2);
            }
        } else {
            if (!aiEntry) payment.refundPayment();
            assertEquals(board(game), before, "Refused pending choices use the full native refund lifecycle");
            assertTrue(game.getStack().isEmpty());
        }
        if (depth > 0) assertEquals(board(original), untouched);
    }

    private void wardBudget(int depth, int forests, boolean execute) {
        Game original = setup(WAVE);
        Player opponent = original.getPlayers().get(1);
        addCardToZone("Tolarian Terror", opponent, ZoneType.Battlefield);
        for (int index = 0; index < forests; index++) addCardToZone("Forest", actor(original), ZoneType.Battlefield);
        Game game = snapshot(original, depth);
        SpellAbility action = ability(game, WAVE);
        action.setXManaCostPaid(3);
        Card target = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Tolarian Terror")).findFirst().orElseThrow();
        action.getTargets().add(target);
        Map<Integer, String> before = board(game);
        assertEquals(ComputerUtilCost.canPayCost(action, actor(game), false), forests == 2,
                "The chosen X=3 waterbend cost and separate ward2 need independent resources");
        assertEquals(action.getXManaCostPaid(), Integer.valueOf(3));
        assertEquals(board(game), before);
        if (!execute) return;
        assertEquals(forests, 2);
        assertTrue(ComputerUtil.playStack(action, actor(game), game));
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getXManaCostPaid(), Integer.valueOf(3));
        assertEquals(paid.getPayingMana().size(), 2, "The spell pays UU; ward is a separate cost");
        assertEquals(eligible(game).stream().filter(Card::isTapped).count(), 3L);
        GameSimulator.resolveStack(game, actor(game));
        assertTrue(target.isTapped());
        assertEquals(target.getCounters(CounterEnumType.STUN), 3);
        assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest") && c.isTapped()).count(), 2L,
                "The native ward trigger actually consumes both additional mana sources");
        assertEquals(actor(game).getManaPool().totalMana(), 0);
    }

    private void restrictedWard(int depth, boolean blue, boolean ordered) {
        Game original = setup(WAVE);
        addCardToZone("Tolarian Terror", original.getPlayers().get(1), ZoneType.Battlefield);
        for (int index = 0; index < 2; index++) {
            Card source = addCardToZone(blue ? "Hydro-Channeler" : "Curious Homunculus", actor(original), ZoneType.Battlefield);
            source.setSickness(false);
            SpellAbility mana = source.getManaAbilities().get(0);
            mana.setActivatingPlayer(actor(original));
            assertTrue(mana.canPlay());
            assertTrue(new CostPayment(mana.getPayCosts(), mana).payComputerCosts(new AiCostDecision(actor(original), mana, false, true)));
            original.getStack().addAndUnfreeze(mana);
            assertTrue(source.isTapped());
        }
        assertEquals(actor(original).getManaPool().totalMana(), 2);
        Game game = snapshot(original, depth);
        SpellAbility action = ability(game, WAVE);
        action.setXManaCostPaid(3);
        Card target = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Tolarian Terror")).findFirst().orElseThrow();
        action.getTargets().add(target);
        Map<Integer, String> before = board(game);
        if (!ordered) {
            assertEquals(ComputerUtilCost.canPayCost(action, actor(game), false), blue,
                    "Restricted CC cannot pay a ward ability; restricted UU can pay the spell and preserve Islands for ward");
            assertEquals(board(game), before);
            assertEquals(action.getXManaCostPaid(), Integer.valueOf(3));
            return;
        }
        // Independent native cast bypasses the AI affordability prediction under test.
        Card onStack = game.getAction().moveToStack(action.getHostCard(), action);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        action.setHostCard(onStack);
        assertTrue(new CostPayment(action.getPayCosts(), action).payComputerCosts(new AiCostDecision(actor(game), action, false, true)));
        assertEquals(action.getPayingMana().size(), 2);
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(action);
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(target.isTapped(), blue, "Native ward permits only an actual legal separate payment");
        assertEquals(target.getCounters(CounterEnumType.STUN), blue ? 3 : 0);
        assertEquals(actor(game).getManaPool().totalMana(), blue ? 0 : 2,
                "Unspendable cast-only CC remains floating after ward counters the spell");
    }

    private boolean extraBudget(Cost cost, SpellAbility ability, Player payer, int extra, boolean effect) {
        try {
            return (Boolean) ComputerUtilMana.class.getMethod("canPayManaCostWithExtraBudget",
                    Cost.class, SpellAbility.class, Player.class, int.class, boolean.class)
                    .invoke(null, cost, ability, payer, extra, effect);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("The separate-budget native forecast API must be available", error);
        }
    }

    private void budgetIsolation(int depth, String mode) {
        Game original = setup(WAVE);
        addCardToZone("Tolarian Terror", original.getPlayers().get(1), ZoneType.Battlefield);
        int forests = mode.equals("short") ? 0 : mode.equals("lethal") ? 1 : 2;
        for (int index = 0; index < forests; index++) addCardToZone("Forest", actor(original), ZoneType.Battlefield);
        if (mode.equals("lethal")) {
            actor(original).setLife(1, null);
            addCardToZone("City of Brass", actor(original), ZoneType.Battlefield);
        }
        Game game = snapshot(original, depth);
        Card held = mode.equals("held") ? actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Forest")).findFirst().orElseThrow() : null;
        if (held != null) AiCardMemory.rememberCard(actor(game), held, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);
        SpellAbility action = ability(game, WAVE);
        action.setXManaCostPaid(3);
        action.getTargets().add(game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Tolarian Terror")).findFirst().orElseThrow());
        Map<Integer, String> before = board(game);
        Integer maximum = action.getMaxWaterbend();
        Card host = action.getHostCard();
        for (Card card : actor(game).getCardsIn(ZoneType.Battlefield)) {
            for (SpellAbility mana : card.getManaAbilities()) mana.setActivatingPlayer(null);
        }
        long seed = 3100L + depth;
        java.util.Random reference = new java.util.Random(seed);
        forge.util.MyRandom.withRandom(new java.util.Random(seed), () -> AiCache.withIsolatedCache(() -> {
            Object token = new Object();
            String cached = AiCache.getCached("budget-preview-sentinel", () -> "kept", null, token);
            java.util.Random provider = forge.util.MyRandom.getRandom();
            long calls = forge.util.MyRandom.callCount();
            for (int repeat = 0; repeat < 3; repeat++) {
                assertEquals(extraBudget(action.getPayCosts(), action, actor(game), 2, false), mode.equals("payable"));
                assertEquals(board(game), before);
                assertSame(action.getHostCard(), host);
                assertEquals(action.getXManaCostPaid(), Integer.valueOf(3));
                assertEquals(action.getMaxWaterbend(), maximum);
                for (Card card : actor(game).getCardsIn(ZoneType.Battlefield)) {
                    for (SpellAbility mana : card.getManaAbilities()) assertNull(mana.getActivatingPlayer());
                }
                if (held != null) assertTrue(AiCardMemory.isRememberedCard(actor(game), held, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL));
                assertEquals(AiCache.getCached("budget-preview-sentinel", () -> "lost", null, token), cached);
                assertSame(forge.util.MyRandom.getRandom(), provider);
                assertEquals(forge.util.MyRandom.callCount(), calls);
            }
            assertEquals(provider.nextLong(), reference.nextLong());
            return null;
        }));
    }

    private void explicitBudgetCost(int depth, boolean payable) {
        Game original = setup(WAVE);
        for (int index = 0; index < (payable ? 3 : 2); index++) addCardToZone("Forest", actor(original), ZoneType.Battlefield);
        Game game = snapshot(original, depth);
        SpellAbility action = ability(game, WAVE);
        action.setXManaCostPaid(3);
        Cost expanded = action.getPayCosts().copy();
        expanded.add(new Cost(ManaCost.get(1), false));
        String beforeCost = expanded.toString();
        Map<Integer, String> before = board(game);
        assertEquals(extraBudget(expanded, action, actor(game), 2, false), payable,
                "Copy the explicitly supplied increased cost, not only the ability's original cost");
        assertEquals(expanded.toString(), beforeCost);
        assertEquals(board(game), before);
        assertEquals(action.getXManaCostPaid(), Integer.valueOf(3));
    }

    @Test
    public void actualKataraDepth0() {
        check(KATARA, 0, "actual");
    }

    @Test
    public void actualKataraDepth1() {
        check(KATARA, 1, "actual");
    }

    @Test
    public void actualKataraDepth2() {
        check(KATARA, 2, "actual");
    }

    @Test
    public void chosenPreviewKataraDepth0() {
        check(KATARA, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewKataraDepth1() {
        check(KATARA, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewKataraDepth2() {
        check(KATARA, 2, "chosenPreview");
    }

    @Test
    public void requestedKataraDepth0() {
        check(KATARA, 0, "requested");
    }

    @Test
    public void requestedKataraDepth1() {
        check(KATARA, 1, "requested");
    }

    @Test
    public void requestedKataraDepth2() {
        check(KATARA, 2, "requested");
    }

    @Test
    public void maximumKataraDepth0() {
        check(KATARA, 0, "maximum");
    }

    @Test
    public void maximumKataraDepth1() {
        check(KATARA, 1, "maximum");
    }

    @Test
    public void maximumKataraDepth2() {
        check(KATARA, 2, "maximum");
    }

    @Test
    public void insufficientPreviewKataraDepth0() {
        check(KATARA, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewKataraDepth1() {
        check(KATARA, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewKataraDepth2() {
        check(KATARA, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualKataraDepth0() {
        check(KATARA, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualKataraDepth1() {
        check(KATARA, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualKataraDepth2() {
        check(KATARA, 2, "insufficientActual");
    }

    @Test
    public void actualRestorationDepth0() {
        check(RESTORATION, 0, "actual");
    }

    @Test
    public void actualRestorationDepth1() {
        check(RESTORATION, 1, "actual");
    }

    @Test
    public void actualRestorationDepth2() {
        check(RESTORATION, 2, "actual");
    }

    @Test
    public void chosenPreviewRestorationDepth0() {
        check(RESTORATION, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewRestorationDepth1() {
        check(RESTORATION, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewRestorationDepth2() {
        check(RESTORATION, 2, "chosenPreview");
    }

    @Test
    public void requestedRestorationDepth0() {
        check(RESTORATION, 0, "requested");
    }

    @Test
    public void requestedRestorationDepth1() {
        check(RESTORATION, 1, "requested");
    }

    @Test
    public void requestedRestorationDepth2() {
        check(RESTORATION, 2, "requested");
    }

    @Test
    public void maximumRestorationDepth0() {
        check(RESTORATION, 0, "maximum");
    }

    @Test
    public void maximumRestorationDepth1() {
        check(RESTORATION, 1, "maximum");
    }

    @Test
    public void maximumRestorationDepth2() {
        check(RESTORATION, 2, "maximum");
    }

    @Test
    public void insufficientPreviewRestorationDepth0() {
        check(RESTORATION, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewRestorationDepth1() {
        check(RESTORATION, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewRestorationDepth2() {
        check(RESTORATION, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualRestorationDepth0() {
        check(RESTORATION, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualRestorationDepth1() {
        check(RESTORATION, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualRestorationDepth2() {
        check(RESTORATION, 2, "insufficientActual");
    }

    @Test
    public void actualVisionsDepth0() {
        check(VISIONS, 0, "actual");
    }

    @Test
    public void actualVisionsDepth1() {
        check(VISIONS, 1, "actual");
    }

    @Test
    public void actualVisionsDepth2() {
        check(VISIONS, 2, "actual");
    }

    @Test
    public void chosenPreviewVisionsDepth0() {
        check(VISIONS, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewVisionsDepth1() {
        check(VISIONS, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewVisionsDepth2() {
        check(VISIONS, 2, "chosenPreview");
    }

    @Test
    public void requestedVisionsDepth0() {
        check(VISIONS, 0, "requested");
    }

    @Test
    public void requestedVisionsDepth1() {
        check(VISIONS, 1, "requested");
    }

    @Test
    public void requestedVisionsDepth2() {
        check(VISIONS, 2, "requested");
    }

    @Test
    public void maximumVisionsDepth0() {
        check(VISIONS, 0, "maximum");
    }

    @Test
    public void maximumVisionsDepth1() {
        check(VISIONS, 1, "maximum");
    }

    @Test
    public void maximumVisionsDepth2() {
        check(VISIONS, 2, "maximum");
    }

    @Test
    public void insufficientPreviewVisionsDepth0() {
        check(VISIONS, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewVisionsDepth1() {
        check(VISIONS, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewVisionsDepth2() {
        check(VISIONS, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualVisionsDepth0() {
        check(VISIONS, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualVisionsDepth1() {
        check(VISIONS, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualVisionsDepth2() {
        check(VISIONS, 2, "insufficientActual");
    }

    @Test
    public void actualWaveDepth0() {
        check(WAVE, 0, "actual");
    }

    @Test
    public void actualWaveDepth1() {
        check(WAVE, 1, "actual");
    }

    @Test
    public void actualWaveDepth2() {
        check(WAVE, 2, "actual");
    }

    @Test
    public void chosenPreviewWaveDepth0() {
        check(WAVE, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewWaveDepth1() {
        check(WAVE, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewWaveDepth2() {
        check(WAVE, 2, "chosenPreview");
    }

    @Test
    public void requestedWaveDepth0() {
        check(WAVE, 0, "requested");
    }

    @Test
    public void requestedWaveDepth1() {
        check(WAVE, 1, "requested");
    }

    @Test
    public void requestedWaveDepth2() {
        check(WAVE, 2, "requested");
    }

    @Test
    public void maximumWaveDepth0() {
        check(WAVE, 0, "maximum");
    }

    @Test
    public void maximumWaveDepth1() {
        check(WAVE, 1, "maximum");
    }

    @Test
    public void maximumWaveDepth2() {
        check(WAVE, 2, "maximum");
    }

    @Test
    public void insufficientPreviewWaveDepth0() {
        check(WAVE, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewWaveDepth1() {
        check(WAVE, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewWaveDepth2() {
        check(WAVE, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualWaveDepth0() {
        check(WAVE, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualWaveDepth1() {
        check(WAVE, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualWaveDepth2() {
        check(WAVE, 2, "insufficientActual");
    }

    @Test
    public void zeroKataraDepth0() {
        zeroKatara(0);
    }

    @Test
    public void zeroKataraDepth1() {
        zeroKatara(1);
    }

    @Test
    public void zeroKataraDepth2() {
        zeroKatara(2);
    }

    @Test
    public void directZeroKataraDepth0() {
        directZeroKatara(0);
    }

    @Test
    public void directZeroKataraDepth1() {
        directZeroKatara(1);
    }

    @Test
    public void directZeroKataraDepth2() {
        directZeroKatara(2);
    }

    @Test
    public void legalZeroRestorationDepth0() {
        legalZeroSpell(RESTORATION, 0);
    }

    @Test
    public void legalZeroRestorationDepth1() {
        legalZeroSpell(RESTORATION, 1);
    }

    @Test
    public void legalZeroRestorationDepth2() {
        legalZeroSpell(RESTORATION, 2);
    }

    @Test
    public void legalZeroVisionsDepth0() {
        legalZeroSpell(VISIONS, 0);
    }

    @Test
    public void legalZeroVisionsDepth1() {
        legalZeroSpell(VISIONS, 1);
    }

    @Test
    public void legalZeroVisionsDepth2() {
        legalZeroSpell(VISIONS, 2);
    }

    @Test
    public void legalZeroWaveDepth0() {
        legalZeroSpell(WAVE, 0);
    }

    @Test
    public void legalZeroWaveDepth1() {
        legalZeroSpell(WAVE, 1);
    }

    @Test
    public void legalZeroWaveDepth2() {
        legalZeroSpell(WAVE, 2);
    }

    @Test
    public void requestedWithExistingXKataraDepth0() {
        check(KATARA, 0, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXKataraDepth1() {
        check(KATARA, 1, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXKataraDepth2() {
        check(KATARA, 2, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXRestorationDepth0() {
        check(RESTORATION, 0, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXRestorationDepth1() {
        check(RESTORATION, 1, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXRestorationDepth2() {
        check(RESTORATION, 2, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXVisionsDepth0() {
        check(VISIONS, 0, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXVisionsDepth1() {
        check(VISIONS, 1, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXVisionsDepth2() {
        check(VISIONS, 2, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXWaveDepth0() {
        check(WAVE, 0, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXWaveDepth1() {
        check(WAVE, 1, "requestedWithExistingX");
    }

    @Test
    public void requestedWithExistingXWaveDepth2() {
        check(WAVE, 2, "requestedWithExistingX");
    }

    @Test
    public void otherZeroHydraDepth0() {
        otherMinimum("Lair of the Hydra", 0, 0);
    }

    @Test
    public void otherZeroHydraDepth1() {
        otherMinimum("Lair of the Hydra", 1, 0);
    }

    @Test
    public void otherZeroHydraDepth2() {
        otherMinimum("Lair of the Hydra", 2, 0);
    }

    @Test
    public void otherOneHydraDepth0() {
        otherMinimum("Lair of the Hydra", 0, 1);
    }

    @Test
    public void otherOneHydraDepth1() {
        otherMinimum("Lair of the Hydra", 1, 1);
    }

    @Test
    public void otherOneHydraDepth2() {
        otherMinimum("Lair of the Hydra", 2, 1);
    }

    @Test
    public void otherZeroCorpseweftDepth0() {
        otherMinimum("Corpseweft", 0, 0);
    }

    @Test
    public void otherZeroCorpseweftDepth1() {
        otherMinimum("Corpseweft", 1, 0);
    }

    @Test
    public void otherZeroCorpseweftDepth2() {
        otherMinimum("Corpseweft", 2, 0);
    }

    @Test
    public void otherOneCorpseweftDepth0() {
        otherMinimum("Corpseweft", 0, 1);
    }

    @Test
    public void otherOneCorpseweftDepth1() {
        otherMinimum("Corpseweft", 1, 1);
    }

    @Test
    public void otherOneCorpseweftDepth2() {
        otherMinimum("Corpseweft", 2, 1);
    }

    @Test
    public void otherLegalZeroEndlessOneDepth0() {
        otherMinimum("Endless One", 0, 0);
    }

    @Test
    public void otherLegalZeroEndlessOneDepth1() {
        otherMinimum("Endless One", 1, 0);
    }

    @Test
    public void otherLegalZeroEndlessOneDepth2() {
        otherMinimum("Endless One", 2, 0);
    }

    @Test
    public void unannouncedKataraDepth0() {
        unannouncedMinimum("Katara, Water Tribe's Hope", 0, false);
    }

    @Test
    public void unannouncedKataraDepth1() {
        unannouncedMinimum("Katara, Water Tribe's Hope", 1, false);
    }

    @Test
    public void unannouncedKataraDepth2() {
        unannouncedMinimum("Katara, Water Tribe's Hope", 2, false);
    }

    @Test
    public void unannouncedHydraDepth0() {
        unannouncedMinimum("Lair of the Hydra", 0, false);
    }

    @Test
    public void unannouncedHydraDepth1() {
        unannouncedMinimum("Lair of the Hydra", 1, false);
    }

    @Test
    public void unannouncedHydraDepth2() {
        unannouncedMinimum("Lair of the Hydra", 2, false);
    }

    @Test
    public void unannouncedCorpseweftDepth0() {
        unannouncedMinimum("Corpseweft", 0, false);
    }

    @Test
    public void unannouncedCorpseweftDepth1() {
        unannouncedMinimum("Corpseweft", 1, false);
    }

    @Test
    public void unannouncedCorpseweftDepth2() {
        unannouncedMinimum("Corpseweft", 2, false);
    }

    @Test
    public void unannouncedAiKataraDepth0() {
        unannouncedMinimum(KATARA, 0, true);
    }

    @Test
    public void unannouncedAiKataraDepth1() {
        unannouncedMinimum(KATARA, 1, true);
    }

    @Test
    public void unannouncedAiKataraDepth2() {
        unannouncedMinimum(KATARA, 2, true);
    }

    @Test
    public void wardBudget0Depth0() {
        wardBudget(0, 0, false);
    }

    @Test
    public void wardBudget0Depth1() {
        wardBudget(1, 0, false);
    }

    @Test
    public void wardBudget0Depth2() {
        wardBudget(2, 0, false);
    }

    @Test
    public void wardBudget1Depth0() {
        wardBudget(0, 1, false);
    }

    @Test
    public void wardBudget1Depth1() {
        wardBudget(1, 1, false);
    }

    @Test
    public void wardBudget1Depth2() {
        wardBudget(2, 1, false);
    }

    @Test
    public void wardBudget2Depth0() {
        wardBudget(0, 2, false);
    }

    @Test
    public void wardBudget2Depth1() {
        wardBudget(1, 2, false);
    }

    @Test
    public void wardBudget2Depth2() {
        wardBudget(2, 2, false);
    }

    @Test
    public void paidWardDepth0() {
        wardBudget(0, 2, true);
    }

    @Test
    public void paidWardDepth1() {
        wardBudget(1, 2, true);
    }

    @Test
    public void paidWardDepth2() {
        wardBudget(2, 2, true);
    }

    @Test
    public void restrictedWardColorlessPreviewDepth0() {
        restrictedWard(0, false, false);
    }

    @Test
    public void restrictedWardColorlessPreviewDepth1() {
        restrictedWard(1, false, false);
    }

    @Test
    public void restrictedWardColorlessPreviewDepth2() {
        restrictedWard(2, false, false);
    }

    @Test
    public void restrictedWardColorlessNativeDepth0() {
        restrictedWard(0, false, true);
    }

    @Test
    public void restrictedWardColorlessNativeDepth1() {
        restrictedWard(1, false, true);
    }

    @Test
    public void restrictedWardColorlessNativeDepth2() {
        restrictedWard(2, false, true);
    }

    @Test
    public void restrictedWardBluePreviewDepth0() {
        restrictedWard(0, true, false);
    }

    @Test
    public void restrictedWardBluePreviewDepth1() {
        restrictedWard(1, true, false);
    }

    @Test
    public void restrictedWardBluePreviewDepth2() {
        restrictedWard(2, true, false);
    }

    @Test
    public void restrictedWardBlueNativeDepth0() {
        restrictedWard(0, true, true);
    }

    @Test
    public void restrictedWardBlueNativeDepth1() {
        restrictedWard(1, true, true);
    }

    @Test
    public void restrictedWardBlueNativeDepth2() {
        restrictedWard(2, true, true);
    }

    @Test
    public void budgetIsolationPayableDepth0() {
        budgetIsolation(0, "payable");
    }

    @Test
    public void budgetIsolationPayableDepth1() {
        budgetIsolation(1, "payable");
    }

    @Test
    public void budgetIsolationPayableDepth2() {
        budgetIsolation(2, "payable");
    }

    @Test
    public void budgetIsolationShortDepth0() {
        budgetIsolation(0, "short");
    }

    @Test
    public void budgetIsolationShortDepth1() {
        budgetIsolation(1, "short");
    }

    @Test
    public void budgetIsolationShortDepth2() {
        budgetIsolation(2, "short");
    }

    @Test
    public void budgetIsolationHeldDepth0() {
        budgetIsolation(0, "held");
    }

    @Test
    public void budgetIsolationHeldDepth1() {
        budgetIsolation(1, "held");
    }

    @Test
    public void budgetIsolationHeldDepth2() {
        budgetIsolation(2, "held");
    }

    @Test
    public void budgetIsolationLethalDepth0() {
        budgetIsolation(0, "lethal");
    }

    @Test
    public void budgetIsolationLethalDepth1() {
        budgetIsolation(1, "lethal");
    }

    @Test
    public void budgetIsolationLethalDepth2() {
        budgetIsolation(2, "lethal");
    }

    @Test
    public void explicitBudgetCostShortDepth0() {
        explicitBudgetCost(0, false);
    }

    @Test
    public void explicitBudgetCostShortDepth1() {
        explicitBudgetCost(1, false);
    }

    @Test
    public void explicitBudgetCostShortDepth2() {
        explicitBudgetCost(2, false);
    }

    @Test
    public void explicitBudgetCostPaidDepth0() {
        explicitBudgetCost(0, true);
    }

    @Test
    public void explicitBudgetCostPaidDepth1() {
        explicitBudgetCost(1, true);
    }

    @Test
    public void explicitBudgetCostPaidDepth2() {
        explicitBudgetCost(2, true);
    }
}
