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
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class CommanderDiscoverTest extends AITest {
    @Test
    public void appraiserPutsMurderInHandWhenItCannotBeCast() {
        verifyDiscover("Murder", DiscoverChoice.ATTEMPT_CAST, null);
    }

    @Test
    public void appraiserLetsControllerPutACastableCardInHand() {
        verifyDiscover("Sol Ring", DiscoverChoice.HAND, null);
    }

    @Test
    public void appraiserCastsSolRingWithoutPayingMana() {
        verifyDiscover("Sol Ring", DiscoverChoice.CAST, null);
    }

    @Test
    public void appraiserCastsMurderWhenAnOpponentHasALegalTarget() {
        verifyDiscover("Murder", DiscoverChoice.CAST, "Grizzly Bears");
    }

    @Test
    public void appraiserPutsVillageRitesInHandWhenTheSacrificeCannotBePaid() {
        verifyDiscover("Village Rites", DiscoverChoice.UNAVAILABLE, null);
    }

    private enum DiscoverChoice {
        HAND,
        ATTEMPT_CAST,
        UNAVAILABLE,
        CAST
    }

    private void verifyDiscover(String name, DiscoverChoice choice, String targetName) {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card target = targetName == null ? null : addCardToZone(targetName, opponent, ZoneType.Battlefield);
        Card skippedLand = addCardToZone("Plains", actor, ZoneType.Library);
        Card discovered = addCardToZone(name, actor, ZoneType.Library);
        Card untouchedLand = addCardToZone("Island", actor, ZoneType.Library);
        assertSame(actor.getCardsIn(ZoneType.Library).getFirst(), skippedLand);
        List<Boolean> attempts = new ArrayList<>();
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (params != null && discovered.equals(params.get("Card"))) {
                    assertEquals(game.getCardState(discovered).getZone().getZoneType(), ZoneType.Exile);
                    return choice != DiscoverChoice.HAND;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }

            @Override
            public SpellAbility getAbilityToPlay(Card card, List<SpellAbility> abilities, ITriggerEvent event) {
                if (discovered.equals(card)) {
                    assertEquals(abilities.size(), 1);
                    return abilities.get(0);
                }
                return super.getAbilityToPlay(card, abilities, event);
            }

            @Override
            public boolean playSaFromPlayEffect(SpellAbility ability) {
                boolean cast = super.playSaFromPlayEffect(ability);
                if (discovered.equals(ability.getHostCard())) {
                    attempts.add(cast);
                }
                return cast;
            }
        });

        Card appraiser = addCardToZone("Geological Appraiser", actor, ZoneType.Hand);
        SpellAbility spell = appraiser.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), appraiser.getId());
        if (choice == DiscoverChoice.ATTEMPT_CAST || choice == DiscoverChoice.UNAVAILABLE) {
            game.getAction().moveToGraveyard(game.getCardState(appraiser), null);
            game.getAction().checkStateEffects(true);
        }
        assertTrue(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).allMatch(Card::isTapped));

        game.getStack().resolveStack();
        List<Boolean> expectedAttempts = switch (choice) {
            case HAND, UNAVAILABLE -> List.of();
            case ATTEMPT_CAST -> List.of(false);
            case CAST -> List.of(true);
        };
        assertEquals(attempts, expectedAttempts);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 2);
        assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), untouchedLand.getId());
        assertEquals(actor.getCardsIn(ZoneType.Library).getLast().getId(), skippedLand.getId());
        if (choice == DiscoverChoice.CAST) {
            assertEquals(game.getStack().size(), 1);
            assertEquals(game.getStack().peekAbility().getHostCard().getId(), discovered.getId());
            game.getStack().resolveStack();
            if (target == null) {
                assertTrue(game.getCardState(discovered).isInPlay());
            } else {
                assertTrue(game.getCardState(target).isInZone(ZoneType.Graveyard));
                assertTrue(game.getCardState(discovered).isInZone(ZoneType.Graveyard));
            }
        } else {
            assertTrue(actor.getCardsIn(ZoneType.Hand).contains(discovered));
            assertEquals(game.getStack().size(), 0);
        }
        assertEquals(game.getStack().getSpellsCastThisTurn().size(), choice == DiscoverChoice.CAST ? 2 : 1);
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
