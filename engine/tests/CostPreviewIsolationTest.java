package forge.ai;

import forge.ai.simulation.SpellAbilityPicker;
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
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Candidate feasibility must not consume live randomness or alter legal candidates. */
public class CostPreviewIsolationTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            LobbyPlayerAi lobby = new LobbyPlayerAi("costPreview" + seat, null);
            lobby.setAiProfile("Default");
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(lobby));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Cost preview isolation"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) addCardToZone("Forest", player, ZoneType.Library);
        Player actor = game.getPlayers().get(0);
        for (int count = 0; count < 8; count++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        return game;
    }

    private List<SpellAbility> candidates(Player actor) {
        return new SpellAbilityPicker(actor).getCandidateSpellsAndAbilities();
    }

    private void unchanged(Game game, Card spell) {
        assertTrue(game.getStack().isEmpty());
        assertEquals(spell.getZone().getZoneType(), ZoneType.Hand);
        Player actor = game.getPlayers().get(0);
        assertEquals(actor.getSpellsCastThisTurn(), 0);
        assertTrue(actor.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isTapped));
    }

    @Test public void costPreviewPreservesCallerSeedStream() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        for (long seed : new long[] {0L, 4096L}) {
            MyRandom.withRandom(new Random(seed), () -> {
                List<SpellAbility> options = candidates(actor);
                assertEquals(options.size(), 1);
                assertSame(options.get(0).getHostCard(), spell);
                assertEquals(MyRandom.getRandom().nextLong(), new Random(seed).nextLong());
                return null;
            });
        }
        unchanged(game, spell);
    }

    @Test public void costPreviewDoesNotIncrementLiveUndoRandomCounter() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        Random provider = MyRandom.getRandom();
        long before = MyRandom.callCount();
        assertEquals(candidates(actor).size(), 1);
        assertSame(MyRandom.getRandom(), provider);
        assertEquals(MyRandom.callCount(), before);
        unchanged(game, spell);
    }

    @Test public void costPreviewRestoresEnclosingProvider() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        Random original = MyRandom.getRandom();
        Random enclosing = new Random(4096L);
        MyRandom.withRandom(enclosing, () -> {
            candidates(actor);
            assertSame(MyRandom.getRandom(), enclosing);
            return null;
        });
        assertSame(MyRandom.getRandom(), original);
        unchanged(game, spell);
    }

    @Test public void costPreviewRetainsUnaffordableSpellRejection() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Emrakul, the Promised End", actor, ZoneType.Hand);
        for (Card land : actor.getCardsIn(ZoneType.Battlefield)) land.setTapped(true);
        assertTrue(candidates(actor).isEmpty());
        assertTrue(game.getStack().isEmpty());
        assertEquals(spell.getZone().getZoneType(), ZoneType.Hand);
    }

    @Test public void costPreviewRetainsRequiredTargetRejection() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Giant Growth", actor, ZoneType.Hand);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        assertTrue(candidates(actor).isEmpty());
        unchanged(game, spell);
    }

    @Test public void secondMainClearsExpiredReservationEvenAtZeroChance() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        ((LobbyPlayerAi) actor.getLobbyPlayer()).setAiProfile("");
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        for (Card land : actor.getCardsIn(ZoneType.Battlefield)) {
            AiCardMemory.rememberCard(actor, land, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2);
        }
        assertEquals(candidates(actor).size(), 1);
        assertTrue(AiCardMemory.isMemorySetEmpty(actor, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2));
        unchanged(game, spell);
    }

    @Test public void firstMainRetainsFutureMainPhaseReservation() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (Card land : actor.getCardsIn(ZoneType.Battlefield)) {
            AiCardMemory.rememberCard(actor, land, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2);
        }
        assertTrue(candidates(actor).isEmpty());
        assertFalse(AiCardMemory.isMemorySetEmpty(actor, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_MAIN2));
        unchanged(game, spell);
    }

    @Test public void secondMainRetainsNextSpellReservation() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card spell = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        for (Card land : actor.getCardsIn(ZoneType.Battlefield)) {
            AiCardMemory.rememberCard(actor, land, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);
        }
        assertTrue(candidates(actor).isEmpty());
        assertFalse(AiCardMemory.isMemorySetEmpty(actor, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL));
        unchanged(game, spell);
    }

    @Test public void defaultProfileFullPlannerCanChooseAndPayForWhims() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        for (int seat = 1; seat < 4; seat++) {
            Player enemy = game.getPlayers().get(seat);
            for (String name : List.of("Craw Wurm", "Colossal Dreadmaw", "Mammoth Spider",
                    "Vastwood Gorger", "Gigantosaurus", "Ancient Brontodon",
                    "Terra Stomper", "Impervious Greatwurm")) {
                addCardToZone(name, enemy, ZoneType.Battlefield);
            }
        }
        Card source = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> chosen = MyRandom.withRandom(new Random(0L),
                () -> actor.getController().chooseSpellAbilityToPlay());
        assertNotNull(chosen);
        assertFalse(chosen.isEmpty());
        assertSame(chosen.get(0).getHostCard(), source);
        assertTrue(actor.getController().playChosenSpellAbility(chosen.get(0)));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
    }

    private Card hybridSource(Game game) {
        Player actor = game.getPlayers().get(0);
        for (int seat = 1; seat < 4; seat++) {
            Player enemy = game.getPlayers().get(seat);
            for (String name : List.of("Craw Wurm", "Colossal Dreadmaw", "Mammoth Spider",
                    "Vastwood Gorger", "Gigantosaurus", "Ancient Brontodon",
                    "Terra Stomper", "Impervious Greatwurm")) {
                addCardToZone(name, enemy, ZoneType.Battlefield);
            }
        }
        Card source = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        source.getFirstSpellAbility().setActivatingPlayer(actor);
        return source;
    }

    @Test public void hybridSafetyPreviewDoesNotAdvanceLiveRandomness() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = hybridSource(game);
        Random provider = MyRandom.getRandom();
        long calls = MyRandom.callCount();
        forge.ai.simulation.OnePlaySafetyChecker.isAcceptable(actor, source.getFirstSpellAbility());
        assertSame(MyRandom.getRandom(), provider);
        assertEquals(MyRandom.callCount(), calls);
        unchanged(game, source);
    }

    @Test public void defaultHybridWorkerChoiceDoesNotAdvanceLiveRandomness() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = hybridSource(game);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_HYBRID_SIMULATION);
        Random provider = MyRandom.getRandom();
        long calls = MyRandom.callCount();
        List<SpellAbility> chosen = MyRandom.withRandom(new Random(0L),
                () -> actor.getController().chooseSpellAbilityToPlay());
        assertSame(MyRandom.getRandom(), provider);
        assertEquals(MyRandom.callCount(), calls);
        assertNotNull(chosen);
        assertSame(chosen.get(0).getHostCard(), source);
        unchanged(game, source);
    }

    @Test public void pairedSafetyAcceptsBeneficialRandomOutcomeAverage() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        ((LobbyPlayerAi) actor.getLobbyPlayer()).setAiProfile("");
        Card source = hybridSource(game);
        boolean accepted = MyRandom.withRandom(new Random(0L), () ->
                forge.ai.simulation.OnePlaySafetyChecker.isAcceptable(actor, source.getFirstSpellAbility()));
        assertTrue(accepted);
        unchanged(game, source);
    }

    @Test public void pairedSafetyRejectsRiskToOnlyOwnPermanents() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        ((LobbyPlayerAi) actor.getLobbyPlayer()).setAiProfile("");
        Card source = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        source.getFirstSpellAbility().setActivatingPlayer(actor);
        boolean accepted = MyRandom.withRandom(new Random(0L), () ->
                forge.ai.simulation.OnePlaySafetyChecker.isAcceptable(actor, source.getFirstSpellAbility()));
        assertFalse(accepted);
        unchanged(game, source);
    }
}
