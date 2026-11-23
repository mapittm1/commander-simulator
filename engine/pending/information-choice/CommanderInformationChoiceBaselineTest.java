package forge.ai;

import forge.ai.simulation.GameCopier;
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
import static org.testng.Assert.*;

/** Bounded supplied-pod behavior contracts; these do not prescribe every strategic choice. */
public class CommanderInformationChoiceBaselineTest extends AITest {
    @Test public void heuristicChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", null); }
    @Test public void hybridChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicChoosesLensForAnUnknownFace() { choice("Lens of Clarity", null); }
    @Test public void hybridChoosesLensForAnUnknownFace() { choice("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullChoosesLensForAnUnknownFace() { choice("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }

    @Test public void heuristicRecognizesSpyNetworkAdmission() { admission("Spy Network", null); }
    @Test public void hybridRecognizesSpyNetworkAdmission() { admission("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullRecognizesSpyNetworkAdmission() { admission("Spy Network", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicRecognizesLensAdmission() { admission("Lens of Clarity", null); }
    @Test public void hybridRecognizesLensAdmission() { admission("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullRecognizesLensAdmission() { admission("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }

    @Test public void paidSpyNetworkResolvesPrivately() { paid("Spy Network", false, false); }
    @Test public void copiedPendingSpyNetworkKeepsItsOwnDisclosure() { paid("Spy Network", true, false); }
    @Test public void counteredSpyNetworkDisclosesNothing() { paid("Spy Network", false, true); }
    @Test public void unaffordableSpyNetworkDisclosesNothing() { unaffordable("Spy Network"); }
    @Test public void paidLensGrantsOnlyItsAuthorizedAccess() { paid("Lens of Clarity", false, false); }
    @Test public void copiedPendingLensKeepsItsOwnAccess() { paid("Lens of Clarity", true, false); }
    @Test public void counteredLensGrantsNoAccess() { paid("Lens of Clarity", false, true); }
    @Test public void unaffordableLensGrantsNoAccess() { unaffordable("Lens of Clarity"); }

    @Test public void heuristicDoesNotCastARedundantLens() { redundant("Lens of Clarity", null); }
    @Test public void hybridDoesNotCastARedundantLens() { redundant("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullDoesNotCastARedundantLens() { redundant("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", null); }
    @Test public void hybridDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", AIOption.USE_FULL_SIMULATION); }

    private void admission(String name, AIOption mode) {
        Pod pod = pod(name);
        AiController ai = controller(pod.actor, mode);
        if (name.equals("Spy Network")) pod.spell.getTargets().add(pod.owner);
        assertTrue(pod.spell.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.spell, pod.actor, false));
        AiAbilityDecision policy = SpellApiToAi.Converter.get(pod.spell).canPlayWithSubs(pod.actor, pod.spell);
        System.out.println("INFORMATION_ADMISSION name=" + name + "; mode=" + mode
                + "; handler=" + SpellApiToAi.Converter.get(pod.spell).getClass().getName()
                + "; policy=" + policy.decision() + "; controller=" + ai.canPlaySa(pod.spell));
        assertFalse(known(pod.actor, pod.hand));
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.game.getStack().isEmpty());
        assertNotEquals(policy.decision(), AiPlayDecision.CantPlayAi,
                "A legal primary information effect needs supported admission or a supported policy decline");
    }

    private void choice(String name, AIOption mode) {
        Pod pod = pod(name);
        AiController ai = controller(pod.actor, mode);
        List<SpellAbility> choices = ai.chooseSpellAbilityToPlay();
        System.out.println("INFORMATION_CHOICE name=" + name + "; mode=" + mode + "; choices=" + choices);
        assertFalse(known(pod.actor, pod.hand), "Planning cannot disclose an unpaid enemy hand");
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.game.getStack().isEmpty());
        assertNotNull(choices, "This otherwise idle supplied pod should use a useful affordable information spell");
        SpellAbility selected = choices.stream().filter(action -> action.getHostCard().getId() == pod.spell.getHostCard().getId())
                .findFirst().orElseThrow();
        if (name.equals("Spy Network")) {
            java.util.Iterator<Player> targets = selected.getTargets().getTargetPlayers().iterator();
            assertTrue(targets.hasNext());
            assertSame(targets.next(), pod.owner, "Prefer the supplied opponent with two unknown hand cards");
            assertFalse(targets.hasNext(), "There is exactly one announced target player");
        }
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(selected.getPayingMana().size(), 1);
        settle(pod.game);
        assertDisclosure(pod, name, true);
    }

    private void redundant(String name, AIOption mode) {
        Pod pod = pod(name);
        cast(pod, name);
        settle(pod.game);
        assertDisclosure(pod, name, true);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card second = addCardToZone(name, pod.actor, ZoneType.Hand);
        List<SpellAbility> choices = controller(pod.actor, mode).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(action -> action.getHostCard().getId() == second.getId()),
                "No new objects, library order or information justify paying again in this supplied pod");
    }

    private void paid(String name, boolean copied, boolean countered) {
        Pod pod = pod(name);
        cast(pod, name);
        assertFalse(known(pod.actor, pod.hand));
        if (countered) {
            for (int index = 0; index < 2; index++) addCardToZone("Island", pod.owner, ZoneType.Battlefield);
            pod.game.getPhaseHandler().setPriority(pod.owner);
            SpellAbility response = addCardToZone("Counterspell", pod.owner, ZoneType.Hand).getFirstSpellAbility();
            response.setActivatingPlayer(pod.owner);
            response.getTargets().add(pod.game.getStack().peekAbility());
            assertTrue(response.canPlay());
            assertTrue(ComputerUtil.playStack(response, pod.owner, pod.game));
            assertEquals(response.getPayingMana().size(), 2);
        }
        Pod resolved = pod;
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game game = copier.makeCopy(null, pod.actor);
            resolved = new Pod(game, copier.find(pod.actor), copier.find(pod.owner),
                    pod.hidden == null ? null : copier.find(pod.hidden), copier.find(pod.hand),
                    copier.find(pod.top), copier.find(pod.ownTop), pod.spell);
        }
        settle(resolved.game);
        assertDisclosure(resolved, name, !countered);
        if (copied) {
            assertDisclosure(pod, name, false);
            assertEquals(pod.game.getStack().size(), 1, "The original paid spell is still pending");
        }
        if (countered) assertTrue(pod.game.getCardState(pod.spell.getHostCard()).isInZone(ZoneType.Graveyard));
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    private void unaffordable(String name) {
        Pod pod = pod(name);
        pod.actor.getLandsInPlay().forEach(card -> card.setTapped(true));
        if (name.equals("Spy Network")) pod.spell.getTargets().add(pod.owner);
        assertTrue(pod.spell.canPlay());
        assertFalse(ComputerUtilCost.canPayCost(pod.spell, pod.actor, false));
        assertFalse(ComputerUtil.playStack(pod.spell, pod.actor, pod.game));
        assertDisclosure(pod, name, false);
        assertTrue(pod.game.getStack().isEmpty());
    }

    private void cast(Pod pod, String name) {
        if (name.equals("Spy Network")) pod.spell.getTargets().add(pod.owner);
        assertTrue(pod.spell.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.spell, pod.actor, false));
        assertTrue(ComputerUtil.playStack(pod.spell, pod.actor, pod.game));
        assertEquals(pod.spell.getPayingMana().size(), 1);
    }

    private void assertDisclosure(Pod pod, String name, boolean disclosed) {
        if (name.equals("Spy Network")) {
            assertEquals(known(pod.actor, pod.hand), disclosed);
            assertEquals(known(pod.actor, pod.top), disclosed);
            for (Player other : pod.game.getPlayers()) {
                if (other != pod.actor && other != pod.owner) {
                    assertFalse(known(other, pod.hand));
                    assertFalse(known(other, pod.top));
                }
            }
        } else {
            assertEquals(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()), disclosed);
            assertEquals(pod.ownTop.getView().canBeShownTo(pod.actor.getView()), disclosed);
            assertFalse(known(pod.actor, pod.hand), "Lens does not disclose an opponent's hand");
            for (Player other : pod.game.getPlayers()) {
                if (other != pod.actor && other != pod.owner) assertFalse(pod.hidden.getView().canFaceDownBeShownTo(other.getView()));
                if (other != pod.actor) assertFalse(pod.ownTop.getView().canBeShownTo(other.getView()));
            }
        }
    }

