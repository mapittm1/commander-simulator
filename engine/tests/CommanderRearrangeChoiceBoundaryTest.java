package forge.ai;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid rearrange admission must preserve declared choices, payment and private observations. */
public class CommanderRearrangeChoiceBoundaryTest extends InformationChoiceTestSupport {
    @Test public void redundantPreviewRestoresUnannouncedX() { redundant(null); }
    @Test public void redundantPreviewPreservesPreviouslyDeclaredX() { redundant(2); }
    @Test public void emptyPreviewRestoresUnannouncedX() {
        Setup setup = setup("Soothsaying");
        for (Card card : List.copyOf(setup.actor.getCardsIn(ZoneType.Library))) setup.game.getAction().exile(card, null, null);
        assertFalse(SpellApiToAi.Converter.get(setup.action).canPlayWithSubs(setup.actor, setup.action).willingToPlay());
        assertNull(setup.action.getXManaCostPaid()); assertEquals(knownCount(setup.actor, setup.actor), 0);
    }
    @Test public void admissionAppliesToTheAbilityRatherThanEveryAbilityOnItsHost() {
        Setup setup = setup("Soothsaying");
        assertTrue(InformationEffects.supportsPrimary(setup.action));
        SpellAbility shuffle = setup.host.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.Shuffle).findFirst().orElseThrow();
        assertFalse(InformationEffects.supportsPrimary(shuffle));
    }
    @Test public void hybridCapsStandaloneOwnInspectionToPublicLibrarySize() {
        Setup setup = setup("Soothsaying");
        List<Card> cards = List.copyOf(setup.actor.getCardsIn(ZoneType.Library));
        for (int index = 2; index < cards.size(); index++) setup.game.getAction().exile(cards.get(index), null, null);
        SpellAbility selected = choose(setup);
        assertEquals(selected.getXManaCostPaid(), Integer.valueOf(2));
        pay(setup, selected, 2); assertEquals(knownCount(setup.actor, setup.actor), 2);
    }
    @Test public void hybridUsesAnAffordableFixedCostInspection() {
        Setup setup = setup("Aven Fateshaper");
        addCardToZone("Island", setup.actor, ZoneType.Battlefield);
        SpellAbility selected = choose(setup); pay(setup, selected, 5);
        assertEquals(knownCount(setup.actor, setup.actor), 4);
    }
    @Test public void hybridDeclinesFixedCostWithoutItsRequiredColor() {
        Setup setup = setup("Aven Fateshaper");
        for (Card land : List.copyOf(setup.actor.getLandsInPlay())) setup.game.getAction().exile(land, null, null);
        for (int index = 0; index < 6; index++) addCardToZone("Forest", setup.actor, ZoneType.Battlefield);
        assertFalse(ComputerUtilCost.canPayCost(setup.action, setup.actor, false));
        List<SpellAbility> choices = controller(setup.actor, AIOption.USE_HYBRID_SIMULATION).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(sa -> sa.getHostCard().getId() == setup.host.getId()));
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertEquals(knownCount(setup.actor, setup.actor), 0);
    }
    @Test public void selectedOwnInspectionDoesNotDiscloseOtherPlayersLibraries() {
        Setup setup = setup("Soothsaying"); SpellAbility selected = choose(setup);
        for (Player owner : setup.game.getPlayers()) assertEquals(knownCount(setup.actor, owner), 0);
        int x = selected.getXManaCostPaid(); pay(setup, selected, x);
        for (Player owner : setup.game.getPlayers()) {
            if (owner != setup.actor) {
                assertEquals(knownCount(setup.actor, owner), 0);
                assertEquals(knownCount(owner, setup.actor), 0);
            }
        }
        assertEquals(knownCount(setup.actor, setup.actor), x);
    }

    @Test public void paidTargetSearchUsesTheOnlyLegalPlayer() {
        Setup setup = setup("Elemental Augury");
        for (Player player : setup.game.getPlayers()) if (player != setup.actor)
            addCardToZone("Leyline of Sanctity", player, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true); setup.game.getTriggerHandler().resetActiveTriggers();
        for (Player player : setup.game.getPlayers()) assertEquals(setup.action.canTarget(player), player == setup.actor);
        SpellAbility selected = choose(setup);
        java.util.Iterator<Player> targets = selected.getTargets().getTargetPlayers().iterator();
        assertTrue(targets.hasNext()); assertSame(targets.next(), setup.actor); assertFalse(targets.hasNext());
        pay(setup, selected, 3); assertEquals(knownCount(setup.actor, setup.actor), 3);
        for (Player player : setup.game.getPlayers()) if (player != setup.actor) assertEquals(knownCount(setup.actor, player), 0);
    }
    @Test public void paidTargetSearchDeclinesWhenEveryPlayerHasShroud() {
        Setup setup = setup("Elemental Augury");
        for (Player player : setup.game.getPlayers()) addCardToZone("Solitary Confinement", player, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true); setup.game.getTriggerHandler().resetActiveTriggers(); setup.game.copyLastState();
        for (Player player : setup.game.getPlayers()) assertFalse(setup.action.canTarget(player));
        List<SpellAbility> choices = controller(setup.actor, AIOption.USE_HYBRID_SIMULATION).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(sa -> sa.getHostCard().getId() == setup.host.getId()));
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty());
        for (Player player : setup.game.getPlayers()) assertEquals(knownCount(setup.actor, player), 0);
    }

    @Test public void expiredPreviewRestoresUnannouncedXAndRecovers() { expired(null); }
    @Test public void expiredPreviewRestoresPreviouslyDeclaredXAndRecovers() { expired(2); }
    private void expired(Integer previousX) {
        Setup setup = setup("Soothsaying"); setup.action.setXManaCostPaid(previousX);
        Object limit = forge.util.AITimeoutTracker.arm(System.nanoTime() - 1, true);
        try {
            assertThrows(forge.util.AITimeoutTracker.Expired.class,
                    () -> SpellApiToAi.Converter.get(setup.action).canPlayWithSubs(setup.actor, setup.action));
        } finally {
            forge.util.AITimeoutTracker.disarm(limit);
        }
        assertEquals(setup.action.getXManaCostPaid(), previousX);
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty()); assertEquals(knownCount(setup.actor, setup.actor), 0);
        assertTrue(SpellApiToAi.Converter.get(setup.action).canPlayWithSubs(setup.actor, setup.action).willingToPlay(),
                "The timeout must not leak into the next native preview");
        assertEquals(knownCount(setup.actor, setup.actor), 0);
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }

    private void redundant(Integer previousX) {
        Setup setup = setup("Soothsaying"); Map<Integer, Card> positions = new LinkedHashMap<>();
        for (int index = 0; index < setup.actor.getCardsIn(ZoneType.Library).size(); index++) positions.put(index, setup.actor.getCardsIn(ZoneType.Library).get(index));
        ((PlayerControllerAi) setup.actor.getController()).getAi().getCardMemory().rememberLibraryPositions(setup.actor, positions);
        setup.action.setXManaCostPaid(previousX);
        assertFalse(SpellApiToAi.Converter.get(setup.action).canPlayWithSubs(setup.actor, setup.action).willingToPlay());
        assertEquals(setup.action.getXManaCostPaid(), previousX);
        assertEquals(knownCount(setup.actor, setup.actor), positions.size());
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty());
    }
    private SpellAbility choose(Setup setup) {
        setup.game.copyLastState(); List<SpellAbility> choices = controller(setup.actor, AIOption.USE_HYBRID_SIMULATION).chooseSpellAbilityToPlay();
        assertNotNull(choices); SpellAbility selected = choices.stream().filter(sa -> sa.getHostCard().getId() == setup.host.getId()
                && sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty()); return selected;
    }
    private void pay(Setup setup, SpellAbility selected, int expected) {
        assertTrue(ComputerUtil.playStack(selected, setup.actor, setup.game));
        assertEquals(selected.getPayingMana().size(), expected); settle(setup.game);
    }
    private Setup setup(String name) {
        Pod pod = pod("Index"); pod.game.getAction().exile(pod.spell.getHostCard(), null, null);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card host = addCardToZone(name, pod.actor, ZoneType.Battlefield);
        pod.game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, pod.game.getPlayers().get(3), 2);
        pod.game.getPhaseHandler().setPriority(pod.actor);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        SpellAbility action = host.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.setActivatingPlayer(pod.actor); return new Setup(pod.game, pod.actor, host, action);
    }
    private int knownCount(Player observer, Player owner) {
        int count = 0; for (int index = 0; index < owner.getCardsIn(ZoneType.Library).size(); index++)
            if (AiCardMemory.isLibraryPositionKnown(observer, owner, index)) count++;
        return count;
    }
    private record Setup(Game game, Player actor, Card host, SpellAbility action) { }
}
