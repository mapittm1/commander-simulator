package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.testng.Assert.*;

/** A snapshot preserves participation without replaying player-loss actions. */
public class ParticipantGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            players.add(new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("seat" + i, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Participant fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
            addCardToZone("Forest", player, ZoneType.Library);
        }
        return game;
    }
    private void eliminate(Game game, Player player) {
        player.concede();
        game.getAction().checkStateEffects(true);
        assertTrue(player.hasLost());
        assertFalse(game.getPlayers().contains(player));
    }
    @DataProvider(name = "seats")
    public Object[][] seats() { return new Object[][] {{1, "concession"}, {2, "concession"}, {3, "concession"}, {1, "life"}, {2, "life"}, {3, "life"}, {1, "poison"}, {2, "poison"}, {3, "poison"}}; }

    @Test(dataProvider = "seats")
    public void eliminatedSeatStaysEliminated(int seat, String reason) {
        Game game = pod();
        Player lost = game.getPlayers().get(seat);
        if ("life".equals(reason)) lost.setLife(0, null);
        else if ("poison".equals(reason)) lost.setPoisonCounters(10, game.getPlayers().get(0));
        else lost.concede();
        game.getAction().checkStateEffects(true);
        assertTrue(lost.hasLost());
        assertEquals(game.getPlayers().size(), 3);
        Game copy = new GameCopier(game).makeCopy();
        assertEquals(copy.getPlayers().size(), 3);
        assertEquals(copy.getRegisteredPlayers().size(), 4);
        assertEquals(copy.getLostPlayers().size(), 1);
        Player mapped = copy.getPlayer(lost.getId());
        assertEquals(mapped.getLife(), lost.getLife());
        assertEquals(mapped.getCounters(), lost.getCounters());
        assertTrue(mapped.hasLost());
        assertEquals(mapped.getOutcome().lossState, lost.getOutcome().lossState);
        assertFalse(copy.getPlayers().contains(mapped));
        assertSame(copy.getLostPlayers().get(0), mapped);
        assertNotSame(mapped, lost);
        assertEquals(copy.getPlayersInTurnOrder().stream().map(Player::getId).toList(),
                game.getPlayersInTurnOrder().stream().map(Player::getId).toList());
        Card bolt = createCard("Lightning Bolt", copy.getPlayers().get(0));
        forge.game.spellability.SpellAbility ability = bolt.getFirstSpellAbility();
        ability.setActivatingPlayer(copy.getPlayers().get(0));
        assertFalse(mapped.canBeTargetedBy(ability));
    }

    @Test
    public void damageToEliminatedPlayerRetainsCanonicalHistoricalTarget() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player lost = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", owner);
        bear.getDamageHistory().registerDamage(3, true, bear, lost, new HashMap<>());
        eliminate(game, lost);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = copy.getCardsIn(ZoneType.Battlefield).get(0);
        assertEquals(copy.getPlayer(lost.getId()).getAssignedDamage(null, copied), 3);
        assertEquals(copy.getDamageDoneThisTurn(null, false, "Creature", "Player.Opponent",
                copied, copied.getController(), null).stream().mapToInt(Integer::intValue).sum(), 3);
        assertEquals(copy.getPlayers().size(), 3);
    }

    @Test
    public void secondEliminationInCopyDoesNotMutateOriginal() {
        Game game = pod();
        Player first = game.getPlayers().get(1);
        Player second = game.getPlayers().get(2);
        eliminate(game, first);
        Game copy = new GameCopier(game).makeCopy();
        eliminate(copy, copy.getPlayer(second.getId()));
        assertEquals(copy.getPlayers().size(), 2);
        assertEquals(copy.getLostPlayers().size(), 2);
        assertEquals(game.getPlayers().size(), 3);
        assertEquals(game.getLostPlayers().size(), 1);
        assertFalse(second.hasLost());
        Game again = new GameCopier(copy).makeCopy();
        assertEquals(again.getPlayers().size(), 2);
        assertEquals(again.getLostPlayers().get(0).getId(), first.getId());
        assertEquals(again.getLostPlayers().get(1).getId(), second.getId());
    }

    @Test
    public void rememberedEliminatedPlayerMapsIntoCopiedGame() {
        Game game = pod();
        Player lost = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", game.getPlayers().get(0));
        bear.addRemembered(lost);
        eliminate(game, lost);
        Game copy = new GameCopier(game).makeCopy();
        Card copied = copy.getCardsIn(ZoneType.Battlefield).get(0);
        assertEquals(com.google.common.collect.Iterables.size(copied.getRemembered()), 1);
        assertSame(copied.getRemembered().iterator().next(), copy.getPlayer(lost.getId()));
    }

    @Test
    public void eliminatedPlayersOwnedCardsAreNotReintroduced() {
        Game game = pod();
        Player lost = game.getPlayers().get(1);
        Card removed = addCard("Grizzly Bears", lost);
        Card survivor = addCard("Forest", game.getPlayers().get(0));
        eliminate(game, lost);
        assertFalse(game.getCardsInGame().contains(removed));
        Game copy = new GameCopier(game).makeCopy();
        assertFalse(copy.getCardsInGame().stream().anyMatch(card -> card.getId() == removed.getId()));
        assertTrue(copy.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card.getId() == survivor.getId()));
    }

    @Test
    public void resolvedPlayerLossTriggerIsNotReplayedByCopying() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card tyrant = addCard("Blood Tyrant", owner);
        game.getAction().checkStateEffects(true);
        eliminate(game, game.getPlayers().get(1));
        game.getTriggerHandler().runWaitingTriggers();
        forge.ai.simulation.GameSimulator.resolveStack(game, owner);
        assertEquals(tyrant.getCounters(forge.game.card.CounterEnumType.P1P1), 5);
        assertTrue(game.getStack().isEmpty());
        Game copy = new GameCopier(game).makeCopy();
        Card copied = copy.getCardsIn(ZoneType.Battlefield).get(0);
        copy.getTriggerHandler().runWaitingTriggers();
        assertTrue(copy.getStack().isEmpty());
        assertEquals(copied.getCounters(forge.game.card.CounterEnumType.P1P1), 5);
        assertEquals(tyrant.getCounters(forge.game.card.CounterEnumType.P1P1), 5);
    }
}