    private AiController controller(Player actor, AIOption mode) {
        AiController ai = ((PlayerControllerAi) actor.getController()).getAi();
        ai.setUseSimulation(mode);
        return ai;
    }

    private boolean known(Player observer, Card card) {
        return AiCardMemory.isRememberedCard(observer, card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }

    private Pod pod(String name) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("information-choice-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Information choice research"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), owner = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Card hand = addCardToZone("Lightning Bolt", owner, ZoneType.Hand);
        addCardToZone("Sol Ring", owner, ZoneType.Hand);
        Card hidden = null;
        if (name.equals("Lens of Clarity")) {
            for (int index = 0; index < 3; index++) addCardToZone("Island", owner, ZoneType.Battlefield);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 2);
            Card morphCard = addCardToZone("Willbender", owner, ZoneType.Hand);
            SpellAbility morph = morphCard.getAllPossibleAbilities(owner, false).stream()
                    .filter(action -> action.isSpell() && action.isCastFaceDown()).findFirst().orElseThrow();
            morph.setActivatingPlayer(owner);
            assertTrue(morph.canPlay());
            assertTrue(ComputerUtil.playStack(morph, owner, game));
            assertEquals(morph.getPayingMana().size(), 3);
            settle(game);
            hidden = game.getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.getId() == morphCard.getId()).findFirst().orElseThrow();
            assertTrue(hidden.isFaceDown());
            assertFalse(hidden.getView().canFaceDownBeShownTo(actor.getView()));
        }
        for (int index = 0; index < 2; index++) addCardToZone("Island", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 2);
        SpellAbility spell = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        Card top = owner.getCardsIn(ZoneType.Library).get(0), ownTop = actor.getCardsIn(ZoneType.Library).get(0);
        assertFalse(known(actor, hand)); assertFalse(known(actor, top));
        assertFalse(ownTop.getView().canBeShownTo(actor.getView()));
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        return new Pod(game, actor, owner, hidden, hand, top, ownTop, spell);
    }

    private void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Information choice research did not settle");
    }

    private record Pod(Game game, Player actor, Player owner, Card hidden, Card hand, Card top, Card ownTop, SpellAbility spell) { }
}
