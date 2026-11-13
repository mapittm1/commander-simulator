package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderIncubateTest extends AITest {
    @Test
    public void glisteningDawnCreatesTwoIncubatorsAndOnlyPaidTokenTransformsInAiCopy() {
        verifyTransform(true);
    }

    @Test
    public void glisteningDawnCreatesTwoIncubatorsAndOnlyPaidTokenTransformsInSnapshot() {
        verifyTransform(false);
    }

    private void verifyTransform(boolean aiCopy) {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        for (int index = 0; index < 6; index++) {
            addCardToZone("Forest", actor, ZoneType.Battlefield);
        }
        Card dawn = addCardToZone("Glistening Dawn", actor, ZoneType.Hand);
        SpellAbility cast = dawn.getFirstSpellAbility();
        cast.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, cast, null));
        game.getStack().resolveStack();
        List<Card> tokens = actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Incubator")).toList();
        assertEquals(tokens.size(), 2);
        for (Card token : tokens) {
            assertEquals(token.getCounters(CounterEnumType.P1P1), 6);
            assertTrue(token.isArtifact());
            assertFalse(token.isCreature());
        }

        GameSnapshot snapshot = aiCopy ? null : new GameSnapshot(game);
        Game copy = aiCopy ? new GameCopier(game).withStack(true).makeCopy() : snapshot.makeCopy();
        Player copiedActor = copy.getPlayer(actor.getId());
        Card transformed = copy.getCardState(tokens.get(0));
        Card untouched = copy.getCardState(tokens.get(1));
        assertTrue(transformed.isTransformable());
        assertTrue(untouched.isTransformable());
        assertEquals(transformed.getCounters(CounterEnumType.P1P1), 6);
        assertEquals(untouched.getCounters(CounterEnumType.P1P1), 6);
        SpellAbility activate = transformed.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        activate.setActivatingPlayer(copiedActor);
        assertTrue(ComputerUtil.playStack(activate, copiedActor, copy));
        copy.getStack().resolveStack();

        assertTrue(transformed.isTransformed());
        assertTrue(transformed.isArtifact());
        assertTrue(transformed.isCreature());
        assertTrue(transformed.getType().hasSubtype("Phyrexian"));
        assertEquals(transformed.getNetPower(), 6);
        assertEquals(transformed.getNetToughness(), 6);
        assertFalse(untouched.isTransformed());
        transformed.clearCounters();
        assertEquals(transformed.getCounters(CounterEnumType.P1P1), 0);
        assertFalse(tokens.get(0).isTransformed());
        assertEquals(tokens.get(0).getCounters(CounterEnumType.P1P1), 6);
        assertEquals(untouched.getCounters(CounterEnumType.P1P1), 6);

        if (snapshot != null) {
            tokens.get(1).clearCounters();
            snapshot.restoreGameState(game);
            Card restored = game.getCardState(tokens.get(1));
            assertEquals(restored.getCounters(CounterEnumType.P1P1), 6);
            restored.clearCounters();
            assertEquals(untouched.getCounters(CounterEnumType.P1P1), 6);
        }
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Incubate"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        return game;
    }
}
