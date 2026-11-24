package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid Airbending Lesson and native exile-play options distinguish permission from cost/timing. */
public class CommanderAirbendPermissionTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("airbend-" + index))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, int owner, String name, ZoneType zone) {
        return seat(game, owner).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, seat(game, 0));
    }

    private void fund(Game game, int owner) {
        for (int index = 0; index < 12; index++) {
            addCardToZone("Plains", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Mountain", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Island", seat(game, owner), ZoneType.Battlefield);
        }
    }

    private Game setup() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("airbend-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid Airbend permission"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        fund(game, 0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 0), 5);
        return game;
    }

    private void beginCast(Game game, int owner, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, seat(game, owner), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, owner));
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(game, owner)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private void cast(Game game, int owner, String name, Card target, int mana) {
        beginCast(game, owner, name, target, mana);
        settle(game);
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, seat(game, 0));
        return game;
    }

    private SpellAbility option(Game game, int owner, Card card) {
        SpellAbility base = card.getFirstSpellAbility();
        base.setActivatingPlayer(seat(game, owner));
        for (SpellAbility ability : GameActionUtil.getAlternativeCosts(base, seat(game, owner), false)) {
            ability.setActivatingPlayer(seat(game, owner));
            if (ability.getMayPlay() != null && ability.getPayCosts().getTotalMana().getCMC() == 2
                    && ability.canPlay() && ability.checkRestrictions(card, seat(game, owner))
                    && ComputerUtilCost.canPayCost(ability, seat(game, owner), false)) return ability;
        }
        return null;
    }

    private void recast(Game game, int owner) {
        Card exiled = named(game, owner, "Shivan Dragon", ZoneType.Exile);
        SpellAbility ability = option(game, owner, exiled);
        assertNotNull(ability, "Native alternative-cost enumeration must expose Airbend's owner permission");
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2, "Actually pay two generic mana");
        settle(game);
        Card dragon = named(game, owner, "Shivan Dragon", ZoneType.Battlefield);
        assertEquals(dragon.getNetPower(), 5);
        assertEquals(dragon.getNetToughness(), 5);
        assertSame(dragon.getOwner(), seat(game, owner));
        assertTrue(seat(game, owner).getCardsIn(ZoneType.Exile).stream().noneMatch(card -> card.getName().equals("Shivan Dragon")));
        assertTrue(seat(game, owner).getCardsIn(ZoneType.Command).stream().noneMatch(card -> card.getName().startsWith("Airbend ")),
                "Casting the remembered card consumes the one-object exile permission effect");
    }

    private void own(int depth, boolean pending, boolean timing) {
        Game original = setup();
        cast(original, 0, "Shivan Dragon", null, 6);
        beginCast(original, 0, "Airbending Lesson", named(original, 0, "Shivan Dragon", ZoneType.Battlefield), 3);
        if (!pending) settle(original);
        Game game = snapshot(original, depth);
        if (pending) settle(game);
        named(game, 0, "Shivan Dragon", ZoneType.Exile);
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), 23, "Resolve the printed Lesson draw exactly once");
        if (timing) {
            assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_BEGIN, () -> settle(game)));
            assertNull(option(game, 0, named(game, 0, "Shivan Dragon", ZoneType.Exile)), "Airbend does not grant flash");
            assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        }
        recast(game, 0);
        if (depth > 0) {
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), pending ? 24 : 23);
            named(original, 0, "Shivan Dragon", pending ? ZoneType.Battlefield : ZoneType.Exile);
            if (pending) assertFalse(original.getStack().isEmpty());
        }
    }

    private void ownerOnly(int depth) {
        Game original = setup();
        fund(original, 1);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(original, 1), 5);
        cast(original, 1, "Shivan Dragon", null, 6);
        for (int turn = 0; turn < 3; turn++) assertTrue(original.getPhaseHandler()
                .devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(original)));
        assertSame(original.getPhaseHandler().getPlayerTurn(), seat(original, 0));
        cast(original, 0, "Airbending Lesson", named(original, 1, "Shivan Dragon", ZoneType.Battlefield), 3);
        Game game = snapshot(original, depth);
        Card exiled = named(game, 1, "Shivan Dragon", ZoneType.Exile);
        assertNull(option(game, 0, exiled), "Airbender cannot cast the opponent-owned card");
        assertNull(option(game, 1, exiled), "Opponent's creature still needs its owner's main phase");
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        assertSame(game.getPhaseHandler().getPlayerTurn(), seat(game, 1));
        recast(game, 1);
        if (depth > 0) named(original, 1, "Shivan Dragon", ZoneType.Exile);
    }
    @Test public void ownDepth0() { own(0, false, false); }
    @Test public void ownDepth1() { own(1, false, false); }
    @Test public void ownDepth2() { own(2, false, false); }
    @Test public void pendingDepth0() { own(0, true, false); }
    @Test public void pendingDepth1() { own(1, true, false); }
    @Test public void pendingDepth2() { own(2, true, false); }
    @Test public void timingDepth0() { own(0, false, true); }
    @Test public void timingDepth1() { own(1, false, true); }
    @Test public void timingDepth2() { own(2, false, true); }
    @Test public void ownerOnlyDepth0() { ownerOnly(0); }
    @Test public void ownerOnlyDepth1() { ownerOnly(1); }
    @Test public void ownerOnlyDepth2() { ownerOnly(2); }
}
