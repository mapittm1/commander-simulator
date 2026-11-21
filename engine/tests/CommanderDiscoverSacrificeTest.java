package forge.ai;

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
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.ITriggerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Native paid discover entry and sacrifice costs with supplied blockers and library. */
public class CommanderDiscoverSacrificeTest extends AITest {
    @Test
    public void mandatorySacrificeIsPaidBeforeDrawing() {
        verify(List.of(), true, ZoneType.Graveyard);
    }
    @Test
    public void restInPeaceStillAllowsTheSacrificeCost() {
        verify(List.of("Rest in Peace"), true, ZoneType.Exile);
    }
    @Test
    public void yasharnPreservesTheCreatureAndPutsTheSpellInHand() {
        verify(List.of("Yasharn, Implacable Earth"), false, ZoneType.Battlefield);
    }
    @Test
    public void soullessJailerPreventsCastingTheNoncreatureFromExile() {
        verify(List.of("Soulless Jailer"), false, ZoneType.Battlefield);
    }
    @Test
    public void teferiPreventsCastingDuringDiscoverResolution() {
        verify(List.of("Teferi, Time Raveler"), false, ZoneType.Battlefield);
    }
    @Test
    public void cageAllowsTheInstantFromExileAndItsSacrificeCost() {
        verify(List.of("Grafdigger's Cage"), true, ZoneType.Graveyard);
    }
    @Test
    public void exileReplacementDoesNotBypassYasharnsCostProhibition() {
        verify(List.of("Rest in Peace", "Yasharn, Implacable Earth"), false, ZoneType.Battlefield);
    }
    @Test
    public void cageAndRestInPeaceStillAllowTheSacrificeCost() {
        verify(List.of("Grafdigger's Cage", "Rest in Peace"), true, ZoneType.Exile);
    }

    @Test
    public void yasharnAllowsSacrificingALandForTheDiscoveredSpell() {
        verifyLandSacrifice(false);
    }

    @Test
    public void yasharnAllowsLandSacrificeWithAnExileReplacement() {
        verifyLandSacrifice(true);
    }

