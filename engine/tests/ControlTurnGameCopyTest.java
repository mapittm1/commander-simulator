package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Paid player-control lifecycles and independent controller-layer contracts. */
public class ControlTurnGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("control" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Turn control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 20; i++) addCardToZone("Forest", player, ZoneType.Library);
            for (int i = 0; i < 20; i++) addCardToZone("Swamp", player, ZoneType.Battlefield);
            for (int i = 0; i < 8; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture stack did not settle");
    }

    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)));
        settle(game);
    }

    private void advance(Game game, PhaseType phase) {
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(phase, () -> settle(game)));
        settle(game);
    }

    private void pay(Game game, SpellAbility ability, Player owner, Player target, int mana) {
        ability.setActivatingPlayer(owner);
        if (target != null) {
            assertTrue(ability.canTarget(target));
            assertTrue(target.canBeTargetedBy(ability));
            ability.getTargets().add(target);
        }
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ability.checkRestrictions(owner));
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native cost payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getStack().resolveStack();
        settle(game);
    }

    private Card cast(Game game, String name, Player owner, Player target, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, target, mana);
        return game.getCardsInGame().stream().filter(c -> c.getId() == card.getId())
                .findFirst().orElseThrow();
    }

    private void schedule(Game game, String kind, Player owner, Player target) {
        if (kind.equals("mindslaver")) {
            Card card = cast(game, "Mindslaver", owner, null, 6);
            SpellAbility ability = card.getSpellAbilities().stream()
                    .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
            pay(game, ability, owner, target, 4);
            assertEquals(card.getZone().getZoneType(), ZoneType.Graveyard);
        } else {
            cast(game, kind.equals("combat") ? "Secret of Bloodbending" : "Worst Fears",
                    owner, target, kind.equals("combat") ? 4 : 8);
        }
    }

    private void controlled(Game game, Player target, Player master) {
        assertSame(target.getControllingPlayer(), master);
        assertEquals(target.isControlled(), master != null && master != target);
        assertSame(target.getView().getMindSlaveMaster(), master == null ? null : master.getView());
        assertSame(target.getController().getPlayer(), target);
        assertSame(target.getController().getGame(), game);
        assertSame(target.getGame(), game);
    }

    private void control(String kind, boolean active, boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        schedule(game, kind, owner, target);
        controlled(game, target, null);
        if (kind.equals("combat")) {
            nextTurn(game);
            controlled(game, target, null);
            if (active) advance(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
        } else if (active) {
            nextTurn(game);
        }
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        Player mapped = copied.getPlayers().get(1);
        controlled(copied, mapped, active ? copied.getPlayers().get(0) : null);
        if (!active) {
            if (kind.equals("combat")) {
                advance(game, PhaseType.COMBAT_DECLARE_ATTACKERS);
                advance(copied, PhaseType.COMBAT_DECLARE_ATTACKERS);
            } else {
                nextTurn(game);
                nextTurn(copied);
            }
        }
        controlled(game, target, owner);
        controlled(copied, mapped, copied.getPlayers().get(0));
        assertNotSame(mapped.getController(), target.getController());
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(),
                copier.getUnmappedSnapshotCommands().toString());
        if (kind.equals("combat")) advance(copied, PhaseType.MAIN2);
        else nextTurn(copied);
        controlled(copied, mapped, null);
        controlled(game, target, owner);
        if (kind.equals("combat")) advance(game, PhaseType.MAIN2);
        else nextTurn(game);
        controlled(game, target, null);
    }

    private void overlap(boolean active, boolean withStack) {
        Game game = pod();
        Player first = game.getPlayers().get(0);
        Player last = game.getPlayers().get(2);
        Player target = game.getPlayers().get(3);
        schedule(game, "worst", first, target);
        nextTurn(game);
        nextTurn(game);
        schedule(game, "worst", last, target);
        if (active) nextTurn(game);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        Player mapped = copied.getPlayers().get(3);
        controlled(copied, mapped, active ? copied.getPlayers().get(2) : null);
        if (!active) {
            nextTurn(game);
            nextTurn(copied);
        }
        controlled(game, target, last);
        controlled(copied, mapped, copied.getPlayers().get(2));
        nextTurn(copied);
        controlled(copied, mapped, null);
        controlled(game, target, last);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }

    private void expired(boolean withStack) {
        Game game = pod();
        Player target = game.getPlayers().get(1);
        schedule(game, "worst", game.getPlayers().get(0), target);
        nextTurn(game);
        controlled(game, target, game.getPlayers().get(0));
        nextTurn(game);
        controlled(game, target, null);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        controlled(copied, copied.getPlayers().get(1), null);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }

    private void departed(boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        schedule(game, "worst", owner, target);
        owner.concede();
        settle(game);
        assertFalse(owner.isInGame(), "Native concession removes the controlling player");
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        nextTurn(game);
        nextTurn(copied);
        controlled(game, target, null);
        controlled(copied, copied.getPlayer(target.getId()), null);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }

    private void factoryHistory(boolean withStack) {
        Game game = pod();
        Player master = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        Player decisionMaker = game.getPlayers().get(2);
        long first = game.getNextTimestamp();
        master.addController(first, decisionMaker);
        target.addController(game.getNextTimestamp(), master);
        master.removeController(first);
        assertSame(target.getController().getLobbyPlayer(), decisionMaker.getOriginalLobbyPlayer());
        Game copied = new GameCopier(game).withStack(withStack).makeCopy();
        Player mapped = copied.getPlayers().get(1);
        controlled(copied, mapped, copied.getPlayers().get(0));
        assertSame(mapped.getController().getLobbyPlayer(),
                copied.getPlayers().get(2).getOriginalLobbyPlayer());
        assertNotSame(mapped.getController(), target.getController());
    }

    private void layerRemoval(boolean withStack) {
        Game game = pod();
        Player target = game.getPlayers().get(1);
        long first = game.getNextTimestamp();
        long last = game.getNextTimestamp();
        target.addController(first, game.getPlayers().get(0));
        target.addController(last, game.getPlayers().get(2));
        Game copied = new GameCopier(game).withStack(withStack).makeCopy();
        Player mapped = copied.getPlayers().get(1);
        controlled(copied, mapped, copied.getPlayers().get(2));
        mapped.removeController(last);
        controlled(copied, mapped, copied.getPlayers().get(0));
        controlled(game, target, game.getPlayers().get(2));
        mapped.removeController(first);
        controlled(copied, mapped, null);
        controlled(game, target, game.getPlayers().get(2));
    }

    private void selfControl(boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        schedule(game, "worst", owner, owner);
        for (int i = 0; i < 4; i++) nextTurn(game);
        controlled(game, owner, owner);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        controlled(copied, copied.getPlayers().get(0), copied.getPlayers().get(0));
        nextTurn(copied);
        controlled(copied, copied.getPlayers().get(0), null);
        controlled(game, owner, owner);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }

    private void emrakul(boolean active, boolean withStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        cast(game, "Emrakul, the Promised End", owner, null, 13);
        controlled(game, target, null);
        if (active) nextTurn(game);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy();
        Player mapped = copied.getPlayers().get(1);
        controlled(copied, mapped, active ? copied.getPlayers().get(0) : null);
        if (!active) {
            nextTurn(game);
            nextTurn(copied);
        }
        controlled(game, target, owner);
        controlled(copied, mapped, copied.getPlayers().get(0));
        nextTurn(copied);
        controlled(copied, mapped, null);
        assertSame(copied.getPhaseHandler().getPlayerTurn(), mapped);
        assertTrue(mapped.isExtraTurn(), "The following turn is the promised uncontrolled extra turn");
        controlled(game, target, owner);
        nextTurn(game);
        controlled(game, target, null);
        assertSame(game.getPhaseHandler().getPlayerTurn(), target);
        assertTrue(target.isExtraTurn());
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(),
                copier.getUnmappedSnapshotCommands().toString());
    }

    @Test public void worstPendingNoStack() { control("worst", false, false); }
    @Test public void worstPendingStack() { control("worst", false, true); }
    @Test public void worstActiveNoStack() { control("worst", true, false); }
    @Test public void worstActiveStack() { control("worst", true, true); }
    @Test public void mindslaverPendingNoStack() { control("mindslaver", false, false); }
    @Test public void mindslaverPendingStack() { control("mindslaver", false, true); }
    @Test public void mindslaverActiveNoStack() { control("mindslaver", true, false); }
    @Test public void mindslaverActiveStack() { control("mindslaver", true, true); }
    @Test public void combatPendingNoStack() { control("combat", false, false); }
    @Test public void combatPendingStack() { control("combat", false, true); }
    @Test public void combatActiveNoStack() { control("combat", true, false); }
    @Test public void combatActiveStack() { control("combat", true, true); }
    @Test public void overlapPendingNoStack() { overlap(false, false); }
    @Test public void overlapPendingStack() { overlap(false, true); }
    @Test public void overlapActiveNoStack() { overlap(true, false); }
    @Test public void overlapActiveStack() { overlap(true, true); }
    @Test public void expiredNoStack() { expired(false); }
    @Test public void expiredStack() { expired(true); }
    @Test public void departedNoStack() { departed(false); }
    @Test public void departedStack() { departed(true); }
    @Test public void factoryHistoryNoStack() { factoryHistory(false); }
    @Test public void factoryHistoryStack() { factoryHistory(true); }
    @Test public void layerRemovalNoStack() { layerRemoval(false); }
    @Test public void layerRemovalStack() { layerRemoval(true); }
    @Test public void selfControlNoStack() { selfControl(false); }
    @Test public void selfControlStack() { selfControl(true); }
    @Test public void emrakulPendingNoStack() { emrakul(false, false); }
    @Test public void emrakulPendingStack() { emrakul(false, true); }
    @Test public void emrakulActiveNoStack() { emrakul(true, false); }
    @Test public void emrakulActiveStack() { emrakul(true, true); }
}
