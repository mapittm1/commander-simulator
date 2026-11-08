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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class NestedTapManaOverlapProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Nested tap and mana overlap"));
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

    private record Setup(Game game, Player actor, Card grove, Card manaSource, List<Card> creatures,
            SpellAbility ability) {
    }

    private Setup grove(String manaName, int creatureCount) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card manaSource = addCardToZone(manaName, actor, ZoneType.Battlefield);
        List<Card> creatures = new ArrayList<>();
        for (String name : List.of("Grizzly Bears", "Runeclaw Bear", "Ashcoat Bear").subList(0, creatureCount)) {
            creatures.add(addCardToZone(name, actor, ZoneType.Battlefield));
        }
        for (String name : List.of("Forest", "Forest", "Wastes", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        return new Setup(game, actor, grove, manaSource, creatures, tapAbility(grove, actor));
    }

    @Test
    public void drumCannotUseOneOfTheTwoCreaturesNeededForTheGrove() {
        Setup setup = grove("Springleaf Drum", 2);
        assertFalse(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertFalse(setup.manaSource.isTapped());
        assertFalse(setup.grove.isTapped());
    }

    @Test
    public void drumCanTapAThirdCreatureWithoutConsumingTheGroveGroup() {
        Setup setup = grove("Springleaf Drum", 3);
        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.manaSource.isTapped());
        assertTrue(setup.creatures.stream().allMatch(Card::isTapped));
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Graveyard));
    }

    @Test
    public void altarCannotSacrificeOneOfTheTwoCreaturesNeededForTheGrove() {
        Setup setup = grove("Phyrexian Altar", 2);
        assertFalse(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.creatures.stream().allMatch(Card::isInPlay));
        assertFalse(setup.grove.isTapped());
    }

    @Test
    public void altarCanSacrificeAThirdCreatureAndLeaveTwoToTap() {
        Setup setup = grove("Phyrexian Altar", 3);
        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.creatures.stream().filter(Card::isInPlay).count() == 2);
        assertTrue(setup.creatures.stream().filter(Card::isInPlay).allMatch(Card::isTapped));
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Graveyard));
    }

    @Test
    public void drumCannotTapTheSourceAlreadyNeededForItsOwnTapCost() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card eladamri = addCardToZone("Eladamri, Korvecdal", actor, ZoneType.Battlefield);
        Card drum = addCardToZone("Springleaf Drum", actor, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        eladamri.setSickness(false);
        SpellAbility ability = tapAbility(eladamri, actor);

        assertFalse(ComputerUtilCost.canPayCost(ability, actor, false));
        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertFalse(eladamri.isTapped());
        assertFalse(drum.isTapped());
    }
}
