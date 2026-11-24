package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Compare proposed-X affordability to independently paid native waterbend actions. */
public class CommanderWaterbendXTest extends AITest {
    private static final String KATARA = "Katara, Water Tribe's Hope";
    private static final String RESTORATION = "Waterbender's Restoration";
    private static final String VISIONS = "Foggy Swamp Visions";
    private static final String WAVE = "Crashing Wave";

    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("water-x-0")).findFirst().orElseThrow();
    }

    private Game setup(String name) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("water-x-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Variable waterbend payments"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        if (name.equals(KATARA)) {
            addCardToZone(name, actor(game), ZoneType.Battlefield);
            for (int index = 0; index < 2; index++) addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
        } else {
            addCardToZone(name, actor(game), ZoneType.Hand);
            if (name.equals(RESTORATION)) {
                addCardToZone("Grizzly Bears", actor(game), ZoneType.Battlefield);
                addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
                addCardToZone("Memnite", actor(game), ZoneType.Battlefield);
            } else {
                for (int index = 0; index < 3; index++) addCardToZone("Ornithopter", actor(game), ZoneType.Battlefield);
            }
            if (name.equals(VISIONS)) {
                addCardToZone("Swamp", actor(game), ZoneType.Battlefield);
                addCardToZone("Swamp", actor(game), ZoneType.Battlefield);
                addCardToZone("Forest", actor(game), ZoneType.Battlefield);
                for (int index = 0; index < 2; index++) addCardToZone("Grizzly Bears", actor(game), ZoneType.Graveyard);
            } else {
                for (int index = 0; index < 2; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
            }
        }
        if (name.equals(WAVE)) {
            for (int index = 0; index < 2; index++) addCardToZone("Grizzly Bears", game.getPlayers().get(1), ZoneType.Battlefield);
        }
        assertEquals(eligible(game).size(), 3);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private List<Card> eligible(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isCreature() || c.isArtifact()).toList();
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private SpellAbility ability(Game game, String name) {
        Card card = actor(game).getCardsIn(name.equals(KATARA) ? ZoneType.Battlefield : ZoneType.Hand).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
        SpellAbility result = name.equals(KATARA)
                ? card.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && !a.isManaAbility()).findFirst().orElseThrow()
                : card.getFirstSpellAbility();
        result.setActivatingPlayer(actor(game));
        return result;
    }

    private Map<Integer, String> board(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) {
            result.put(card.getId(), card.getZone().getZoneType() + ":" + card.isTapped() + ":" + card.isUsedToPay());
        }
        for (Player player : game.getPlayers()) {
            result.put(-player.getId(), "life=" + player.getLife() + ":pool=" + player.getManaPool().totalMana());
        }
        return result;
    }

    private void target(Game game, SpellAbility action, String name) {
        if (name.equals(KATARA)) return;
        List<Card> targets = name.equals(RESTORATION) ? actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList()
                : name.equals(VISIONS) ? actor(game).getCardsIn(ZoneType.Graveyard).stream().filter(Card::isCreature).toList()
                : game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList();
        action.getTargets().add(targets.get(0));
        action.getTargets().add(targets.get(1));
    }

    private void check(String name, int depth, String mode) {
        Game original = setup(name);
        Map<Integer, String> untouched = board(original);
        Game game = snapshot(original, depth);
        SpellAbility action = ability(game, name);
        Map<Integer, String> before = board(game);
        if (mode.equals("requested") || mode.equals("maximum") || mode.equals("insufficientPreview")) {
            assertNull(action.getXManaCostPaid());
            if (mode.equals("maximum")) {
                assertEquals(ComputerUtilMana.determineLeftoverMana(action, actor(game), false), 3,
                        "Three independent eligible permanents pay X; normal printed mana remains payable separately");
            } else {
                boolean payable = ComputerUtilMana.canPayManaCost(action, actor(game), mode.equals("requested") ? 2 : 4, false);
                assertEquals(payable, mode.equals("requested"), "Forecast the proposed X, not an absent stored X");
            }
            assertNull(action.getXManaCostPaid(), "Forecasting must not retain a chosen X");
            assertEquals(board(game), before);
        } else if (mode.equals("chosenPreview")) {
            action.setXManaCostPaid(2);
            assertTrue(ComputerUtilCost.canPayCost(action, actor(game), false));
            assertEquals(action.getXManaCostPaid(), Integer.valueOf(2));
            assertEquals(board(game), before);
        } else if (mode.equals("insufficientActual")) {
            action.setXManaCostPaid(4);
            target(game, action, name);
            assertFalse(ComputerUtilCost.canPayCost(action, actor(game), false));
            assertFalse(ComputerUtil.playStack(action, actor(game), game));
            assertTrue(game.getStack().isEmpty());
            assertEquals(board(game), before, "Failed native payment restores reversible waterbend taps");
        } else {
            assertEquals(mode, "actual");
            action.setXManaCostPaid(2);
            target(game, action, name);
            assertTrue(action.canPlay());
            assertTrue(ComputerUtil.playStack(action, actor(game), game), "Pay chosen X with independent native costs");
            SpellAbility paid = game.getStack().peekAbility();
            assertEquals(paid.getXManaCostPaid(), Integer.valueOf(2));
            assertEquals(eligible(game).stream().filter(Card::isTapped).count(), 2L);
            assertEquals(paid.getPayingMana().size(), name.equals(KATARA) ? 0 : name.equals(VISIONS) ? 3 : 2);
            GameSimulator.resolveStack(game, actor(game));
            if (name.equals(KATARA)) {
                for (Card card : eligible(game)) {
                    assertEquals(card.getNetPower(), 2);
                    assertEquals(card.getNetToughness(), 2);
                }
            } else if (name.equals(RESTORATION)) {
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 2);
            } else if (name.equals(VISIONS)) {
                assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 2);
                assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 2L);
            } else {
                List<Card> opponents = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList();
                assertTrue(opponents.stream().allMatch(Card::isTapped));
                assertEquals(opponents.stream().mapToInt(c -> c.getCounters(CounterEnumType.STUN)).sum(), 3);
            }
        }
        if (depth > 0) assertEquals(board(original), untouched, "Copied-game payments must leave the original independent");
    }

    private void zeroKatara(int depth) {
        Game game = snapshot(setup(KATARA), depth);
        SpellAbility action = ability(game, KATARA);
        action.setXManaCostPaid(0);
        Map<Integer, String> before = board(game);
        assertFalse(ComputerUtil.playStack(action, actor(game), game), "Katara explicitly forbids choosing X=0");
        assertTrue(game.getStack().isEmpty());
        assertEquals(board(game), before);
    }

    @Test
    public void actualKataraDepth0() {
        check(KATARA, 0, "actual");
    }

    @Test
    public void actualKataraDepth1() {
        check(KATARA, 1, "actual");
    }

    @Test
    public void actualKataraDepth2() {
        check(KATARA, 2, "actual");
    }

    @Test
    public void chosenPreviewKataraDepth0() {
        check(KATARA, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewKataraDepth1() {
        check(KATARA, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewKataraDepth2() {
        check(KATARA, 2, "chosenPreview");
    }

    @Test
    public void requestedKataraDepth0() {
        check(KATARA, 0, "requested");
    }

    @Test
    public void requestedKataraDepth1() {
        check(KATARA, 1, "requested");
    }

    @Test
    public void requestedKataraDepth2() {
        check(KATARA, 2, "requested");
    }

    @Test
    public void maximumKataraDepth0() {
        check(KATARA, 0, "maximum");
    }

    @Test
    public void maximumKataraDepth1() {
        check(KATARA, 1, "maximum");
    }

    @Test
    public void maximumKataraDepth2() {
        check(KATARA, 2, "maximum");
    }

    @Test
    public void insufficientPreviewKataraDepth0() {
        check(KATARA, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewKataraDepth1() {
        check(KATARA, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewKataraDepth2() {
        check(KATARA, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualKataraDepth0() {
        check(KATARA, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualKataraDepth1() {
        check(KATARA, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualKataraDepth2() {
        check(KATARA, 2, "insufficientActual");
    }

    @Test
    public void actualRestorationDepth0() {
        check(RESTORATION, 0, "actual");
    }

    @Test
    public void actualRestorationDepth1() {
        check(RESTORATION, 1, "actual");
    }

    @Test
    public void actualRestorationDepth2() {
        check(RESTORATION, 2, "actual");
    }

    @Test
    public void chosenPreviewRestorationDepth0() {
        check(RESTORATION, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewRestorationDepth1() {
        check(RESTORATION, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewRestorationDepth2() {
        check(RESTORATION, 2, "chosenPreview");
    }

    @Test
    public void requestedRestorationDepth0() {
        check(RESTORATION, 0, "requested");
    }

    @Test
    public void requestedRestorationDepth1() {
        check(RESTORATION, 1, "requested");
    }

    @Test
    public void requestedRestorationDepth2() {
        check(RESTORATION, 2, "requested");
    }

    @Test
    public void maximumRestorationDepth0() {
        check(RESTORATION, 0, "maximum");
    }

    @Test
    public void maximumRestorationDepth1() {
        check(RESTORATION, 1, "maximum");
    }

    @Test
    public void maximumRestorationDepth2() {
        check(RESTORATION, 2, "maximum");
    }

    @Test
    public void insufficientPreviewRestorationDepth0() {
        check(RESTORATION, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewRestorationDepth1() {
        check(RESTORATION, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewRestorationDepth2() {
        check(RESTORATION, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualRestorationDepth0() {
        check(RESTORATION, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualRestorationDepth1() {
        check(RESTORATION, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualRestorationDepth2() {
        check(RESTORATION, 2, "insufficientActual");
    }

    @Test
    public void actualVisionsDepth0() {
        check(VISIONS, 0, "actual");
    }

    @Test
    public void actualVisionsDepth1() {
        check(VISIONS, 1, "actual");
    }

    @Test
    public void actualVisionsDepth2() {
        check(VISIONS, 2, "actual");
    }

    @Test
    public void chosenPreviewVisionsDepth0() {
        check(VISIONS, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewVisionsDepth1() {
        check(VISIONS, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewVisionsDepth2() {
        check(VISIONS, 2, "chosenPreview");
    }

    @Test
    public void requestedVisionsDepth0() {
        check(VISIONS, 0, "requested");
    }

    @Test
    public void requestedVisionsDepth1() {
        check(VISIONS, 1, "requested");
    }

    @Test
    public void requestedVisionsDepth2() {
        check(VISIONS, 2, "requested");
    }

    @Test
    public void maximumVisionsDepth0() {
        check(VISIONS, 0, "maximum");
    }

    @Test
    public void maximumVisionsDepth1() {
        check(VISIONS, 1, "maximum");
    }

    @Test
    public void maximumVisionsDepth2() {
        check(VISIONS, 2, "maximum");
    }

    @Test
    public void insufficientPreviewVisionsDepth0() {
        check(VISIONS, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewVisionsDepth1() {
        check(VISIONS, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewVisionsDepth2() {
        check(VISIONS, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualVisionsDepth0() {
        check(VISIONS, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualVisionsDepth1() {
        check(VISIONS, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualVisionsDepth2() {
        check(VISIONS, 2, "insufficientActual");
    }

    @Test
    public void actualWaveDepth0() {
        check(WAVE, 0, "actual");
    }

    @Test
    public void actualWaveDepth1() {
        check(WAVE, 1, "actual");
    }

    @Test
    public void actualWaveDepth2() {
        check(WAVE, 2, "actual");
    }

    @Test
    public void chosenPreviewWaveDepth0() {
        check(WAVE, 0, "chosenPreview");
    }

    @Test
    public void chosenPreviewWaveDepth1() {
        check(WAVE, 1, "chosenPreview");
    }

    @Test
    public void chosenPreviewWaveDepth2() {
        check(WAVE, 2, "chosenPreview");
    }

    @Test
    public void requestedWaveDepth0() {
        check(WAVE, 0, "requested");
    }

    @Test
    public void requestedWaveDepth1() {
        check(WAVE, 1, "requested");
    }

    @Test
    public void requestedWaveDepth2() {
        check(WAVE, 2, "requested");
    }

    @Test
    public void maximumWaveDepth0() {
        check(WAVE, 0, "maximum");
    }

    @Test
    public void maximumWaveDepth1() {
        check(WAVE, 1, "maximum");
    }

    @Test
    public void maximumWaveDepth2() {
        check(WAVE, 2, "maximum");
    }

    @Test
    public void insufficientPreviewWaveDepth0() {
        check(WAVE, 0, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewWaveDepth1() {
        check(WAVE, 1, "insufficientPreview");
    }

    @Test
    public void insufficientPreviewWaveDepth2() {
        check(WAVE, 2, "insufficientPreview");
    }

    @Test
    public void insufficientActualWaveDepth0() {
        check(WAVE, 0, "insufficientActual");
    }

    @Test
    public void insufficientActualWaveDepth1() {
        check(WAVE, 1, "insufficientActual");
    }

    @Test
    public void insufficientActualWaveDepth2() {
        check(WAVE, 2, "insufficientActual");
    }

    @Test
    public void zeroKataraDepth0() {
        zeroKatara(0);
    }

    @Test
    public void zeroKataraDepth1() {
        zeroKatara(1);
    }

    @Test
    public void zeroKataraDepth2() {
        zeroKatara(2);
    }
}
