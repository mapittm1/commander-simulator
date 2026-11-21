package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderPreventedReturnTest extends AITest {
    @Test
    public void reaperKeepsItsReturnWhenGrafdiggersCagePreventsTheMove() {
        verifyReturn("The Reaper, King No More", false, "Grafdigger's Cage", ZoneType.Graveyard);
    }

    @Test
    public void rinoaKeepsItsReturnWhenGrafdiggersCagePreventsTheMove() {
        verifyReturn("Rinoa, Angel Wing", true, "Grafdigger's Cage", ZoneType.Graveyard);
    }

    @Test
    public void aReaperReturnReplacedByContainmentPriestStillUsesTheAction() {
        verifyReturn("The Reaper, King No More", false, "Containment Priest", ZoneType.Exile);
    }

    @Test
    public void aRinoaReturnReplacedByContainmentPriestStillUsesTheAction() {
        verifyReturn("Rinoa, Angel Wing", true, "Containment Priest", ZoneType.Exile);
    }

    @Test
    public void kunorosPreservesReapersForbiddenReturn() {
        verifyReturn("The Reaper, King No More", false, "Kunoros, Hound of Athreos", ZoneType.Graveyard);
    }

    @Test
    public void kunorosPreservesRinoasForbiddenReturn() {
        verifyReturn("Rinoa, Angel Wing", true, "Kunoros, Hound of Athreos", ZoneType.Graveyard);
    }

    @Test
    public void soullessJailerPreservesReapersForbiddenReturn() {
        verifyReturn("The Reaper, King No More", false, "Soulless Jailer", ZoneType.Graveyard);
    }

    @Test
    public void soullessJailerPreservesRinoasForbiddenReturn() {
        verifyReturn("Rinoa, Angel Wing", true, "Soulless Jailer", ZoneType.Graveyard);
    }

    @Test
    public void weatheredRunestonePreservesReapersForbiddenReturn() {
        verifyReturn("The Reaper, King No More", false, "Weathered Runestone", ZoneType.Graveyard);
    }

    @Test
    public void weatheredRunestonePreservesRinoasForbiddenReturn() {
        verifyReturn("Rinoa, Angel Wing", true, "Weathered Runestone", ZoneType.Graveyard);
    }

    @Test
    public void wormsOfTheEarthDoesNotPreventReapersCreatureReturn() {
        verifyReturn("The Reaper, King No More", false, "Worms of the Earth", ZoneType.Battlefield);
    }

    @Test
    public void wormsOfTheEarthDoesNotPreventRinoasCreatureReturn() {
        verifyReturn("Rinoa, Angel Wing", true, "Worms of the Earth", ZoneType.Battlefield);
    }

    @Test
    public void cageOffersOnlyTheDeadAttackerThatIsALandCard() {
        verifyMixedReturn("Grafdigger's Cage", true);
    }

    @Test
    public void runestoneOffersOnlyTheDeadAttackerThatIsALandCard() {
        verifyMixedReturn("Weathered Runestone", true);
    }

    @Test
    public void wormsOfTheEarthOffersOnlyTheDeadAttackerThatIsACreatureCard() {
        verifyMixedReturn("Worms of the Earth", false);
    }

    @Test
    public void wormsOfTheEarthPreservesAnImpossibleLandReturn() {
        Pod pod = newPod("Rinoa, Angel Wing");
        addCardToZone("Swamp", pod.actor, ZoneType.Battlefield);
        Card factory = animatedFactory(pod);
        Card worms = addCardToZone("Worms of the Earth", pod.game.getPlayers().get(2), ZoneType.Battlefield);
        prepareCombat(pod, List.of(factory));
        kill(pod, factory);
        assertTrue(pod.game.getCardState(factory).isInZone(ZoneType.Graveyard));
        removeBlocker(pod, worms);
        Card second = bear(pod, "Grizzly Bears", pod.actor);
        prepareCombat(pod, List.of(second));
        kill(pod, second);
        assertTrue(pod.game.getCardState(second).isInZone(ZoneType.Battlefield),
                "The forbidden land return must not consume Rinoa's action");
        Card third = bear(pod, "Runeclaw Bear", pod.actor);
        prepareCombat(pod, List.of(third));
        kill(pod, third);
        assertTrue(pod.game.getCardState(third).isInZone(ZoneType.Graveyard));
    }

    private void verifyReturn(String name, boolean attacking, String blockerName, ZoneType firstZone) {
        Pod pod = newPod(name);
        Card blocker = addCardToZone(blockerName, pod.game.getPlayers().get(2), ZoneType.Battlefield);
        Player owner = attacking ? pod.actor : pod.opponent;
        Card first = bear(pod, "Grizzly Bears", owner);
        Card second = bear(pod, "Runeclaw Bear", owner);
        Card third = bear(pod, "Balduvian Bears", owner);
        prepareCombat(pod, attacking ? List.of(first, second, third) : List.of());
        kill(pod, first);
        assertTrue(pod.game.getCardState(first).isInZone(firstZone),
                "The blocker must produce the expected first return outcome");
        if (firstZone == ZoneType.Battlefield) {
            assertEquals(pod.game.getCardState(first).getController(), pod.actor);
        }
        removeBlocker(pod, blocker);
        kill(pod, second);
        boolean firstReturnWasForbidden = firstZone == ZoneType.Graveyard;
        assertTrue(pod.game.getCardState(second).isInZone(
                firstReturnWasForbidden ? ZoneType.Battlefield : ZoneType.Graveyard),
                "A prohibition preserves the allowance; a legal or replaced return uses it");
        if (firstReturnWasForbidden) {
            assertEquals(pod.game.getCardState(second).getController(), pod.actor);
        }
        kill(pod, third);
        assertTrue(pod.game.getCardState(third).isInZone(ZoneType.Graveyard));
    }

    private void verifyMixedReturn(String blockerName, boolean returnLand) {
        Pod pod = newPod("Rinoa, Angel Wing");
        addCardToZone(blockerName, pod.game.getPlayers().get(2), ZoneType.Battlefield);
        Card creature = addCardToZone("Kozilek, the Great Distortion", pod.actor, ZoneType.Battlefield);
        creature.setCounters(CounterEnumType.M1M1, 11);
        Card factory = animatedFactory(pod);
        creature.setSickness(false);
        prepareCombat(pod, List.of(creature, factory));
        int[] selections = {0};
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().equals(pod.commander) || super.confirmTrigger(ability);
            }

            @Override
            public Card chooseSingleCardForZoneChange(ZoneType destination, List<ZoneType> origin,
                    SpellAbility ability, CardCollection options, DelayedReveal reveal,
                    String prompt, boolean optional, Player decider) {
                if (ability.getHostCard().equals(pod.commander)) {
                    selections[0]++;
                    assertEquals(destination, ZoneType.Battlefield);
                    assertEquals(options.size(), 1, "The prohibited creature card must not be offered");
                    assertEquals(options.get(0).getId(), returnLand ? factory.getId() : creature.getId());
                    return options.get(0);
                }
                return super.chooseSingleCardForZoneChange(destination, origin, ability,
                        options, reveal, prompt, optional, decider);
            }
        });
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        SpellAbility pyroclasm = addCardToZone("Pyroclasm", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        pyroclasm.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(pyroclasm, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        pod.game.getAction().checkStateEffects(true);
        queueTriggers(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getCardState(creature).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(factory).isInZone(ZoneType.Graveyard));
        assertTrue(!pod.game.getCardState(factory).isCreature());
        settle(pod.game);
        assertEquals(selections[0], 1);
        Card prohibited = pod.game.getCardState(returnLand ? creature : factory);
        assertTrue(prohibited.isInZone(ZoneType.Graveyard));
        Card returned = pod.game.getCardState(returnLand ? factory : creature);
        assertTrue(returned.isInZone(ZoneType.Battlefield));
        assertTrue(returned.isTapped());
        assertEquals(returned.isLand(), returnLand);
        assertEquals(returned.isCreature(), !returnLand);
        assertEquals(returned.getCounters(CounterType.getType("Flying")), 1);
    }

    private Card animatedFactory(Pod pod) {
        Card factory = addCardToZone("Mishra's Factory", pod.actor, ZoneType.Battlefield);
        SpellAbility animate = factory.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Animate).findFirst().orElseThrow();
        animate.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(animate, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(factory.isCreature());
        factory.setCounters(CounterEnumType.M1M1, 1);
        // Attacking preconditions are supplied; declaring the attack is not under test.
        factory.setTapped(false);
        factory.setSickness(false);
        return factory;
    }

    private Pod newPod(String name) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Prevented return"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        Card commander = addCardToZone(name, actor, ZoneType.Battlefield);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().equals(commander) || super.confirmTrigger(ability);
            }
        });
        for (int index = 0; index < 5; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        addCardToZone("Plains", actor, ZoneType.Battlefield);
        addCardToZone("Plains", actor, ZoneType.Battlefield);
        return new Pod(game, actor, opponent, commander);
    }

    private Card bear(Pod pod, String name, Player owner) {
        Card card = addCardToZone(name, owner, ZoneType.Battlefield);
        card.setCounters(CounterEnumType.M1M1, 1);
        return card;
    }

    private void prepareCombat(Pod pod, List<Card> attackers) {
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.getPhaseHandler().setCombat(new Combat(pod.actor));
        for (Card attacker : attackers) {
            pod.game.getCombat().addAttacker(attacker, pod.opponent);
        }
    }

    private void removeBlocker(Pod pod, Card blocker) {
        int lifeBefore = pod.actor.getLife();
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(blocker);
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), lifeBefore - 3);
    }

    private void kill(Pod pod, Card target) {
        SpellAbility cut = addCardToZone("Cut Down", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        cut.setActivatingPlayer(pod.actor);
        cut.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(cut, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queueTriggers(pod.game);
        settle(pod.game);
    }

    private void queueTriggers(Game game) {
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        for (int index = 0; index < 12 && !game.getStack().isEmpty(); index++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }

    private record Pod(Game game, Player actor, Player opponent, Card commander) {
    }
}
