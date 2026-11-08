package forge.ai;

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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class FilterTapManaOverlapProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Filter tap and mana overlap"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }


    private SpellAbility tapAbility(Card host, Player actor) {
        SpellAbility ability = host.getSpellAbilities().stream()
                .filter(candidate -> candidate.isActivatedAbility() && !candidate.isManaAbility())
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return ability;
    }

    private record Setup(Game game, Player actor, Card eladamri, Card pilgrim, Card bear, Card signet,
            SpellAbility ability) {
    }

    private Setup eladamri() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card eladamri = addCardToZone("Eladamri, Korvecdal", actor, ZoneType.Battlefield);
        Card pilgrim = addCardToZone("Avacyn's Pilgrim", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card signet = addCardToZone("Selesnya Signet", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        eladamri.setSickness(false);
        pilgrim.setSickness(false);
        return new Setup(game, actor, eladamri, pilgrim, bear, signet, tapAbility(eladamri, actor));
    }

    @Test
    public void signetCannotUseTheOnlyCreatureThatCouldFundItsActivation() {
        Setup setup = eladamri();
        assertFalse(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertFalse(setup.signet.isTapped());
        assertFalse(setup.pilgrim.isTapped());
    }

    @Test
    public void signetUsesAncientTombAndPreservesTheTwoTapCreatures() {
        Setup setup = eladamri();
        Card tomb = addCardToZone("Ancient Tomb", setup.actor, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(tomb.isTapped());
        assertEquals(setup.actor.getLife(), 38);
        assertTrue(setup.signet.isTapped());
        assertTrue(setup.pilgrim.isTapped());
        assertTrue(setup.bear.isTapped());
    }
}
