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

/** Paid counter/recruit resolution and actual planning are separate supplied contracts. */
public class CommanderRecruitPolicyPlanningTest extends AITest {
    @Test public void hybridChoosesCounterWithRecruit() { choice(true,AIOption.USE_HYBRID_SIMULATION,false); }
    @Test public void fullChoosesCounterWithRecruit() { choice(true,AIOption.USE_FULL_SIMULATION,false); }
    @Test public void copiedHybridChoosesCounterWithRecruit() { choice(true,AIOption.USE_HYBRID_SIMULATION,true); }
    @Test public void copiedFullChoosesCounterWithRecruit() { choice(true,AIOption.USE_FULL_SIMULATION,true); }
    @Test public void hybridChoosesOrdinaryCounterControl() { choice(false,AIOption.USE_HYBRID_SIMULATION,false); }
    @Test public void fullChoosesOrdinaryCounterControl() { choice(false,AIOption.USE_FULL_SIMULATION,false); }
    @Test public void copiedHybridChoosesOrdinaryCounterControl() { choice(false,AIOption.USE_HYBRID_SIMULATION,true); }
    @Test public void copiedFullChoosesOrdinaryCounterControl() { choice(false,AIOption.USE_FULL_SIMULATION,true); }
    @Test public void paidCounterWithRecruitResolvesDrawDiscardAndSoldier() { paid(false,false); }
    @Test public void copiedPaidCounterWithRecruitResolvesDrawDiscardAndSoldier() { paid(true,false); }
    @Test public void higherManaValueCounterDoesNotRecruit() { paid(false,true); }
    @Test public void copiedHigherManaValueCounterDoesNotRecruit() { paid(true,true); }
    @Test public void counterWithRecruitHasSupportedAdmission() { admission(true,false); }
    @Test public void copiedCounterWithRecruitHasSupportedAdmission() { admission(true,true); }
    @Test public void ordinaryCounterHasSupportedAdmission() { admission(false,false); }
    @Test public void copiedOrdinaryCounterHasSupportedAdmission() { admission(false,true); }

