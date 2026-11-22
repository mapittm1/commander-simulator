package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Composition witnesses: choices made through parent targeting and modal spell chains. */
public class CommanderCompositeModeCoverageTest extends AITest {
    @Test public void fullModeWhimReplaysItsColorChoice() { verify("Whim of Volrath"); }
    @Test public void fullModeMindBendReplaysItsColorChoice() { verify("Mind Bend"); }
    @Test public void fullModeSpectralShiftReplaysItsColorChoice() { verify("Spectral Shift"); }

    @Test public void copiedComposedSpellKeepsItsPendingWordReplay() { verify("Spectral Shift", true); }
    @Test public void suppliedEntwinedCastReplaysBothModesAndAllFourWords() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat"+i,null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats,rules,new Match(rules,seats,"Entwined text changes"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0), enemy=game.getPlayers().get(1);
        enemy.setLife(8,null);game.getPhaseHandler().devModeSet(PhaseType.MAIN1,actor,2);
        for(Player player:game.getPlayers())for(int i=0;i<8;i++)addCardToZone("Forest",player,ZoneType.Library);
        for(int i=0;i<4;i++)addCardToZone("Island",actor,ZoneType.Battlefield);
        addCardToZone("Island",enemy,ZoneType.Battlefield);
        addCardToZone("Honor of the Pure",actor,ZoneType.Battlefield);
        Card wall=addCardToZone("Coral Merfolk",actor,ZoneType.Battlefield);wall.setSickness(false);
        Card dryad=addCardToZone("Shanodin Dryads",actor,ZoneType.Battlefield);dryad.setSickness(false);
        dryad.setCounters(forge.game.card.CounterEnumType.P1P1,7);
        Card blocker=addCardToZone("Grizzly Bears",enemy,ZoneType.Battlefield);
        addCardToZone("Omniscience",actor,ZoneType.Hand);addCardToZone("Enter the Infinite",actor,ZoneType.Hand);
        Card host=addCardToZone("Spectral Shift",actor,ZoneType.Hand);
        // Use the active damage window so the color mode has an immediate paid benefit.
        Card combatBlocker=addCardToZone("Memnite",enemy,ZoneType.Battlefield);
        Combat combat=new Combat(actor);combat.addAttacker(wall,enemy);combat.addBlocker(wall,combatBlocker);
        combat.getBandOfAttacker(wall).setBlocked(true);wall.setTapped(true);
        wall.addBlockedByThisTurn(CardCopyService.getLKICopy(combatBlocker));combatBlocker.addBlockedThisTurn(CardCopyService.getLKICopy(wall));
        combat.orderBlockersForDamageAssignment(wall,combat.getBlockers(wall));combat.orderAttackersForDamageAssignment(combatBlocker);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,actor,2);game.getPhaseHandler().setCombat(combat);

        game.getAction().checkStateEffects(true);game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
        List<Byte> colorWords = new ArrayList<>();
        List<String> typeWords = new ArrayList<>();
        // Supply the entwined cast form; this isolates replay from optional-cost policy.
        actor.dangerouslySetController(new PlayerControllerAi(game,actor,actor.getLobbyPlayer()) {
            @Override public byte chooseColor(String prompt,SpellAbility action,forge.card.ColorSet options) {
                byte choice=super.chooseColor(prompt,action,options);
                if(action.getApi()==forge.game.ability.ApiType.ChangeText && game.getStack().isResolving()) colorWords.add(choice);
                return choice;
            }
            @Override public String chooseSomeType(String kind,SpellAbility action,java.util.Collection<String> options,boolean optional) {
                String choice=super.chooseSomeType(kind,action,options,optional);
                if(action.getApi()==forge.game.ability.ApiType.ChangeText && game.getStack().isResolving()) typeWords.add(choice);
                return choice;
            }
            @Override public List<forge.game.spellability.OptionalCostValue> chooseOptionalCosts(
                    SpellAbility ability, List<forge.game.spellability.OptionalCostValue> costs) {
                return costs.stream().filter(cost -> cost.getType() == forge.game.spellability.OptionalCost.Entwine).toList();
            }
        });
        AiController ai=((PlayerControllerAi)actor.getController()).getAi();ai.setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> choices=ai.chooseSpellAbilityToPlay();
        assertTrue(choices!=null&&!choices.isEmpty(),"Expected a useful entwined action");
        SpellAbility selected=choices.stream().filter(a->a.getHostCard().getId()==host.getId()).findFirst().orElseThrow();
        assertTrue(selected.isOptionalCostPaid(forge.game.spellability.OptionalCost.Entwine));
        assertTrue(ComputerUtil.playStack(selected,actor,game));settle(game);
        assertEquals(wall.getNetPower(),3,"The color mode must replay White to Blue");
        assertEquals(colorWords.size(),2,"Both native color prompts must run");
        assertEquals(typeWords.size(),2,"Both native type prompts must run");
        assertTrue(!colorWords.get(0).equals(colorWords.get(1)));
        assertTrue(!typeWords.get(0).equals(typeWords.get(1)));
        assertTrue(game.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card ->
                typeWords.get(1).equals(card.getChangedTextTypeWords().get(typeWords.get(0)))),
                "The land-type mode must resolve its chosen replacement on a real target");
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(),4L);
    }

    @Test public void heuristicWhimReplaysItsColorChoice() { verify("Whim of Volrath", false, null); }
    @Test public void hybridWhimReplaysItsColorChoice() { verify("Whim of Volrath", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void heuristicMindBendReplaysItsColorChoice() { verify("Mind Bend", false, null); }
    @Test public void hybridMindBendReplaysItsColorChoice() { verify("Mind Bend", false, AIOption.USE_HYBRID_SIMULATION); }
    @Test public void heuristicSpectralShiftReplaysItsColorChoice() { verify("Spectral Shift", false, null); }
    @Test public void hybridSpectralShiftReplaysItsColorChoice() { verify("Spectral Shift", false, AIOption.USE_HYBRID_SIMULATION); }

    private void verify(String name) { verify(name, false); }
    private void verify(String name, boolean copiedPending) { verify(name, copiedPending, AIOption.USE_FULL_SIMULATION); }
    private void verify(String name, boolean copiedPending, AIOption mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat"+i,null)));
        GameRules rules=new GameRules(GameType.Commander);
        Game game=new Game(seats,rules,new Match(rules,seats,"Text composition"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0), enemy=game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,actor,2);
        for(Player player:game.getPlayers())for(int i=0;i<8;i++)addCardToZone("Forest",player,ZoneType.Library);
        addCardToZone("Island",actor,ZoneType.Battlefield);addCardToZone("Island",actor,ZoneType.Battlefield);
        addCardToZone("Honor of the Pure",actor,ZoneType.Battlefield);
        Card attacker=addCardToZone("Coral Merfolk",actor,ZoneType.Battlefield);
        Card blocker=addCardToZone("Memnite",enemy,ZoneType.Battlefield);
        attacker.setTapped(true);attacker.setSickness(false);
        Combat combat=new Combat(actor);combat.addAttacker(attacker,enemy);combat.addBlocker(attacker,blocker);
        combat.getBandOfAttacker(attacker).setBlocked(true);
        attacker.addBlockedByThisTurn(CardCopyService.getLKICopy(blocker));blocker.addBlockedThisTurn(CardCopyService.getLKICopy(attacker));
        combat.orderBlockersForDamageAssignment(attacker,combat.getBlockers(attacker));combat.orderAttackersForDamageAssignment(blocker);
        game.getPhaseHandler().setCombat(combat);
        addCardToZone("Omniscience",actor,ZoneType.Hand);addCardToZone("Enter the Infinite",actor,ZoneType.Hand);
        Card host=addCardToZone(name,actor,ZoneType.Hand);
        game.getAction().checkStateEffects(true);game.getTriggerHandler().resetActiveTriggers();game.copyLastState();
        AiController ai=((PlayerControllerAi)actor.getController()).getAi();ai.setUseSimulation(mode);
        List<SpellAbility> choices=ai.chooseSpellAbilityToPlay();
        assertTrue(choices!=null&&!choices.isEmpty(),"Expected a useful composed text-change action");
        SpellAbility selected=choices.stream().filter(a->a.getHostCard().getId()==host.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected,actor,game));
        if (copiedPending) {
            forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty()); settle(copy);
            assertEquals(copier.find(attacker).getNetPower(), 3);
            assertEquals(attacker.getNetPower(), 2);
            assertEquals(game.getStack().size(), 1);
        }
        settle(game);
        assertTrue(game.getStack().isEmpty());assertEquals(attacker.getNetPower(),3,"The selected child effect must replay useful words");
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,actor,false,2);
        assertTrue(combat.assignCombatDamage(false));combat.dealAssignedDamage();game.getAction().checkStateEffects(true);
        assertTrue(attacker.isInZone(ZoneType.Battlefield));
    }
    private void settle(Game game) {
        for(int i=0;i<20;i++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
            if(game.getStack().isEmpty())return;game.getStack().resolveStack();
        }
        throw new AssertionError("Native composed stack did not settle");
    }
}
