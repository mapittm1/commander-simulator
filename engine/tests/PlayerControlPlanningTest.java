package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Actual choices and native future-control outcomes, separate from dispatch classification. */
public class PlayerControlPlanningTest extends AITest {
    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty() || game.isGameOver()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture did not settle");
    }

    private Game pod(boolean threat) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("future" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Future control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            for (int i = 0; i < 8; i++) {
                if (threat || seat == 0) {
                    addCardToZone("Swamp", player, ZoneType.Battlefield);
                    addCardToZone("Mountain", player, ZoneType.Battlefield);
                }
                addCardToZone(threat ? "Forest" : "Darksteel Colossus", player, ZoneType.Library);
            }
            if (threat || seat == 0) for (int i = 0; i < 8; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        if (threat) {
            game.getPlayers().get(0).setLife(2, null);
            addCardToZone("Shivan Dragon", game.getPlayers().get(1), ZoneType.Battlefield);
        }
        return game;
    }

    private Card prepare(Game game, String name) {
        Player actor = game.getPlayers().get(0);
        Card card = addCardToZone(name, actor, ZoneType.Hand);
        if (!name.equals("Worst Fears") && !name.equals("Secret of Bloodbending")) {
            SpellAbility cast = card.getFirstSpellAbility();
            cast.setActivatingPlayer(actor);
            assertTrue(ComputerUtil.playStack(cast, actor, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), name.equals("The Dominion Bracelet") ? 2 : 6);
            settle(game);
            int identity = card.getId();
            card = game.getCardsInGame().stream().filter(candidate -> candidate.getId() == identity).findFirst().orElseThrow();
        }
        if (name.equals("Sorin Markov")) {
            com.google.common.collect.Multiset<CounterType> counters = com.google.common.collect.HashMultiset.create();
            counters.add(CounterEnumType.LOYALTY, 8);
            card.setCounters(counters); // Fixed previously accumulated loyalty, not a claimed activation history.
        }
        if (name.equals("The Dominion Bracelet")) {
            Card creature = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
            creature.setTapped(true); // It cannot block the forecast threat before our next untap.
            SpellAbility equip = card.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.Attach).findFirst().orElseThrow();
            equip.setActivatingPlayer(actor);
            equip.getTargets().add(creature);
            assertTrue(ComputerUtil.playStack(equip, actor, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
            settle(game);
            assertEquals(creature.getNetPower(), 1);
            card = creature;
        }
        return card;
    }

    private void selection(String name, AIOption mode) { selection(name, mode, false); }

    private void selection(String name, AIOption mode, boolean normalSecret) {
        Game game = pod(true);
        Player actor = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        Card card = prepare(game, name);
        if (normalSecret) {
            int blue = 0;
            for (Card land : actor.getLandsInPlay()) land.setTapped(!land.getName().equals("Island") || blue++ >= 4);
        }
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(mode);
        int originalTapped = (int) actor.getLandsInPlay().stream().filter(Card::isTapped).count();
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertEquals(actor.getLife(), 2, "Planning cannot change original life");
        assertEquals((int) actor.getLandsInPlay().stream().filter(Card::isTapped).count(), originalTapped);
        System.out.println("PRIMARY_CONTROL card=" + name + " mode=" + mode + " chosen="
                + (choices == null || choices.isEmpty() ? "pass" : choices.get(0).getHostCard().getName()));
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        SpellAbility selected = choices.get(0);
        // Full search may retain legal equip prefixes in its completed plan.
        // Execute and pay those actual choices; never insert the desired activation.
        for (int prefix = 0; selected.getApi() != ApiType.ControlPlayer && prefix < 3; prefix++) {
            assertEquals(name, "The Dominion Bracelet");
            assertEquals(mode, AIOption.USE_FULL_SIMULATION);
            assertEquals(selected.getApi(), ApiType.Attach);
            assertTrue(ComputerUtil.playStack(selected, actor, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
            settle(game);
            choices = agent.chooseSpellAbilityToPlay();
            assertNotNull(choices);
            assertFalse(choices.isEmpty());
            selected = choices.get(0);
            System.out.println("CONTROL_PREFIX paidEquip=1 next=" + selected.getApi());
        }
        assertEquals(selected.getApi(), ApiType.ControlPlayer);
        assertEquals(selected.getHostCard().getId(), card.getId());
        assertEquals(com.google.common.collect.Iterables.getOnlyElement(selected.getTargets().getTargetPlayers()), target);
        assertTrue(ComputerUtil.playStack(selected, actor, game));
        int payment = game.getStack().peekAbility().getPayingMana().size();
        boolean waterbend = name.equals("Secret of Bloodbending") && payment == 14;
        if (name.equals("Secret of Bloodbending")) assertTrue(payment == 4 || payment == 14);
        else assertEquals(payment, name.equals("Worst Fears") ? 8 : name.equals("Sorin Markov") ? 0 : name.equals("The Dominion Bracelet") ? 14 : 4);
        if (normalSecret) assertEquals(payment, 4);
        settle(game);
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        if (name.equals("Secret of Bloodbending") && !waterbend) assertNull(target.getControllingPlayer());
        else assertSame(target.getControllingPlayer(), actor);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
        if (name.equals("Secret of Bloodbending") && !waterbend) assertNull(target.getControllingPlayer());
        int identity = card.getId();
        Card resolved = game.getCardsInGame().stream().filter(candidate -> candidate.getId() == identity).findFirst().orElseThrow();
        if (name.equals("Mindslaver")) assertTrue(resolved.isInZone(ZoneType.Graveyard));
        else if (name.equals("Sorin Markov")) {
            assertTrue(resolved.isInZone(ZoneType.Battlefield));
            assertEquals(resolved.getCounters(CounterEnumType.LOYALTY), 1);
        } else if (name.equals("The Dominion Bracelet")) {
            assertTrue(resolved.isInZone(ZoneType.Battlefield));
            assertEquals(resolved.getNetPower(), 0);
            assertTrue(game.getCardsIn(ZoneType.Exile).stream().anyMatch(candidate -> candidate.getName().equals(name)));
        } else assertTrue(resolved.isInZone(ZoneType.Exile));
    }

    private void unhelpful(String name) {
        Game game = pod(false);
        Player actor = game.getPlayers().get(0);
        Card card = prepare(game, name);
        SpellAbility action = card.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.ControlPlayer).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        assertTrue(ComputerUtilCost.canPayCost(action, actor, false));
        AiAbilityDecision decision = SpellApiToAi.Converter.get(action).canPlayWithSubs(actor, action);
        System.out.println("UNHELPFUL_CONTROL card=" + name + " decision=" + decision);
        assertNotEquals(decision.decision(), AiPlayDecision.CantPlayAi);
        assertFalse(decision.willingToPlay(), "Do not spend real resources for no forecast benefit");
    }

    @Test public void worstHeuristic() { selection("Worst Fears", null); }
    @Test public void worstHybrid() { selection("Worst Fears", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void worstFull() { selection("Worst Fears", AIOption.USE_FULL_SIMULATION); }
    @Test public void mindHeuristic() { selection("Mindslaver", null); }
    @Test public void mindHybrid() { selection("Mindslaver", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void mindFull() { selection("Mindslaver", AIOption.USE_FULL_SIMULATION); }
    @Test public void secretHeuristic() { selection("Secret of Bloodbending", null); }
    @Test public void secretHybrid() { selection("Secret of Bloodbending", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void secretFull() { selection("Secret of Bloodbending", AIOption.USE_FULL_SIMULATION); }
    @Test public void declineWorst() { unhelpful("Worst Fears"); }
    @Test public void declineMind() { unhelpful("Mindslaver"); }
    @Test public void declineSecret() { unhelpful("Secret of Bloodbending"); }
    @Test public void sorinHeuristic() { selection("Sorin Markov", null); }
    @Test public void sorinHybrid() { selection("Sorin Markov", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void sorinFull() { selection("Sorin Markov", AIOption.USE_FULL_SIMULATION); }
    @Test public void braceletHeuristic() { selection("The Dominion Bracelet", null); }
    @Test public void braceletHybrid() { selection("The Dominion Bracelet", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void braceletFull() { selection("The Dominion Bracelet", AIOption.USE_FULL_SIMULATION); }
    @Test public void declineSorin() { unhelpful("Sorin Markov"); }
    @Test public void declineBracelet() { unhelpful("The Dominion Bracelet"); }
    @Test public void normalSecretHeuristic() { selection("Secret of Bloodbending", null, true); }
    @Test public void normalSecretHybrid() { selection("Secret of Bloodbending", AIOption.USE_HYBRID_SIMULATION, true); }
    @Test public void normalSecretFull() { selection("Secret of Bloodbending", AIOption.USE_FULL_SIMULATION, true); }
}
