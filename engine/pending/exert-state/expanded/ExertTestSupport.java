package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Native paid exert mana, actual creature casting, and first/second owner untaps. */
public class ExertTestSupport extends AITest {
    protected Player actor(Game game) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("exert-0"))
                .findFirst().orElseThrow();
    }

    protected Card arena(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Arena of Glory")).findFirst().orElseThrow();
    }

    protected void settle(Game game) { GameSimulator.resolveStack(game, actor(game)); }

    protected Game paid() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("exert-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid exert snapshots"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 12; index++) addCardToZone("Mountain", player, ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        Card land = addCardToZone("Arena of Glory", actor(game), ZoneType.Battlefield);
        SpellAbility mana = land.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && sa.hasParam("AddsKeywords"))
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(actor(game));
        assertTrue(mana.canPlay());
        assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
        assertTrue(land.isTapped());
        assertEquals(land.getExertedThisTurn(), 1);
        assertTrue(land.isExertedBy(actor(game)));
        Card dragon = addCardToZone("Furnace Whelp", actor(game), ZoneType.Hand);
        SpellAbility spell = dragon.getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        assertTrue(spell.canPlay() && spell.checkRestrictions(dragon, actor(game)));
        assertTrue(ComputerUtil.playStack(spell, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        assertTrue(game.getStack().peekAbility().getPayingMana().stream()
                .anyMatch(value -> value.getSourceCard().getId() == land.getId()),
                "The actual exert mana must pay for the creature");
        settle(game);
        assertTrue(actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getName().equals("Furnace Whelp") && card.hasKeyword("Haste")));
        game.copyLastState();
        return game;
    }

    protected void checkState(Game game) {
        assertEquals(arena(game).getExertedThisTurn(), 1);
        assertTrue(arena(game).isExertedBy(actor(game)));
        assertFalse(arena(game).canUntap(actor(game), false));
    }

    protected void nextOwnerMain(Game game) {
        boolean reached = false;
        for (int turn = 0; turn < 4; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == actor(game)) { reached = true; break; }
        }
        assertTrue(reached);
    }

    protected void checkUntaps(Game game) {
        nextOwnerMain(game);
        assertTrue(arena(game).isTapped(), "The first actual owner untap skips the exerted permanent");
        assertFalse(arena(game).isExertedBy(actor(game)), "The exert restriction expires during that untap");
        nextOwnerMain(game);
        assertFalse(arena(game).isTapped(), "The second actual owner untap works normally");
    }

    protected void stateCopy(int depth) {
        Game original = paid();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        checkState(copy);
        checkState(original);
    }

    protected void pairedUntaps(int depth, boolean copyFirst) {
        Game original = paid();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        checkUntaps(first);
        assertTrue(arena(other).isTapped());
        assertTrue(arena(other).isExertedBy(actor(other)), "Advancing one world must not consume the other's exert restriction");
        checkUntaps(other);
    }
}
