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
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class TapManaOverlapProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Tap and mana overlap"));
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

    @Test
    public void groveCannotUseTheOnlyWhiteSourceForManaAndItsGroupTap() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card pilgrim = addCardToZone("Avacyn's Pilgrim", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        for (String name : List.of("Forest", "Forest", "Wastes", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        pilgrim.setSickness(false);
        SpellAbility ability = tapAbility(grove, actor);

        assertFalse(ComputerUtilCost.canPayCost(ability, actor, false));
        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertFalse(grove.isTapped());
        assertFalse(pilgrim.isTapped());
    }

    @Test
    public void grovePaysWithALandAndTapsTheCreatureManaSourceForTheGroup() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card pilgrim = addCardToZone("Avacyn's Pilgrim", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card plains = addCardToZone("Plains", actor, ZoneType.Battlefield);
        for (String name : List.of("Forest", "Forest", "Wastes", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        pilgrim.setSickness(false);
        SpellAbility ability = tapAbility(grove, actor);

        assertTrue(ComputerUtilCost.canPayCost(ability, actor, false));
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        assertTrue(plains.isTapped());
        assertTrue(pilgrim.isTapped());
        assertTrue(bear.isTapped());
        assertTrue(game.getZoneOf(grove).is(ZoneType.Graveyard));
    }

    @Test
    public void duneDivinerCannotUseOneDesertForBothManaAndTheTapCost() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card diviner = addCardToZone("Dune Diviner", actor, ZoneType.Battlefield);
        Card desert = addCardToZone("Desert", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = tapAbility(diviner, actor);

        assertFalse(ComputerUtilCost.canPayCost(ability, actor, false));
        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertFalse(desert.isTapped());
    }

    @Test
    public void duneDivinerCanSpendOneDesertAndTapTheOther() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card diviner = addCardToZone("Dune Diviner", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Desert", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Scavenger Grounds", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = tapAbility(diviner, actor);

        assertTrue(ComputerUtilCost.canPayCost(ability, actor, false));
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        assertTrue(first.isTapped());
        assertTrue(second.isTapped());
    }

    @Test
    public void groveReselectsBothCreaturesAfterThePilgrimPaysWhite() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card pilgrim = addCardToZone("Avacyn's Pilgrim", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        for (String name : List.of("Forest", "Forest", "Wastes", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        pilgrim.setSickness(false);
        SpellAbility ability = tapAbility(grove, actor);

        assertTrue(ComputerUtilCost.canPayCost(ability, actor, false));
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        assertTrue(pilgrim.isTapped());
        assertTrue(first.isTapped());
        assertTrue(second.isTapped());
        assertEquals(Set.copyOf(ability.getPaidList("TappedCards", true)), Set.of(first, second));
        assertTrue(game.getZoneOf(grove).is(ZoneType.Graveyard));
    }
}
