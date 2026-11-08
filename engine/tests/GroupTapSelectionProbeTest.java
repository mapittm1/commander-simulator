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
public class GroupTapSelectionProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Group tap selection"));
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

    private record WeightSetup(Game game, Player actor, Card victim, Card first, Card second, SpellAbility ability) {
    }

    private WeightSetup weight(String firstName, String secondName) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card victim = addCardToZone("Hill Giant", game.getPlayers().get(1), ZoneType.Battlefield);
        Card weight = addCardToZone("Weight of Conscience", actor, ZoneType.Battlefield);
        weight.attachToEntity(victim, null);
        Card first = addCardToZone(firstName, actor, ZoneType.Battlefield);
        Card second = addCardToZone(secondName, actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        return new WeightSetup(game, actor, victim, first, second, tapAbility(weight, actor));
    }

    @Test
    public void weightChoosesMatchingCreaturesAndExilesEnchantedCreature() {
        WeightSetup setup = weight("Grizzly Bears", "Runeclaw Bear");
        Card decoy = addCardToZone("Llanowar Elves", setup.actor, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.first.isTapped());
        assertTrue(setup.second.isTapped());
        assertFalse(decoy.isTapped());
        setup.game.getStack().resolveStack();
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Exile));
    }

    @Test
    public void weightAcceptsAChangelingAsMatchingTheOtherCreature() {
        WeightSetup setup = weight("Grizzly Bears", "Universal Automaton");

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.first.isTapped());
        assertTrue(setup.second.isTapped());
    }

    @Test
    public void weightRejectsTwoCreaturesWithNoCommonType() {
        WeightSetup setup = weight("Grizzly Bears", "Llanowar Elves");

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertFalse(setup.first.isTapped());
        assertFalse(setup.second.isTapped());
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Battlefield));
    }

    private record TrollSetup(Game game, Player actor, Card troll, Card first, Card second, SpellAbility ability) {
    }

    private TrollSetup troll(String secondName) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card troll = addCardToZone("Mossbridge Troll", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Craw Wurm", actor, ZoneType.Battlefield);
        Card second = addCardToZone(secondName, actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        return new TrollSetup(game, actor, troll, first, second, tapAbility(troll, actor));
    }

    @Test
    public void mossbridgeTrollTapsEnoughCombinedPowerAndResolves() {
        TrollSetup setup = troll("Rumbling Baloth");

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.first.isTapped());
        assertTrue(setup.second.isTapped());
        assertFalse(setup.troll.isTapped());
        setup.game.getStack().resolveStack();
        assertEquals(setup.troll.getNetPower(), 25);
    }

    @Test
    public void mossbridgeTrollRejectsCombinedPowerBelowTen() {
        TrollSetup setup = troll("Centaur Courser");

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertFalse(setup.first.isTapped());
        assertFalse(setup.second.isTapped());
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void crewPaysCombinedPowerAndAnimatesTheVehicle() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card vehicle = addCardToZone("Renegade Freighter", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Llanowar Elves", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Elvish Mystic", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility crew = tapAbility(vehicle, actor);
        assertTrue(crew.isCrew());

        assertTrue(ComputerUtil.playStack(crew, actor, game));
        assertTrue(first.isTapped());
        assertTrue(second.isTapped());
        game.getStack().resolveStack();
        assertTrue(vehicle.isCreature());
    }

    @Test
    public void giantOxCrewsWithToughnessDespiteZeroPower() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card vehicle = addCardToZone("Renegade Freighter", actor, ZoneType.Battlefield);
        Card ox = addCardToZone("Giant Ox", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility crew = tapAbility(vehicle, actor);
        assertEquals(ox.getNetPower(), 0);

        assertTrue(ComputerUtil.playStack(crew, actor, game));
        assertTrue(ox.isTapped());
        game.getStack().resolveStack();
        assertTrue(vehicle.isCreature());
    }
}
