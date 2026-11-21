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
import static org.testng.Assert.assertTrue;

/** Native paid entry, supplied tax permanents, and actual additional mana payment. */
public class CommanderDiscoverTaxTest extends AITest {
    @Test
    public void thaliasUnpayableTaxPutsTheCardInHand() {
        verifyTax("Thalia, Guardian of Thraben", 0);
    }
    @Test
    public void thaliasTaxIsPaidForAFreeSpell() {
        verifyTax("Thalia, Guardian of Thraben", 1);
    }
    @Test
    public void trinispheresUnpayableMinimumPutsTheCardInHand() {
        verifyTax("Trinisphere", 0);
    }
    @Test
    public void trinispheresMinimumIsPaidForAFreeSpell() {
        verifyTax("Trinisphere", 3);
    }
    @Test
    public void dampingSpheresUnpayableSecondSpellTaxPutsTheCardInHand() {
        verifyTax("Damping Sphere", 0);
    }
    @Test
    public void dampingSpheresSecondSpellTaxIsPaidForAFreeSpell() {
        verifyTax("Damping Sphere", 1);
    }

    @Test
    public void thaliasTaxIsAppliedBeforeTrinispheresMinimum() {
        verifyTaxes(List.of("Thalia, Guardian of Thraben", "Trinisphere"), 3);
    }

    @Test
    public void countedSpellAndThaliaTaxesBothPrecedeTrinispheresMinimum() {
        verifyTaxes(List.of("Damping Sphere", "Thalia, Guardian of Thraben", "Trinisphere"), 3);
    }

    private void verifyTax(String taxName, int extraMana) {
        verifyTaxes(List.of(taxName), extraMana);
    }

    private void verifyTaxes(List<String> taxNames, int extraMana) {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        for (String taxName : taxNames) addCardToZone(taxName, opponent, ZoneType.Battlefield);
        for (int i = 0; i < extraMana; i++) addCardToZone("Mountain", actor, ZoneType.Battlefield);
        Card skipped = addCardToZone("Plains", actor, ZoneType.Library);
        Card found = addCardToZone("Sol Ring", actor, ZoneType.Library);
        Card untouched = addCardToZone("Island", actor, ZoneType.Library);
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
        Card host = addCardToZone("Geological Appraiser", actor, ZoneType.Hand);
        SpellAbility entry = host.getFirstSpellAbility();
        entry.setActivatingPlayer(actor);
        game.copyLastState();
        assertTrue(ComputerUtil.playStack(entry, actor, game));
        game.getStack().resolveStack();
        queue(game);
        assertEquals(game.getStack().size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain") && !card.isTapped()).count(), (long) extraMana);
        game.copyLastState();
        game.getStack().resolveStack();
        queue(game);
        if (extraMana == 0) {
            assertTrue(game.getStack().isEmpty());
            assertTrue(game.getCardState(found).isInZone(ZoneType.Hand));
            assertEquals(game.getStack().getSpellsCastThisTurn().size(), 1);
        } else {
            assertEquals(game.getStack().size(), 1);
            assertEquals(game.getStack().peekAbility().getHostCard().getId(), found.getId());
            assertEquals(game.getStack().getSpellsCastThisTurn().size(), 2);
            game.getStack().resolveStack();
            queue(game);
            assertTrue(game.getStack().isEmpty());
            assertTrue(game.getCardState(found).isInZone(ZoneType.Battlefield));
        }
        assertTrue(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).allMatch(Card::isTapped),
                "The cast tax must consume the additional native mana sources");
        assertTrue(game.getCardState(skipped).isInZone(ZoneType.Library));
        assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), untouched.getId());
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 2);
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
