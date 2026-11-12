package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
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

public class StarTrekStationTest extends AITest {
    @Test
    public void stationUsesTheLivePowerOfTheCreatureTappedAsCost() {
        verifyStation(false);
    }

    @Test
    public void stationUsesLastKnownPowerIfTheTappedCreatureLeaves() {
        verifyStation(true);
    }

    private void verifyStation(boolean removed) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Station power"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card ship = addCardToZone("Suurok-Class Science Vessel", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        assertFalse(ship.isCreature());
        SpellAbility station = ship.getNonManaAbilities().stream()
                .filter(ability -> ability.isKeyword(Keyword.STATION)).findFirst().orElseThrow();
        station.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, station, null));
        assertTrue(bear.isTapped());
        assertEquals(ship.getCounters(CounterEnumType.CHARGE), 0);

        respond(actor, "Giant Growth", bear);
        game.getStack().resolveStack();
        assertEquals(bear.getNetPower(), 5);
        if (removed) {
            respond(opponent, "Swords to Plowshares", bear);
            game.getStack().resolveStack();
            assertTrue(game.getCardState(bear).isInZone(ZoneType.Exile));
        }
        game.getStack().resolveStack();
        assertEquals(ship.getCounters(CounterEnumType.CHARGE), 5);
        assertTrue(ship.isCreature() && ship.hasKeyword(Keyword.FLYING));
    }

    private void respond(Player player, String name, Card target) {
        Card card = addCardToZone(name, player, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        assertTrue(ability.canTarget(target));
        ability.getTargets().add(target);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(player, ability, null));
    }
}
