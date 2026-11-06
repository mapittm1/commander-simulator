package forge.ai;

import forge.ai.ability.ControlExchangeVariantAi;
import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Pure exchange-group plans preserve ownership, decision perspective and world identity. */
public class ExchangeChoiceContractsTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("exchangeContract" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Exchange contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) addCardToZone("Forest", player, ZoneType.Library);
        return game;
    }

    private SpellAbility exchange(Player actor, Player first, Player second) {
        SpellAbility action = addCardToZone("Cultural Exchange", actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(first);
        action.getTargets().add(second);
        return action;
    }

    private CardCollection selected(Player actor, Player from, SpellAbility action, int min, int max) {
        return ControlExchangeVariantAi.chooseCards(actor, from.getCreaturesInPlay(), action, min, max);
    }

    private void unchanged(Game game, SpellAbility action) {
        assertTrue(game.getStack().isEmpty());
        assertEquals(action.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        for (Player player : game.getPlayers()) assertEquals(player.getSpellsCastThisTurn(), 0);
    }

    @Test public void ownershipDoesNotReplaceCurrentControlOrForceMaximumCount() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card weak = addCardToZone("Ornithopter", enemy, ZoneType.Battlefield);
        Card strong = addCardToZone("Darksteel Colossus", actor, ZoneType.Battlefield);
        Card best = addCardToZone("Serra Angel", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        weak.addTempController(actor, game.getNextTimestamp());
        best.addTempController(enemy, game.getNextTimestamp());
        game.getAction().checkStateEffects(true);
        SpellAbility action = exchange(actor, actor, enemy);
        assertEquals(selected(actor, actor, action, 0, 2), new CardCollection(weak));
        assertEquals(selected(actor, enemy, action, 1, 1), new CardCollection(best));
        action.resetTargets();
        action.getTargets().add(enemy);
        action.getTargets().add(actor);
        assertEquals(selected(actor, enemy, action, 0, 2), new CardCollection(best));
        assertEquals(selected(actor, actor, action, 1, 1), new CardCollection(weak));
        assertSame(weak.getOwner(), enemy);
        assertSame(weak.getController(), actor);
        assertSame(best.getOwner(), actor);
        assertSame(best.getController(), enemy);
        assertSame(strong.getController(), actor);
        unchanged(game, action);
    }

    @Test public void cardinalityBoundsAreHonoredOnBothGroups() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        addCardToZone("Memnite", actor, ZoneType.Battlefield);
        addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        addCardToZone("Shivan Dragon", enemy, ZoneType.Battlefield);
        SpellAbility action = exchange(actor, actor, enemy);
        assertEquals(selected(actor, actor, action, 0, 2).size(), 2);
        assertEquals(selected(actor, actor, action, 0, 1).size(), 1);
        assertEquals(selected(actor, enemy, action, 1, 1).size(), 1);
        assertTrue(selected(actor, enemy, action, 2, 1).isEmpty());
        unchanged(game, action);
    }

    @Test public void controlledActorUsesItsDecisionBeneficiary() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1), beneficiary = game.getPlayers().get(2);
        Card weak = addCardToZone("Ornithopter", beneficiary, ZoneType.Battlefield);
        addCardToZone("Darksteel Colossus", beneficiary, ZoneType.Battlefield);
        Card best = addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        SpellAbility action = exchange(actor, beneficiary, enemy);
        assertTrue(selected(actor, beneficiary, action, 0, 2).isEmpty());
        // Explicit controller context, not a claimed paid player-control spell.
        actor.addController(game.getNextTimestamp(), beneficiary);
        assertEquals(selected(actor, beneficiary, action, 0, 2), new CardCollection(weak));
        assertEquals(selected(actor, enemy, action, 1, 1), new CardCollection(best));
        assertSame(action.getActivatingPlayer(), actor);
        assertSame(weak.getController(), beneficiary);
        assertSame(best.getController(), enemy);
        unchanged(game, action);
    }

    @Test public void copiedPlansReturnOnlyCopiedCards() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card weak = addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        addCardToZone("Darksteel Colossus", actor, ZoneType.Battlefield);
        addCardToZone("Serra Angel", enemy, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        SpellAbility action = exchange(actor, actor, enemy);
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        Player copiedActor = copier.find(actor), copiedEnemy = copier.find(enemy);
        Card copiedSource = copier.find(action.getHostCard());
        SpellAbility copiedAction = copiedSource.getFirstSpellAbility();
        copiedAction.setActivatingPlayer(copiedActor);
        copiedAction.resetTargets();
        copiedAction.getTargets().add(copiedActor);
        copiedAction.getTargets().add(copiedEnemy);
        CardCollection group = selected(copiedActor, copiedActor, copiedAction, 0, 2);
        assertEquals(group.size(), 1);
        assertSame(group.get(0), copier.find(weak));
        assertNotSame(group.get(0), weak);
        assertSame(group.get(0).getGame(), copy);
        assertSame(weak.getController(), actor);
        unchanged(game, action);
    }

    @Test public void foreignTargetPlayersCannotSupplyAChoicePool() {
        Game game = pod(), foreign = pod();
        Player actor = game.getPlayers().get(0);
        addCardToZone("Ornithopter", actor, ZoneType.Battlefield);
        Player outsider = foreign.getPlayers().get(1);
        addCardToZone("Serra Angel", outsider, ZoneType.Battlefield);
        SpellAbility action = exchange(actor, actor, outsider);
        assertTrue(selected(actor, actor, action, 0, 1).isEmpty());
        unchanged(game, action);
    }
}
