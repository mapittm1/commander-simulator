package forge.ai;

import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native composed spell-control costs, choices and outcomes. */
public class ComposedSpellControlTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("spellControl" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Spell control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(1), 1);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 8; i++) {
                addCardToZone("Island", player, ZoneType.Battlefield);
                addCardToZone("Mountain", player, ZoneType.Battlefield);
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        game.getPlayers().get(0).setLife(2, null);
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixed spell-control fixture did not settle");
    }


    private SpellAbility shock(Game game) {
        Player enemy = game.getPlayers().get(1);
        SpellAbility incoming = addCardToZone("Shock", enemy, ZoneType.Hand).getFirstSpellAbility();
        incoming.setActivatingPlayer(enemy);
        incoming.getTargets().add(game.getPlayers().get(0));
        assertTrue(ComputerUtil.playStack(incoming, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        return game.getStack().peekAbility();
    }
    private void polarity(long seed, boolean win) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = shock(game);
        SpellAbility response = addCardToZone("Invert Polarity", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        forge.util.MyRandom.withRandom(new Random(seed), () -> { game.getStack().resolveStack(); return null; });
        assertEquals(game.getStack().size(), win ? 1 : 0);
        if (win) {
            assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
            assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), actor);
        }
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }
    @Test public void polarityNativeWin() { polarity(0, true); }
    @Test public void polarityNativeLoss() { polarity(4096, false); }

    @Test public void suddenSubstitutionNativeExchange() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        Card offered = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        SpellAbility incoming = shock(game);
        SpellAbility response = addCardToZone("Sudden Substitution", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        response.getSubAbility().getTargets().add(offered);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        game.getStack().resolveStack();
        assertSame(offered.getController(), enemy);
        assertSame(offered.getOwner(), actor);
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
        assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), actor);
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }
    private void choosePolarity(AIOption mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        shock(game);
        addCardToZone("Invert Polarity", actor, ZoneType.Hand);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        assertEquals(choices.get(0).getHostCard().getName(), "Invert Polarity");
        assertEquals(choices.get(0).getApi(), ApiType.FlipCoin);
        assertTrue(ComputerUtil.playStack(choices.get(0), actor, game));
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }
    @Test public void polarityHeuristic() { choosePolarity(null); }
    @Test public void polarityHybrid() { choosePolarity(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void polarityFull() { choosePolarity(AIOption.USE_FULL_SIMULATION); }
    private void chooseChimera(AIOption mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(mode);
        Player enemy = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        SpellAbility chimera = addCardToZone("Perplexing Chimera", actor, ZoneType.Hand).getFirstSpellAbility();
        chimera.setActivatingPlayer(actor);
        int identity = chimera.getHostCard().getId();
        assertTrue(ComputerUtil.playStack(chimera, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, enemy, 1);
        shock(game);
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
        Card exchanged = game.getCardsInGame().stream().filter(card -> card.getId() == identity).findFirst().orElseThrow();
        assertSame(exchanged.getController(), enemy);
        assertSame(exchanged.getOwner(), actor);
    }

    @Test public void chimeraOptionalTriggerProtectsController() { chooseChimera(null); }
    @Test public void chimeraHybrid() { chooseChimera(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void chimeraFull() { chooseChimera(AIOption.USE_FULL_SIMULATION); }

    private void chooseSubstitution(AIOption mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        Card offered = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        SpellAbility incoming = shock(game);
        int incomingId = incoming.getHostCard().getId();
        addCardToZone("Sudden Substitution", actor, ZoneType.Hand);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(mode);
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        SpellAbility response = choices.get(0);
        assertEquals(response.getHostCard().getName(), "Sudden Substitution");
        assertEquals(com.google.common.collect.Iterables.getOnlyElement(response.getTargets().getTargetSpells()).getHostCard().getId(), incomingId);
        assertSame(response.getSubAbility().getTargets().getFirstTargetedCard(), offered);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        game.getStack().resolveStack();
        assertSame(offered.getController(), enemy);
        assertSame(offered.getOwner(), actor);
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
        assertNotEquals(game.getStack().peekAbility().getTargets().getFirstTargetedPlayer(), actor);
        settle(game);
        assertFalse(actor.hasLost());
        assertEquals(actor.getLife(), 2);
    }
    @Test public void substitutionHeuristic() { chooseSubstitution(null); }
    @Test public void substitutionHybrid() { chooseSubstitution(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void substitutionFull() { chooseSubstitution(AIOption.USE_FULL_SIMULATION); }

    @Test public void chimeraDeclinesLowValueExchange() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        actor.setLife(40, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        SpellAbility chimera = addCardToZone("Perplexing Chimera", actor, ZoneType.Hand).getFirstSpellAbility();
        chimera.setActivatingPlayer(actor);
        int identity = chimera.getHostCard().getId();
        assertTrue(ComputerUtil.playStack(chimera, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, enemy, 1);
        SpellAbility incoming = addCardToZone("Shock", enemy, ZoneType.Hand).getFirstSpellAbility();
        incoming.setActivatingPlayer(enemy);
        incoming.getTargets().add(game.getPlayers().get(3));
        assertTrue(ComputerUtil.playStack(incoming, enemy, game));
        settle(game);
        assertEquals(actor.getLife(), 40);
        assertEquals(game.getPlayers().get(3).getLife(), 38);
        Card retained = game.getCardsInGame().stream().filter(card -> card.getId() == identity).findFirst().orElseThrow();
        assertSame(retained.getController(), actor);
    }

    private Card prepareChimera(Game game) {
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        SpellAbility source = addCardToZone("Perplexing Chimera", actor, ZoneType.Hand).getFirstSpellAbility();
        source.setActivatingPlayer(actor);
        int identity = source.getHostCard().getId();
        assertTrue(ComputerUtil.playStack(source, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(1), 1);
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity).findFirst().orElseThrow();
    }

    private void queueTriggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    @Test public void departedExchangeSourceCannotStealSpell() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        actor.setLife(40, null);
        Card source = prepareChimera(game);
        SpellAbility incoming = shock(game);
        queueTriggers(game);
        manualOriginalExchangeChoice(game, incoming.getHostCard().getId());
        SpellAbility response = addCardToZone("Rapid Hybridization", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(source);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        game.getStack().resolveStack();
        assertTrue(source.isInZone(ZoneType.Graveyard));
        settle(game);
        assertSame(incoming.getActivatingPlayer(), enemy);
        assertEquals(actor.getLife(), 38);
        assertFalse(actor.hasLost());
    }

    @Test public void phasedExchangeSourceCannotStealSpell() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        actor.setLife(40, null);
        Card source = prepareChimera(game);
        SpellAbility incoming = shock(game);
        queueTriggers(game);
        manualOriginalExchangeChoice(game, incoming.getHostCard().getId());
        SpellAbility response = addCardToZone("March of Swirling Mist", actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.setXManaCostPaid(1);
        response.getTargets().add(source);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        game.getStack().resolveStack();
        assertTrue(source.isPhasedOut());
        settle(game);
        assertSame(source.getController(), actor);
        assertSame(incoming.getActivatingPlayer(), enemy);
        assertEquals(actor.getLife(), 38);
        assertFalse(actor.hasLost());
    }

    @Test public void counteredSpellCannotBeExchanged() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        Player countering = game.getPlayers().get(2);
        actor.setLife(40, null);
        Card source = prepareChimera(game);
        SpellAbility incoming = shock(game);
        queueTriggers(game);
        manualOriginalExchangeChoice(game, incoming.getHostCard().getId());
        SpellAbility response = addCardToZone("Counterspell", countering, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(countering);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, countering, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        assertSame(source.getController(), actor);
        assertSame(source.getOwner(), actor);
        assertSame(incoming.getActivatingPlayer(), enemy);
        assertEquals(actor.getLife(), 40);
        assertFalse(actor.hasLost());
    }

    /** Rules-only affirmative choice for the original spell, not an AI witness. */
    private void manualOriginalExchangeChoice(Game game, int originalSpellId) {
        Player actor = game.getPlayers().get(0);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getController().getLobbyPlayer()) {
            @Override public boolean confirmTrigger(forge.game.trigger.WrappedAbility wrapper) {
                SpellAbility action = wrapper.getWrappedAbility();
                if (action.getApi() != ApiType.ControlSpell) return super.confirmTrigger(wrapper);
                List<SpellAbility> spells = forge.game.ability.AbilityUtils.getDefinedSpellAbilities(
                        action.getHostCard(), "TriggeredSpellAbility", action);
                return !spells.isEmpty() && spells.get(0).getHostCard().getId() == originalSpellId;
            }
        });
    }
}
