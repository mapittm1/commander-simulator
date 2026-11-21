package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.keyword.Keyword;
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native paid discover entry; supplied library order, spell form and response targets. */
public class CommanderDiscoverCastFormsTest extends AITest {
    @Test
    public void drannithPutsTheUncastCardInHand() {
        verify("Sol Ring", "Drannith Magistrate", 1, ZoneType.Hand, null);
    }

    @Test
    public void cageAllowsACreatureCastFromExile() {
        verify("Grizzly Bears", "Grafdigger's Cage", 2, ZoneType.Battlefield, null);
    }

    @Test
    public void valkiDoesNotOfferTheHigherManaValueBackFace() {
        verify("Valki, God of Lies", null, 2, ZoneType.Battlefield, null);
    }

    @Test
    public void brambleDoesNotOfferTheHigherManaValueAdventure() {
        verify("Bramble Familiar", null, 2, ZoneType.Battlefield, null);
    }

    @Test
    public void aDiscoveredSplitCardCastsOnlyTheChosenHalf() {
        verify("Wear // Tear", "Grafdigger's Cage", 2, ZoneType.Graveyard, ZoneType.Graveyard);
    }

    @Test
    public void aLegalAdventureUsesItsOwnManaValueAndExilesOnResolution() {
        verify("Brazen Borrower", "Sol Ring", 2, ZoneType.Exile, ZoneType.Hand);
    }

    @Test
    public void aSplitCardWithTooHighACombinedValueIsSkipped() {
        Pod pod = pod();
        Card skipped = addCardToZone("Discovery // Dispersal", pod.actor, ZoneType.Library);
        Card found = addCardToZone("Sol Ring", pod.actor, ZoneType.Library);
        Card untouched = addCardToZone("Island", pod.actor, ZoneType.Library);
        List<Integer> offered = new ArrayList<>();
        control(pod, found, 1, offered);
        enter(pod);
        resolveDiscover(pod);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getId(), found.getId());
        settle(pod.game);
        assertTrue(pod.game.getCardState(found).isInZone(ZoneType.Battlefield));
        assertTrue(pod.game.getCardState(skipped).isInZone(ZoneType.Library));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).getFirst().getId(), untouched.getId());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 2);
    }

    @Test
    public void anAftermathHalfIsNotOfferedFromExile() {
        Pod pod = pod();
        Player opponent = pod.game.getPlayers().get(1);
        addCardToZone("Vedalken Orrery", pod.actor, ZoneType.Battlefield);
        addCardToZone("Mountain", opponent, ZoneType.Battlefield);
        Card found = addCardToZone("Failure // Comply", pod.actor, ZoneType.Library);
        addCardToZone("Island", pod.actor, ZoneType.Library);
        Card bolt = addCardToZone("Lightning Bolt", opponent, ZoneType.Hand);
        List<Integer> offered = new ArrayList<>();
        control(pod, found, 2, offered);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        int life = pod.actor.getLife();
        SpellAbility response = bolt.getFirstSpellAbility();
        response.setActivatingPlayer(opponent);
        response.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(response, opponent, pod.game));
        SpellAbility entry = pod.host.getFirstSpellAbility();
        entry.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(entry, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 2);
        resolveDiscover(pod);
        assertEquals(pod.game.getStack().size(), 2);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getId(), found.getId());
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getCMC(), 2);
        assertEquals(offered, List.of(2));
        settle(pod.game);
        assertTrue(pod.game.getCardState(found).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(bolt).isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getLife(), life);
    }

    private void verify(String name, String blockerName, int chosenValue,
            ZoneType result, ZoneType blockerResult) {
        Pod pod = pod();
        Player opponent = pod.game.getPlayers().get(1);
        Card blocker = blockerName == null ? null : addCardToZone(blockerName, opponent, ZoneType.Battlefield);
        Card skipped = addCardToZone("Plains", pod.actor, ZoneType.Library);
        Card found = addCardToZone(name, pod.actor, ZoneType.Library);
        Card untouched = addCardToZone("Island", pod.actor, ZoneType.Library);
        List<Integer> offered = new ArrayList<>();
        control(pod, found, chosenValue, offered);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        enter(pod);
        resolveDiscover(pod);
        if (result == ZoneType.Hand) {
            assertTrue(pod.game.getStack().isEmpty());
            assertEquals(pod.game.getStack().getSpellsCastThisTurn().size(), 1);
        } else {
            assertEquals(pod.game.getStack().size(), 1);
            SpellAbility spell = pod.game.getStack().peekAbility();
            assertEquals(spell.getHostCard().getId(), found.getId());
            assertEquals(spell.getHostCard().getCMC(), chosenValue,
                    "The resulting spell must use the chosen face's mana value");
            assertFalse(offered.isEmpty());
            assertTrue(offered.stream().allMatch(value -> value <= 3));
            assertEquals(pod.game.getStack().getSpellsCastThisTurn().size(), 2);
        }
        settle(pod.game);
        assertTrue(pod.game.getCardState(found).isInZone(result), "Unexpected discovered-card destination: " + name);
        if (blockerResult != null) assertTrue(pod.game.getCardState(blocker).isInZone(blockerResult));
        assertTrue(pod.game.getCardState(skipped).isInZone(ZoneType.Library));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).getFirst().getId(), untouched.getId());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 2);
    }

    private void control(Pod pod, Card found, int chosenValue, List<Integer> offered) {
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (params != null && params.get("Card") instanceof Card discovered && discovered.getId() == found.getId()) {
                    assertTrue(pod.game.getCardState(found).isInZone(ZoneType.Exile));
                    return true;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }

            @Override
            public SpellAbility getAbilityToPlay(Card card, List<SpellAbility> abilities, ITriggerEvent event) {
                if (card.getId() == found.getId()) {
                    assertTrue(abilities.stream().noneMatch(SpellAbility::isLandAbility));
                    assertTrue(abilities.stream().noneMatch(ability -> ability.isKeyword(Keyword.FUSE)));
                    assertTrue(abilities.stream().noneMatch(SpellAbility::isAftermath));
                    for (SpellAbility ability : abilities) offered.add(ability.getPayCosts().getTotalMana().getCMC());
                    assertTrue(offered.stream().allMatch(value -> value <= 3));
                    return abilities.stream().filter(ability -> ability.getPayCosts().getTotalMana().getCMC() == chosenValue)
                            .findFirst().orElseThrow();
                }
                return super.getAbilityToPlay(card, abilities, event);
            }
        });
    }

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Discover cast forms"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (int i = 0; i < 4; i++) addCardToZone("Mountain", actor, ZoneType.Battlefield);
        for (Player player : game.getPlayers()) if (player != actor) {
            for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Card host = addCardToZone("Geological Appraiser", actor, ZoneType.Hand);
        return new Pod(game, actor, host);
    }

    private void enter(Pod pod) {
        SpellAbility spell = pod.host.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Battlefield));
        assertTrue(pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).allMatch(Card::isTapped));
    }

    private void resolveDiscover(Pod pod) {
        pod.game.copyLastState();
        pod.game.getStack().resolveStack();
        queue(pod.game);
    }

    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        queue(game);
        for (int step = 0; step < 16 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
            game.getAction().checkStateEffects(true);
            queue(game);
        }
        assertTrue(game.getStack().isEmpty());
    }

    private record Pod(Game game, Player actor, Card host) {}
}
