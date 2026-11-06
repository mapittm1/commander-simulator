package forge.ai;

import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Cast provenance must survive later control and target changes. */
public class SpellControlCastHistoryTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("castHistory" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Cast history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(1), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) {
            for (String land : List.of("Island", "Mountain", "Forest", "Plains"))
                addCardToZone(land, player, ZoneType.Battlefield);
            addCardToZone("Forest", player, ZoneType.Library);
        }
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
        fail("Cast-history fixture failed to settle");
    }
    private SpellAbility pending(Game game, String name) {
        Player enemy = game.getPlayers().get(1);
        SpellAbility action = addCardToZone(name, enemy, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(enemy);
        if (name.equals("Shock")) action.getTargets().add(game.getPlayers().get(0));
        assertTrue(ComputerUtil.playStack(action, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), name.equals("Shock") ? 1 : 9);
        return game.getStack().peekAbility();
    }
    private void steal(Game game, SpellAbility incoming, int seat, String name) {
        Player actor = game.getPlayers().get(seat);
        SpellAbility response = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(actor);
        response.getTargets().add(incoming);
        assertTrue(ComputerUtil.playStack(response, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), name.equals("Aethersnatch") ? 6 : 7);
        game.getStack().resolveStack();
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), actor);
    }
    @Test public void gainingControlDoesNotMoveCastCountsToThief() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player caster = game.getPlayers().get(1);
        SpellAbility incoming = pending(game, "Shock");
        steal(game, incoming, 0, "Aethersnatch");
        assertEquals(actor.getSpellsCastThisTurn(), 1);
        assertEquals(caster.getSpellsCastThisTurn(), 1);
        assertSame(incoming.getHostCard().getOwner(), caster);
        assertSame(incoming.getHostCard().getCastSA().getActivatingPlayer(), caster);
        settle(game);
    }
    @Test public void repeatedTheftKeepsOriginalCaster() {
        Game game = pod();
        Player caster = game.getPlayers().get(1);
        SpellAbility incoming = pending(game, "Shock");
        steal(game, incoming, 0, "Aethersnatch");
        incoming = game.getStack().peekAbility();
        steal(game, incoming, 3, "Commandeer");
        assertSame(incoming.getHostCard().getCastSA().getActivatingPlayer(), caster);
        for (int seat : List.of(0, 1, 3)) assertEquals(game.getPlayers().get(seat).getSpellsCastThisTurn(), 1);
        assertSame(incoming.getHostCard().getOwner(), caster);
        settle(game);
    }
    @Test public void castChoicesRemainDistinctFromRetargetedLiveSpell() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Player caster = game.getPlayers().get(1);
        SpellAbility incoming = pending(game, "Shock");
        steal(game, incoming, 0, "Aethersnatch");
        SpellAbility live = game.getStack().peekAbility();
        assertNotEquals(live.getTargets().getFirstTargetedPlayer(), actor);
        SpellAbility recorded = live.getHostCard().getCastSA();
        assertNotSame(recorded, live);
        assertSame(recorded.getActivatingPlayer(), caster);
        assertSame(recorded.getTargets().getFirstTargetedPlayer(), actor);
        assertEquals(recorded.getPayingMana().size(), 1);
        SpellAbility history = game.getStack().getSpellsCastThisTurn().stream()
                .filter(action -> action.getHostCard().getId() == live.getHostCard().getId()).findFirst().orElseThrow();
        assertSame(history.getActivatingPlayer(), caster);
        assertSame(history.getTargets().getFirstTargetedPlayer(), actor);
        settle(game);
    }
    private void zacama(boolean stolen) {
        Game game = pod();
        Player caster = game.getPlayers().get(1);
        Player actor = game.getPlayers().get(0);
        SpellAbility incoming = pending(game, "Zacama, Primal Calamity");
        int identity = incoming.getHostCard().getId();
        assertEquals((int) caster.getLandsInPlay().stream().filter(Card::isTapped).count(), 9);
        if (stolen) steal(game, incoming, 0, "Aethersnatch");
        settle(game);
        Card creature = game.getCardsInGame().stream().filter(card -> card.getId() == identity).findFirst().orElseThrow();
        assertSame(creature.getOwner(), caster);
        assertSame(creature.getController(), stolen ? actor : caster);
        assertSame(creature.getCastSA().getActivatingPlayer(), caster);
        assertEquals((int) actor.getLandsInPlay().stream().filter(Card::isTapped).count(), stolen ? 6 : 0,
                "A thief did not cast Zacama and must not receive its conditional land untap");
        assertEquals((int) caster.getLandsInPlay().stream().filter(Card::isTapped).count(), stolen ? 9 : 0);
    }
    @Test public void stolenZacamaDoesNotUntapThiefsLands() { zacama(true); }
    @Test public void ordinaryZacamaUntapsItsCastersLands() { zacama(false); }
}