    private Scenario scenario(boolean recruit,boolean copied,boolean highManaValue,boolean willingPolicy) {
        List<RegisteredPlayer> seats=new ArrayList<>();
        for(int index=0;index<4;index++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("recruit-seat-"+index,null)));
        GameRules rules=new GameRules(GameType.Commander);
        Game game=new Game(seats,rules,new Match(rules,seats,"Recruit counter planning"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player enemy=game.getPlayers().get(0),actor=game.getPlayers().get(1);
        for(Player player:game.getPlayers()) for(int index=0;index<8;index++) addCardToZone("Shock",player,ZoneType.Library);
        for(int index=0;index<3;index++) { addCardToZone("Island",actor,ZoneType.Battlefield);addCardToZone("Mountain",enemy,ZoneType.Battlefield); }
        actor.setLife(2,null);
        if(willingPolicy) {
            AiProfileUtil.loadAllProfiles(forge.localinstance.properties.ForgeConstants.AI_PROFILE_DIR);
            ((LobbyPlayerAi)actor.getLobbyPlayer()).setAiProfile("Cautious");
            assertEquals(AiProfileUtil.getIntProperty(actor,AiProps.MIN_SPELL_CMC_TO_COUNTER),2);
            assertEquals(AiProfileUtil.getIntProperty(actor,AiProps.CHANCE_TO_COUNTER_CMC_1),0);
            // The existing profile's below-threshold damage-spell override is deterministic.
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2,enemy,2);
        Card source=addCardToZone(recruit?"Sound the Trumpets":"Counterspell",actor,ZoneType.Hand);
        addCardToZone("Sol Ring",actor,ZoneType.Hand);
        SpellAbility threat=addCardToZone(highManaValue?"Exquisite Firecraft":"Shock",enemy,ZoneType.Hand).getFirstSpellAbility();
        threat.setActivatingPlayer(enemy);threat.getTargets().add(actor);
        assertTrue(ComputerUtil.playStack(threat,enemy,game));assertEquals(threat.getPayingMana().size(),highManaValue?3:1);
        game.getPhaseHandler().setPriority(actor);game.copyLastState();
        Game original=game;Player originalActor=actor;
        if(copied) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);actor=copier.find(actor);source=copier.find(source);
        }
        assertTrue(actor.getLandsInPlay().stream().noneMatch(Card::isTapped));assertEquals(game.getStack().size(),1);
        return new Scenario(game,actor,source,original,originalActor);
    }
    private void choice(boolean recruit,AIOption mode,boolean copied) {
        Scenario setup=scenario(recruit,copied,false,true);
        PlayerControllerAi controller=(PlayerControllerAi)setup.actor.getController();controller.getAi().setUseSimulation(mode);
        List<SpellAbility> choices=controller.getAi().chooseSpellAbilityToPlay();
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));assertEquals(setup.game.getStack().size(),1);
        System.out.println("RECRUIT_CHOICE recruit="+recruit+"; mode="+mode+"; copied="+copied+"; choices="+choices);
        assertNotNull(choices,"An affordable legal counter can prevent the supplied lethal spell");
        SpellAbility selected=choices.stream().filter(ability->ability.getHostCard().getId()==setup.source.getId()).findFirst().orElse(null);
        assertNotNull(selected);assertTrue(ComputerUtil.playStack(selected,setup.actor,setup.game));assertEquals(selected.getPayingMana().size(),recruit?3:2);
        settle(setup.game);assertEquals(setup.actor.getLife(),2);independent(setup);
    }
    private void admission(boolean recruit,boolean copied) {
        Scenario setup=scenario(recruit,copied,false,true);SpellAbility ability=setup.source.getFirstSpellAbility();ability.setActivatingPlayer(setup.actor);
        assertTrue(ability.canTarget(setup.game.getStack().peekAbility()));ability.getTargets().add(setup.game.getStack().peekAbility());
        assertTrue(ComputerUtilCost.canPayCost(ability,setup.actor,false));
        AiAbilityDecision decision=SpellApiToAi.Converter.get(ability).canPlayWithSubs(setup.actor,ability);
        System.out.println("RECRUIT_ADMISSION recruit="+recruit+"; copied="+copied+"; decision="+decision.decision());
        assertNotEquals(decision.decision(),AiPlayDecision.CantPlayAi,"A legal counter and recruit follow-up needs supported admission or a supported policy decline");
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));assertEquals(setup.game.getStack().size(),1);independent(setup);
    }
    private void paid(boolean copied,boolean highManaValue) {
        Scenario setup=scenario(true,copied,highManaValue,false);int library=setup.actor.getCardsIn(ZoneType.Library).size();
        SpellAbility ability=setup.source.getFirstSpellAbility();ability.setActivatingPlayer(setup.actor);ability.getTargets().add(setup.game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(ability,setup.actor,setup.game));assertEquals(ability.getPayingMana().size(),3);settle(setup.game);
        assertEquals(setup.actor.getLife(),2);assertEquals(setup.actor.getCardsIn(ZoneType.Library).size(),library-(highManaValue?0:1));
        assertEquals(setup.actor.getCardsIn(ZoneType.Hand).size(),1);assertEquals(setup.actor.getCardsIn(ZoneType.Graveyard).size(),highManaValue?1:2);
        List<Card> tokens=setup.actor.getCreaturesInPlay().stream().filter(Card::isToken).toList();assertEquals(tokens.size(),highManaValue?0:1);
        if(!highManaValue) { assertTrue(tokens.get(0).getType().hasSubtype("Human"));assertTrue(tokens.get(0).getType().hasSubtype("Soldier"));assertEquals(tokens.get(0).getNetPower(),1);assertEquals(tokens.get(0).getNetToughness(),1); }
        independent(setup);
    }
    private void settle(Game game) {
        for(int guard=0;guard<40;guard++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
            if(game.getStack().isEmpty())return;game.getStack().resolveStack();
        }
        fail("Recruit fixture did not settle");
    }
    private void independent(Scenario setup) {
        if(setup.original==setup.game)return;
        assertEquals(setup.originalActor.getLife(),2);assertEquals(setup.original.getStack().size(),1);
        assertTrue(setup.originalActor.getLandsInPlay().stream().noneMatch(Card::isTapped));assertEquals(setup.originalActor.getCardsIn(ZoneType.Hand).size(),2);
    }
    private record Scenario(Game game,Player actor,Card source,Game original,Player originalActor) { }
}
