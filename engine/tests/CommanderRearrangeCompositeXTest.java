package forge.ai;

import forge.game.ability.ApiType;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** An X-dependent native follow-up shares the whole paid X, beyond the inspected library size. */
public class CommanderRearrangeCompositeXTest extends InformationChoiceTestSupport {
    @Test public void sharedXFollowupIsNotCappedByTheInspectedLibrary() {
        Pod pod = pod("Index"); pod.game.getAction().exile(pod.spell.getHostCard(), null, null);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        List<Card> cards = List.copyOf(pod.actor.getCardsIn(ZoneType.Library));
        for (int index = 2; index < cards.size(); index++) pod.game.getAction().exile(cards.get(index), null, null);
        Card host = addCardToZone("Soothsaying", pod.actor, ZoneType.Battlefield);
        // Trusted native effect composition: the same declared X also gains that much life.
        // This intentionally tests shared engine semantics rather than claiming a printed card has this text.
        host.setSVar("RearrangeFollowup", "DB$ GainLife | Defined$ You | LifeAmount$ X");
        SpellAbility action = host.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.putParam("SubAbility", "RearrangeFollowup");
        action.setSubAbility((AbilitySub) AbilityFactory.getAbility(host.getSVar("RearrangeFollowup"), host));
        action.setActivatingPlayer(pod.actor);
        pod.game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, pod.game.getPlayers().get(3), 2);
        pod.game.getPhaseHandler().setPriority(pod.actor);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        assertEquals(pod.actor.getLandsInPlay().size(), 4); assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 2);
        List<SpellAbility> choices = controller(pod.actor, AIOption.USE_HYBRID_SIMULATION).chooseSpellAbilityToPlay();
        assertNotNull(choices);
        SpellAbility selected = choices.stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary && sa.getHostCard().getId() == host.getId()).findFirst().orElseThrow();
        assertNotNull(selected.getSubAbility()); assertEquals(selected.getSubAbility().getApi(), ApiType.GainLife);
        assertEquals(selected.getXManaCostPaid(), Integer.valueOf(4), "A two-card inspection does not cap its shared-X life-gain follow-up");
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        int life = pod.actor.getLife(); assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(selected.getPayingMana().size(), 4); settle(pod.game); assertEquals(pod.actor.getLife(), life + 4);
        assertTrue(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.actor, 0));
        assertTrue(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.actor, 1));
    }
}
