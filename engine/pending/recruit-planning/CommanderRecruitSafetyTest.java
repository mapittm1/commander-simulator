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
public class CommanderRecruitSafetyTest extends AITest {

    @Test public void originalEmptyLibrary() { safety("EmptyLibrary",false); }
    @Test public void copiedEmptyLibrary() { safety("EmptyLibrary",true); }
    @Test public void originalConditionalSkip() { safety("ConditionalSkip",false); }
    @Test public void copiedConditionalSkip() { safety("ConditionalSkip",true); }
    @Test public void originalLaboratoryManiac() { safety("LaboratoryManiac",false); }
    @Test public void copiedLaboratoryManiac() { safety("LaboratoryManiac",true); }
    @Test public void originalPlatinumAngel() { safety("PlatinumAngel",false); }
    @Test public void copiedPlatinumAngel() { safety("PlatinumAngel",true); }
    @Test public void originalDrawProhibited() { safety("DrawProhibited",false); }
    @Test public void copiedDrawProhibited() { safety("DrawProhibited",true); }

    private void safety(String kind, boolean copied) {
        boolean highManaValue=kind.equals("ConditionalSkip");
        Scenario setup=scenario(true,copied,highManaValue,true);
        SpellAbility ability=setup.source.getFirstSpellAbility();
        ability.setActivatingPlayer(setup.actor);
        ability.getTargets().add(setup.game.getStack().peekAbility());
        if(kind.equals("DrawProhibited")) {
            addCardToZone("Spirit of the Labyrinth",setup.actor,ZoneType.Battlefield);
            setup.game.getAction().checkStateEffects(true);
            setup.actor.drawCards(1,ability,forge.game.ability.AbilityKey.newMap());
            assertFalse(setup.actor.canDraw(),"The printed draw limit must actually prohibit this draw");
        }
        for(Card card:new ArrayList<Card>(setup.actor.getCardsIn(ZoneType.Library)))
            setup.game.getAction().moveTo(ZoneType.Exile,card,ability,null);
        if(kind.equals("LaboratoryManiac")) addCardToZone("Laboratory Maniac",setup.actor,ZoneType.Battlefield);
        if(kind.equals("PlatinumAngel")) addCardToZone("Platinum Angel",setup.actor,ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);setup.game.copyLastState();
        assertEquals(setup.actor.getCardsIn(ZoneType.Library).size(),0);
        if(kind.equals("DrawProhibited")) {
            assertEquals(setup.actor.getNumDrawnThisTurn(),1);
            GameCopier probe=new GameCopier(setup.game).withStack(true);
            probe.makeCopy(null,setup.actor);
            Player projected=probe.find(setup.actor);
            assertEquals(projected.getNumDrawnThisTurn(),setup.actor.getNumDrawnThisTurn());
            assertEquals(projected.getNumDrawnLastTurn(),setup.actor.getNumDrawnLastTurn());
            assertEquals(projected.numDrawnThisDrawStep(),setup.actor.numDrawnThisDrawStep());
            assertFalse(projected.canDraw(),"A copied draw limit must remain exhausted");
            assertEquals(projected.getCardsIn(ZoneType.Hand).size(),setup.actor.getCardsIn(ZoneType.Hand).size());
        }
        boolean safe=!kind.equals("EmptyLibrary");
        AiAbilityDecision decision=SpellApiToAi.Converter.get(ability).canPlayWithSubs(setup.actor,ability);
        assertEquals(decision.willingToPlay(),safe,"Native outcome must account for "+kind);
        assertFalse(setup.actor.hasLost(),"Planning must not mutate the real outcome");
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertEquals(setup.game.getStack().size(),1);
        assertTrue(ComputerUtil.playStack(ability,setup.actor,setup.game));
        assertEquals(ability.getPayingMana().size(),3);settle(setup.game);
        assertEquals(setup.actor.hasLost(),!safe,"Paid replay must witness the safety premise for "+kind);
        if(kind.equals("LaboratoryManiac")) {
            assertTrue(setup.game.isGameOver());
            assertTrue(setup.actor.getOutcome().hasWon(),"The replacement must actually win");
        }
        if(kind.equals("ConditionalSkip")) assertEquals(setup.actor.getCreaturesInPlay().size(),0);
        independent(setup);
    }

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
        Scenario setup=scenario(recruit,copied,false,false);
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