    private void verifyLandSacrifice(boolean exile) {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card yasharn = addCardToZone("Yasharn, Implacable Earth", opponent, ZoneType.Battlefield);
        if (exile) addCardToZone("Rest in Peace", opponent, ZoneType.Battlefield);
        Card found = addCardToZone("Shard Volley", actor, ZoneType.Library);
        Card untouched = addCardToZone("Island", actor, ZoneType.Library);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (params != null && params.get("Card") instanceof Card candidate && candidate.getId() == found.getId()) return true;
                return super.confirmAction(ability, mode, message, options, card, params);
            }
            @Override
            public SpellAbility getAbilityToPlay(Card card, List<SpellAbility> abilities, ITriggerEvent event) {
                if (card.getId() == found.getId()) return abilities.get(0);
                return super.getAbilityToPlay(card, abilities, event);
            }
            @Override
            public boolean playSaFromPlayEffect(SpellAbility ability) {
                if (ability.getHostCard().getId() == found.getId()) {
                    ability.getTargets().clear();
                    ability.getTargets().add(opponent);
                    return ComputerUtil.playStack(ability, actor, game);
                }
                return super.playSaFromPlayEffect(ability);
            }
        });
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        assertTrue(game.getCardState(yasharn).isInZone(ZoneType.Battlefield));
        Card host = addCardToZone("Geological Appraiser", actor, ZoneType.Hand);
        SpellAbility entry = host.getFirstSpellAbility();
        entry.setActivatingPlayer(actor);
        game.copyLastState();
        assertTrue(ComputerUtil.playStack(entry, actor, game));
        game.getStack().resolveStack();
        queue(game);
        int life = opponent.getLife();
        game.copyLastState();
        game.getStack().resolveStack();
        queue(game);
        assertEquals(game.getStack().size(), 1);
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), found.getId());
        assertTrue(game.getCardState(host).isInZone(ZoneType.Battlefield));
        assertEquals(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).count(), 3L);
        ZoneType destination = exile ? ZoneType.Exile : ZoneType.Graveyard;
        assertEquals(actor.getCardsIn(destination).stream()
                .filter(card -> card.getName().equals("Mountain")).count(), 1L);
        game.getStack().resolveStack();
        queue(game);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(found).isInZone(destination));
        assertEquals(opponent.getLife(), life - 3);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), untouched.getId());
        assertEquals(game.getStack().getSpellsCastThisTurn().size(), 2);
    }

    private void verify(List<String> blockerNames, boolean cast, ZoneType sourceResult) {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        List<Card> blockers = new ArrayList<>();
        for (String name : blockerNames) {
            Card blocker = addCardToZone(name, opponent, ZoneType.Battlefield);
            if (name.equals("Teferi, Time Raveler")) blocker.setCounters(CounterEnumType.LOYALTY, 4);
            blockers.add(blocker);
        }
        Card skipped = addCardToZone("Plains", actor, ZoneType.Library);
        Card found = addCardToZone("Village Rites", actor, ZoneType.Library);
        Card firstDraw = addCardToZone("Island", actor, ZoneType.Library);
        Card secondDraw = addCardToZone("Forest", actor, ZoneType.Library);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (params != null && params.get("Card") instanceof Card candidate && candidate.getId() == found.getId()) {
                    assertTrue(game.getCardState(found).isInZone(ZoneType.Exile));
                    return true;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }
            @Override
            public SpellAbility getAbilityToPlay(Card card, List<SpellAbility> abilities, ITriggerEvent event) {
                if (card.getId() == found.getId()) {
                    assertEquals(abilities.size(), 1);
                    return abilities.get(0);
                }
                return super.getAbilityToPlay(card, abilities, event);
            }
        });
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        for (Card blocker : blockers) assertTrue(game.getCardState(blocker).isInZone(ZoneType.Battlefield));
        Card host = addCardToZone("Geological Appraiser", actor, ZoneType.Hand);
        SpellAbility entry = host.getFirstSpellAbility();
        entry.setActivatingPlayer(actor);
        game.copyLastState();
        assertTrue(ComputerUtil.playStack(entry, actor, game));
        game.getStack().resolveStack();
        queue(game);
        assertEquals(game.getStack().size(), 1);
        assertTrue(game.getCardState(host).isInZone(ZoneType.Battlefield));
        assertTrue(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).allMatch(Card::isTapped));
        game.copyLastState();
        game.getStack().resolveStack();
        queue(game);
        assertTrue(game.getCardState(host).isInZone(sourceResult), "The sacrifice cost must be paid or preserved correctly");
        if (cast) {
            assertEquals(game.getStack().size(), 1);
            assertEquals(game.getStack().peekAbility().getHostCard().getId(), found.getId());
            assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 0, "No cards are drawn before the paid spell resolves");
            game.getStack().resolveStack();
            queue(game);
            assertTrue(game.getStack().isEmpty());
            assertTrue(game.getCardState(found).isInZone(sourceResult));
            assertTrue(game.getCardState(firstDraw).isInZone(ZoneType.Hand));
            assertTrue(game.getCardState(secondDraw).isInZone(ZoneType.Hand));
            assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 2);
            assertEquals(actor.getCardsIn(ZoneType.Library).size(), 1);
            assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), skipped.getId());
        } else {
            assertTrue(game.getStack().isEmpty());
            assertTrue(game.getCardState(found).isInZone(ZoneType.Hand));
            assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
            assertEquals(actor.getCardsIn(ZoneType.Library).size(), 3);
            assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), firstDraw.getId());
        }
        assertEquals(game.getStack().getSpellsCastThisTurn().size(), cast ? 2 : 1);
    }

    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private Game newGameWithMountains() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Commander discover"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        return game;
    }
}
