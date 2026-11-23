package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Information-only text does not erase public material interactions with other permanents. */
public class CommanderInformationContextTest extends InformationChoiceTestSupport {
    @Test public void heuristicUsesAProviderCastThatDrawsACard() { castTrigger(null); }
    @Test public void hybridUsesAProviderCastThatDrawsACard() { castTrigger(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullUsesAProviderCastThatDrawsACard() { castTrigger(AIOption.USE_FULL_SIMULATION); }

    @Test public void grantedManaMakesAProviderMoreThanPureInformation() {
        Pod pod = manaPod();
        Card lens = pod.game.getCardState(pod.spell.getHostCard());
        assertFalse(lens.getManaAbilities().isEmpty());
        assertFalse(InformationEffects.isPureInformationPermanent(lens), "Granted material abilities must participate in evaluation");
    }
    @Test public void copiedGrantedManaStillPreventsPureInformationClassification() {
        Pod pod = manaPod();
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy(null, pod.actor);
        Card lens = copy.getCardState(copier.find(pod.spell.getHostCard()));
        assertFalse(lens.getManaAbilities().isEmpty());
        assertFalse(InformationEffects.isPureInformationPermanent(lens));
    }
    @Test public void aProvidersGrantedManaPaysForANativeInstant() {
        Pod pod = manaPod();
        Card lens = pod.game.getCardState(pod.spell.getHostCard());
        pod.actor.getLandsInPlay().forEach(card -> card.setTapped(true));
        assertFalse(lens.isTapped());
        SpellAbility opt = addCardToZone("Opt", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        opt.setActivatingPlayer(pod.actor);
        assertTrue(opt.canPlay());
        assertTrue(ComputerUtil.playStack(opt, pod.actor, pod.game));
        assertEquals(opt.getPayingMana().size(), 1);
        assertTrue(lens.isTapped(), "With every land tapped, the artifact supplies the actual blue payment");
        settle(pod.game);
    }

    @Test public void heuristicUsesAnAdditionalGrantedManaSource() { manaCast(null); }
    @Test public void hybridUsesAnAdditionalGrantedManaSource() { manaCast(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullUsesAnAdditionalGrantedManaSource() { manaCast(AIOption.USE_FULL_SIMULATION); }

    private void manaCast(AIOption mode) {
        Pod pod = manaPod();
        pod.actor.setLandsPlayedThisTurn(pod.actor.getMaxLandPlays());
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card second = addCardToZone("Lens of Clarity", pod.actor, ZoneType.Hand);
        List<SpellAbility> choices = controller(pod.actor, mode).chooseSpellAbilityToPlay();
        assertNotNull(choices, "A granted material ability can justify the cast without new inspection");
        SpellAbility selected = choices.stream().filter(action -> action.getHostCard().getId() == second.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(selected.getPayingMana().size(), 1); settle(pod.game);
        assertFalse(pod.game.getCardState(second).getManaAbilities().isEmpty());
    }

    @Test public void heuristicDeclinesAnInformationCastWithANetDamageCost() { harmfulCast(null); }
    @Test public void hybridDeclinesAnInformationCastWithANetDamageCost() { harmfulCast(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullDeclinesAnInformationCastWithANetDamageCost() { harmfulCast(AIOption.USE_FULL_SIMULATION); }
    @Test public void paidInformationCastActuallyTakesItsNativeCastDamage() {
        Pod pod = pod("Lens of Clarity"); addContext(pod, "Eidolon of the Great Revel");
        int life = pod.actor.getLife(); assertTrue(life > 2);
        cast(pod, "Lens of Clarity"); settle(pod.game);
        assertEquals(pod.actor.getLife(), life - 2);
        assertTrue(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
    }

    private void harmfulCast(AIOption mode) {
        Pod pod = pod("Lens of Clarity"); addContext(pod, "Eidolon of the Great Revel");
        int life = pod.actor.getLife(); assertTrue(life > 2);
        List<SpellAbility> choices = controller(pod.actor, mode).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(action -> action.getHostCard().getId() == pod.spell.getHostCard().getId()),
                "Native cast harm outweighs the supplied small information gain");
        assertEquals(pod.actor.getLife(), life);
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.game.getStack().isEmpty());
    }

    private void castTrigger(AIOption mode) {
        Pod pod = pod("Lens of Clarity");
        addContext(pod, "Jhoira, Weatherlight Captain");
        pod.actor.setLandsPlayedThisTurn(pod.actor.getMaxLandPlays());
        int before = pod.actor.getCardsIn(ZoneType.Hand).size();
        cast(pod, "Lens of Clarity"); settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), before, "The native cast trigger replaces the consumed card");
        assertTrue(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card second = addCardToZone("Lens of Clarity", pod.actor, ZoneType.Hand);
        List<SpellAbility> choices = controller(pod.actor, mode).chooseSpellAbilityToPlay();
        assertNotNull(choices, "Existing information does not make an actual card-drawing cast redundant");
        SpellAbility selected = choices.stream().filter(action -> action.getHostCard().getId() == second.getId()).findFirst().orElseThrow();
        int hand = pod.actor.getCardsIn(ZoneType.Hand).size();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(selected.getPayingMana().size(), 1); settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), hand);
    }
    private Pod manaPod() {
        Pod pod = pod("Lens of Clarity");
        addContext(pod, "Galazeth Prismari");
        cast(pod, "Lens of Clarity"); settle(pod.game);
        return pod;
    }
    private void addContext(Pod pod, String name) {
        addCardToZone(name, pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
    }
}
