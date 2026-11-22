package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Supplied native word choices are controls; AI cases use the unmodified native controller. */
public class CommanderChangeTextAiTest extends AITest {
    @Test public void nativeColorWordChangePaysManaAndChangesTheAnthem() {
        Pod p = color(); assertEquals(p.beneficiary.getNetPower(), 2); castChosen(p);
        assertEquals(p.target.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(p.beneficiary.getNetPower(), 3);
        assertEquals(p.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }
    @Test public void nativeBasicLandWordChangeChangesBlocking() {
        Pod p = land(); assertTrue(CombatUtil.canBlock(p.target, p.beneficiary)); castChosen(p);
        assertTrue(p.target.hasKeyword("Landwalk:Island")); assertFalse(p.target.hasKeyword("Landwalk:Forest"));
        assertFalse(CombatUtil.canBlock(p.target, p.beneficiary));
    }
    @Test public void nativeCreatureWordChangeChangesTheTribalBonus() {
        Pod p = creature(); assertEquals(p.beneficiary.getNetPower(), 2); castChosen(p);
        assertEquals(p.target.getChangedTextTypeWords().get("Elf"), "Goblin");
        assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void nativeColorChangeCopiesWithoutSharingTheWordMap() {
        Pod p = color(); castChosen(p); GameCopier copier = new GameCopier(p.game).withStack(true);
        Game copy = copier.makeCopy(); Card mapped = copier.find(p.target);
        assertSame(mapped.getGame(), copy); assertEquals(mapped.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(copier.find(p.beneficiary).getNetPower(), 3);
        mapped.addChangedTextColorWord("Blue", "Red", copy.getNextTimestamp(), 0);
        copy.getAction().checkStateEffects(true);
        assertEquals(p.target.getChangedTextColorWords().get("White"), "Blue");
        assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void unusedColorWordsDoNotChangeNamesOrColor() {
        Pod p = base("Sleight of Mind", "White Knight", "Coral Merfolk", false); castChosen(p);
        assertEquals(p.target.getName(), "White Knight"); assertTrue(p.target.isWhite());
        assertEquals(p.target.getNetPower(), 2);
    }
    @Test public void nativeAbsentColorWordDoesNotCreateAnAnthem() {
        Pod p = base("Sleight of Mind", "Grizzly Bears", "Coral Merfolk", false); castChosen(p);
        assertEquals(p.beneficiary.getNetPower(), 2); assertEquals(p.target.getNetPower(), 2);
    }
    @Test public void aiAdmitsAUsefulColorWordChange() { admission(color()); }
    @Test public void aiAdmitsAUsefulBasicLandWordChange() {
        Pod p = land();
        p.target.setCounters(forge.game.card.CounterEnumType.P1P1, 7);
        p.game.getPlayers().get(1).setLife(8, null);
        p.game.getAction().checkStateEffects(true); p.game.copyLastState();
        admission(p);
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertTrue(p.target.hasKeyword("Landwalk:Island"));
    }
    @Test public void lowImpactLandwalkDoesNotJustifySpendingTheCard() {
        Pod p = land();
        assertFalse(ai(p).canPlaySa(p.action) == AiPlayDecision.WillPlay);
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    @Test public void aiAdmitsAUsefulCreatureWordChange() { admission(creature()); }
    @Test public void heuristicPickerUsesAUsefulColorWordChange() { pick(null); }
    @Test public void hybridPickerUsesAUsefulColorWordChange() { pick(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerUsesAUsefulColorWordChange() { pick(AIOption.USE_FULL_SIMULATION); }

    private void admission(Pod p) {
        java.util.Map<String,String> rules = new java.util.HashMap<>(p.action.getMapParams());
        java.util.Map<String,String> originalRules = new java.util.HashMap<>(p.action.getOriginalMapParams());
        assertEquals(ai(p).canPlaySa(p.action), AiPlayDecision.WillPlay);
        assertEquals(p.action.getMapParams(), rules);
        assertEquals(p.action.getOriginalMapParams(), originalRules);
        assertTrue(p.game.getStack().isEmpty());
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    private void pick(AIOption mode) {
        Pod p = blockedColor(); ai(p).setUseSimulation(mode); List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "Expected a useful text change in mode " + mode);
        SpellAbility selected = choices.stream().filter(a -> a.getApi() == ApiType.ChangeText).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, p.actor, p.game)); settle(p.game);
        assertEquals(p.beneficiary.getNetPower(), 3, "Actual native controller must choose useful words");
        damage(p);
        assertTrue(p.beneficiary.isInZone(ZoneType.Battlefield));
        assertEquals(p.beneficiary.getDamage(), 1);

    }
    @Test public void fullModeRetainsItsPrecombatInstantDeferral() {
        Pod p = color(); ai(p).setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.isEmpty());
        assertEquals(p.beneficiary.getNetPower(), 2);
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    @Test public void chosenWordsSurviveOrdinaryAbilityCopies() {
        Pod p = color(); admission(p);
        assertFalse(p.action.hasParam("AITextColorFrom"), "Plans are not rules text");
        SpellAbility copied = p.action.copy();
        assertTrue(ComputerUtil.playStack(copied, p.actor, p.game)); settle(p.game);
        assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void aCopiedPendingFullModeSpellKeepsItsWordsAndOriginalIsolation() {
        Pod p = blockedColor(); ai(p).setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "Expected a planned text-change cast");
        SpellAbility selected = choices.stream()
                .filter(a -> a.getApi() == ApiType.ChangeText).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, p.actor, p.game));
        assertEquals(p.game.getStack().size(), 1);
        GameCopier copier = new GameCopier(p.game).withStack(true); Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty()); settle(copy);
        assertEquals(copier.find(p.beneficiary).getNetPower(), 3);
        assertEquals(p.beneficiary.getNetPower(), 2);
        assertEquals(p.game.getStack().size(), 1);
        settle(p.game); assertEquals(p.beneficiary.getNetPower(), 3);
    }
    @Test public void anAlreadyUsefulAnthemIsNotRewrittenHarmfully() {
        Pod p = alreadyBlue(); assertFalse(ai(p).canPlaySa(p.action) == AiPlayDecision.WillPlay);
        assertEquals(p.beneficiary.getNetPower(), 3);
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    @Test public void heuristicPickerRefusesTheHarmfulRewrite() { noPick(null); }
    @Test public void hybridPickerRefusesTheHarmfulRewrite() { noPick(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerRefusesTheHarmfulRewrite() { noPick(AIOption.USE_FULL_SIMULATION); }
    @Test public void copiedPlansDoNotChooseForADifferentActivator() {
        Pod p = color(); admission(p); Player other = p.game.getPlayers().get(1);
        addCardToZone("Shock", other, ZoneType.Hand);
        // Outside native resolution this metadata must not affect unrelated prompts.
        assertEquals(p.actor.getController().chooseColor("original word", p.action, ColorSet.WUBRG), MagicColor.BLUE);
        p.game.getStack().setResolving(true);
        try {
            assertEquals(p.actor.getController().chooseColor("original word", p.action, ColorSet.WUBRG), MagicColor.WHITE);
            SpellAbility copy = p.action.copy(); copy.setActivatingPlayer(other);
            assertEquals(other.getController().chooseColor("original word", copy, ColorSet.WUBRG), MagicColor.RED);
        } finally { p.game.getStack().setResolving(false); }
    }

    private void noPick(AIOption mode) {
        Pod p = alreadyBlue(); ai(p).setUseSimulation(mode);
        List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(a -> a.getApi() == ApiType.ChangeText));
        assertEquals(p.beneficiary.getNetPower(), 3);
        assertTrue(p.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
    }
    private Pod alreadyBlue() {
        Pod p = blockedColor();
        p.target.addChangedTextColorWord("White", "Blue", p.game.getNextTimestamp(), 0);
        p.game.getAction().checkStateEffects(true); p.game.copyLastState(); return p;
    }
    private Pod blockedColor() {
        Pod p = color(); Player defender = p.game.getPlayers().get(1);
        Card blocker = addCardToZone("Memnite", defender, ZoneType.Battlefield);
        forge.game.combat.Combat combat = new forge.game.combat.Combat(p.actor);
        p.beneficiary.setTapped(true); p.beneficiary.setSickness(false);
        combat.addAttacker(p.beneficiary, defender); combat.addBlocker(p.beneficiary, blocker);
        combat.getBandOfAttacker(p.beneficiary).setBlocked(true);
        p.beneficiary.addBlockedByThisTurn(forge.game.card.CardCopyService.getLKICopy(blocker));
        blocker.addBlockedThisTurn(forge.game.card.CardCopyService.getLKICopy(p.beneficiary));
        combat.orderBlockersForDamageAssignment(p.beneficiary, combat.getBlockers(p.beneficiary));
        combat.orderAttackersForDamageAssignment(blocker);
        p.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, p.actor, false, 2);
        p.game.getPhaseHandler().setCombat(combat);
        p.game.getAction().checkStateEffects(true); p.game.getTriggerHandler().resetActiveTriggers(); p.game.copyLastState();
        return p;
    }
    private void damage(Pod p) {
        forge.game.combat.Combat combat = p.game.getCombat();
        p.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, p.actor, false, 2);
        assertSame(p.game.getCombat(), combat); assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage(); p.game.getAction().checkStateEffects(true);
    }

    @Test public void alterRealityPaysItsNativeSpellCost() { additionalColorRoot("Alter Reality", ZoneType.Hand); }
    @Test public void glamerdyePaysItsNativeSpellCost() { additionalColorRoot("Glamerdye", ZoneType.Hand); }
    @Test public void shamanPaysItsTapCostAndRetainsTheAddedUpkeep() {
        Pod p = additionalColorRoot("Balduvian Shaman", ZoneType.Battlefield);
        assertTrue(p.host.isTapped()); assertTrue(p.target.hasKeyword("Cumulative upkeep:1"));
    }
    @Test public void changingTargetsInvalidatesAnOldWordPlan() {
        Pod p = color(); admission(p);
        Card replacement = addCardToZone("Bad Moon", p.actor, ZoneType.Battlefield);
        p.game.getStack().setResolving(true);
        try {
            assertEquals(p.actor.getController().chooseColor("original word", p.action, ColorSet.WUBRG), MagicColor.WHITE);
            SpellAbility copy = p.action.copy(); copy.resetTargets(); copy.getTargets().add(replacement);
            assertEquals(p.actor.getController().chooseColor("original word", copy, ColorSet.WUBRG), MagicColor.BLUE);
        } finally { p.game.getStack().setResolving(false); }
    }
    @Test public void wordForecastsPreserveTheCallerRandomStream() {
        Pod p = color(); java.util.Random stream = new java.util.Random(610L);
        forge.util.MyRandom.withRandom(stream, () -> {
            admission(p);
            assertSame(forge.util.MyRandom.getRandom(), stream);
            assertEquals(stream.nextLong(), new java.util.Random(610L).nextLong());
            return true;
        });
    }
    @Test public void artificialEvolutionNeverSelectsWallAsTheNewType() {
        Pod p = base("Artificial Evolution", "Elvish Archdruid", "Wall of Stone", false);
        addCardToZone("Wall of Stone", p.actor, ZoneType.Battlefield);
        addCardToZone("Wall of Stone", p.actor, ZoneType.Battlefield);
        p.game.getAction().checkStateEffects(true); p.game.copyLastState(); admission(p);
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertEquals(p.actor.getCreaturesInPlay().stream()
                .filter(c -> c.getName().equals("Wall of Stone") && c.getNetPower() == 1).count(), 1L);
        assertFalse(p.target.getChangedTextTypeWords().containsValue("Wall"));
    }
    @Test public void rewritingACounterspellOnTheStackProtectsTheBlueSpell() {
        CounterCase c = counterCase(); Pod p = c.p;
        int hand = p.actor.getCardsIn(ZoneType.Hand).size();
        assertEquals(ai(p).canPlaySa(p.action), AiPlayDecision.WillPlay);
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertEquals(p.actor.getCardsIn(ZoneType.Hand).size(), hand + 1, "Pay one card, then the saved spell draws two");
    }
    @Test public void nativeCounterspellTextChangeMakesItsBlueTargetIllegal() {
        CounterCase c = counterCase(); Pod p = c.p;
        p.actor.dangerouslySetController(new PlayerControllerAi(p.game, p.actor, p.actor.getLobbyPlayer()) {
            @Override public byte chooseColor(String title, SpellAbility action, ColorSet options) {
                return (options.getColor() & MagicColor.BLUE) != 0 ? MagicColor.BLUE : MagicColor.RED;
            }
        });
        int hand = p.actor.getCardsIn(ZoneType.Hand).size();
        p.action.getTargets().add(c.counter);
        assertTrue(p.action.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertEquals(p.actor.getCardsIn(ZoneType.Hand).size(), hand + 1);
    }
    @Test public void heuristicPickerRewritesThePendingCounterspell() { reactive(null); }
    @Test public void hybridPickerRewritesThePendingCounterspell() { reactive(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPickerRewritesThePendingCounterspell() { reactive(AIOption.USE_FULL_SIMULATION); }
    @Test public void aLegalNoEffectRewriteCanBeUsefulThroughCastTriggers() {
        Pod p = base("Sleight of Mind", "Grizzly Bears", "Coral Merfolk", false);
        addCardToZone("Archmage Emeritus", p.actor, ZoneType.Battlefield);
        addCardToZone("Archmage Emeritus", p.actor, ZoneType.Battlefield);
        p.game.getAction().checkStateEffects(true); p.game.getTriggerHandler().resetActiveTriggers(); p.game.copyLastState();
        int hand = p.actor.getCardsIn(ZoneType.Hand).size(); admission(p);
        assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertEquals(p.actor.getCardsIn(ZoneType.Hand).size(), hand + 1);
        assertEquals(p.target.getNetPower(), 2);
    }
    private void reactive(AIOption mode) {
        CounterCase c = counterCase(); Pod p = c.p; ai(p).setUseSimulation(mode);
        int hand = p.actor.getCardsIn(ZoneType.Hand).size();
        List<SpellAbility> choices = ai(p).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "A useful response must be available in mode " + mode);
        SpellAbility selected = choices.stream().filter(a -> a.getApi() == ApiType.ChangeText).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, p.actor, p.game)); settle(p.game);
        assertEquals(p.actor.getCardsIn(ZoneType.Hand).size(), hand + 1);
    }
    private CounterCase counterCase() {
        // No anthem reward competes with saving the pending draw spell.
        Pod p = base("Sleight of Mind", "Grizzly Bears", "Coral Merfolk", false);
        Player enemy = p.game.getPlayers().get(1);
        addCardToZone("Island", p.actor, ZoneType.Battlefield); addCardToZone("Island", p.actor, ZoneType.Battlefield);
        addCardToZone("Island", enemy, ZoneType.Battlefield);
        SpellAbility draw = addCardToZone("Divination", p.actor, ZoneType.Hand).getFirstSpellAbility();
        draw.setActivatingPlayer(p.actor); assertTrue(ComputerUtil.playStack(draw, p.actor, p.game));
        SpellAbility counter = addCardToZone("Gainsay", enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(enemy); counter.getTargets().add(draw);
        assertTrue(ComputerUtil.playStack(counter, enemy, p.game));
        assertEquals(p.game.getStack().size(), 2); p.game.copyLastState();
        return new CounterCase(p, draw, counter);
    }
    private record CounterCase(Pod p, SpellAbility draw, SpellAbility counter) { }
    private Pod additionalColorRoot(String name, ZoneType zone) {
        Pod p = base(name, "Honor of the Pure", "Coral Merfolk", false, zone);
        admission(p); assertTrue(ComputerUtil.playStack(p.action, p.actor, p.game)); settle(p.game);
        assertEquals(p.beneficiary.getNetPower(), 3); return p;
    }

    private AiController ai(Pod p) { return ((PlayerControllerAi)p.actor.getController()).getAi(); }
    private Pod color() { return base("Sleight of Mind", "Honor of the Pure", "Coral Merfolk", false); }
    private Pod land() { return base("Magical Hack", "Shanodin Dryads", "Grizzly Bears", true); }
    private Pod creature() { return base("Artificial Evolution", "Elvish Archdruid", "Goblin Piker", false); }
    private Pod base(String spell, String targetName, String beneficiaryName, boolean enemyBeneficiary) {
        return base(spell, targetName, beneficiaryName, enemyBeneficiary, ZoneType.Hand);
    }
    private Pod base(String spell, String targetName, String beneficiaryName, boolean enemyBeneficiary, ZoneType hostZone) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat"+i,null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats,rules,new Match(rules,seats,"Text change AI")); game.setNoGUIUser();game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0); Player enemy=game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,actor,2);
        for (Player player:game.getPlayers()) for(int i=0;i<8;i++) addCardToZone("Forest",player,ZoneType.Library);
        for(int i=0;i<2;i++) addCardToZone("Island",actor,ZoneType.Battlefield);
        addCardToZone("Island",enemy,ZoneType.Battlefield);
        Card target=addCardToZone(targetName,actor,ZoneType.Battlefield);target.setSickness(false);
        Card beneficiary=addCardToZone(beneficiaryName,enemyBeneficiary?enemy:actor,ZoneType.Battlefield);beneficiary.setSickness(false);
        // Blue cards that cannot be cast with two lands prevent a coincidental empty-hand color choice.
        addCardToZone("Omniscience",actor,ZoneType.Hand);addCardToZone("Enter the Infinite",actor,ZoneType.Hand);
        Card host=addCardToZone(spell,actor,hostZone);host.setSickness(false);
        SpellAbility action=host.getSpellAbilities().stream().filter(a->a.getApi()==ApiType.ChangeText).findFirst().orElseThrow();action.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
        return new Pod(game,actor,host,target,beneficiary,action);
    }
    private void castChosen(Pod p) {
        PlayerController original=p.actor.getController();
        p.actor.dangerouslySetController(new PlayerControllerAi(p.game,p.actor,p.actor.getLobbyPlayer()) {
            @Override public byte chooseColor(String title,SpellAbility action,ColorSet options) {
                return (options.getColor() & MagicColor.WHITE)!=0 ? MagicColor.WHITE : MagicColor.BLUE;
            }
            @Override public String chooseSomeType(String kind,SpellAbility action,Collection<String> valid,boolean optional) {
                if(kind.equals("Creature")) return valid.contains("Elf")?"Elf":"Goblin";
                return valid.contains("Forest")?"Forest":"Island";
            }
        });
        try {
            p.action.getTargets().add(p.target);assertTrue(p.action.isTargetNumberValid());
            assertTrue(ComputerUtilCost.canPayCost(p.action,p.actor,false));
            assertTrue(ComputerUtil.playStack(p.action,p.actor,p.game));settle(p.game);
        } finally { p.actor.dangerouslySetController(original); }
    }
    private void settle(Game game) {
        for(int i=0;i<20;i++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
            if(game.getStack().isEmpty())return;game.getStack().resolveStack();
        }
        throw new AssertionError("Native stack did not settle");
    }
    private record Pod(Game game,Player actor,Card host,Card target,Card beneficiary,SpellAbility action) {}
}
