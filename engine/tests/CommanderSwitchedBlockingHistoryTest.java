package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameSnapshot;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid printed switches must retain newly assigned blockers after their deaths. */
public class CommanderSwitchedBlockingHistoryTest extends AITest {
    @Test public void originalJarkeldState() { check("jarkeld", 0, false); }
    @Test public void originalJarkeldDamage() { check("jarkeld", 0, true); }
    @Test public void originalSorrowsPathState() { check("path", 0, false); }
    @Test public void originalSorrowsPathDamage() { check("path", 0, true); }
    @Test public void copiedJarkeldState() { check("jarkeld", 1, false); }
    @Test public void copiedJarkeldDamage() { check("jarkeld", 1, true); }
    @Test public void copiedSorrowsPathState() { check("path", 1, false); }
    @Test public void copiedSorrowsPathDamage() { check("path", 1, true); }
    @Test public void commonJarkeldState() { check("jarkeld", 2, false); }
    @Test public void commonJarkeldDamage() { check("jarkeld", 2, true); }
    @Test public void commonSorrowsPathState() { check("path", 2, false); }
    @Test public void commonSorrowsPathDamage() { check("path", 2, true); }

    private void check(String mode, int world, boolean damageOnly) {
        Setup setup=setup(mode,world);Combat combat=setup.game.getCombat();
        SpellAbility action=setup.source.getSpellAbilities().stream()
                .filter(sa -> sa.getApi()==ApiType.RemoveFromCombat).findFirst().orElseThrow();
        action.setActivatingPlayer(setup.actor);assertTrue(action.canTarget(setup.target));
        action.getTargets().add(setup.target);assertTrue(action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(action,setup.actor,false));
        fixedChoice(setup.game,setup.actor,setup.big,() -> {
            assertTrue(ComputerUtil.playStack(action,setup.actor,setup.game));
            assertEquals(action.getPayingMana().size(),0);assertTrue(setup.source.isTapped());
            settle(setup.game);
        });
        assertFalse(combat.isBlocking(setup.target,setup.small));
        assertTrue(combat.isBlocking(setup.target,setup.big));assertTrue(combat.isBlocked(setup.big));
        assign(setup.game,setup.actor,setup.target,setup.small);
        assertTrue(combat.isBlocking(setup.target,setup.small));assertTrue(combat.getBlockers(setup.big).isEmpty());
        boolean shouldRemainBlocked=true;
        if (!damageOnly) assertTrue(combat.isBlocked(setup.big),
                "The creature assigned by the paid switch still counts after it died");
        System.out.println("BLOCK_HISTORY "+mode+" world="+world+" damageOnly="+damageOnly
                +" blockedFlag="+combat.isBlocked(setup.big));
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,setup.enemy,false,3);
        assertTrue(combat.assignCombatDamage(false));combat.dealAssignedDamage();settle(setup.game);
        assertEquals(setup.actor.hasLost(),!shouldRemainBlocked);
        if (shouldRemainBlocked) assertEquals(setup.actor.getLife(),3,"Both attackers remain blocked");
        if (world != 0) {
            assertEquals(setup.originalActor.getLife(),3);assertFalse(setup.originalActor.hasLost());
            assertFalse(setup.originalSource.isTapped());assertTrue(setup.original.getStack().isEmpty());
            assertTrue(setup.original.getCombat().isBlocking(setup.originalTarget,setup.originalSmall));
            assertEquals(setup.original.getCombat().getBlockers(setup.originalBig).size(),0);
            assertEquals(setup.originalActor.getSpellsCastThisTurn(),2);
        }
    }

    private Setup setup(String mode, int world) {
        List<RegisteredPlayer> seats=new ArrayList<>();
        for (int i=0;i<4;i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("blocking-history-"+i,null)));
        GameRules rules=new GameRules(GameType.Commander);
        Game game=new Game(seats,rules,new Match(rules,seats,"Blocking history"));
        game.setNoGUIUser();game.setAge(GameStage.Play);
        Player enemy=game.getPlayers().get(0),actor=game.getPlayers().get(1);actor.setLife(3,null);
        for (Player player:game.getPlayers()) {
            for (int i=0;i<12;i++) addCardToZone("Shock",player,ZoneType.Library);
        }
        for (int i=0;i<3;i++) addCardToZone("Mountain",actor,ZoneType.Battlefield);
        Card source=addCardToZone("Balduvian Warlord",actor,ZoneType.Battlefield);source.setSickness(false);
        Card target=addCardToZone("Runeclaw Bear",actor,ZoneType.Battlefield);
        Card earlier=addCardToZone("Grizzly Bears",actor,ZoneType.Battlefield);
        Card big=addCardToZone("Craw Wurm",enemy,ZoneType.Battlefield);
        Card small=addCardToZone("Runeclaw Bear",enemy,ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,enemy,3);
        Combat combat=new Combat(enemy);game.getPhaseHandler().setCombat(combat);
        for (Card attacker:List.of(big,small)) {
            attacker.setSickness(false);assertTrue(CombatUtil.canAttack(attacker,actor));
            combat.addAttacker(attacker,actor);attacker.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game,attacker,combat,true);combat.setBlocked(attacker,false);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,enemy,false,3);
        game.getPhaseHandler().setPriority(actor);
        assign(game,actor,target,big);assign(game,actor,earlier,small);
        Player switchOwner=mode.equals("path") ? game.getPlayers().get(2) : actor;
        Card switcher=addCardToZone(mode.equals("path") ? "Sorrow's Path" : "General Jarkeld",switchOwner,ZoneType.Battlefield);
        switcher.setSickness(false);
        SpellAbility swap=switcher.getSpellAbilities().stream().filter(sa -> sa.getApi()==ApiType.SwitchBlock).findFirst().orElseThrow();
        swap.setActivatingPlayer(switchOwner);game.getPhaseHandler().setPriority(switchOwner);
        Card first=mode.equals("path") ? target : big,second=mode.equals("path") ? earlier : small;
        assertTrue(swap.canTarget(first));assertTrue(swap.canTarget(second));
        swap.getTargets().add(first);swap.getTargets().add(second);assertTrue(swap.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(swap,switchOwner,false));
        assertTrue(ComputerUtil.playStack(swap,switchOwner,game));assertTrue(switcher.isTapped());
        assertEquals(swap.getPayingMana().size(),0);settle(game);
        assertTrue(combat.isBlocking(target,small));assertTrue(combat.isBlocking(earlier,big));
        addCardToZone("Mountain",enemy,ZoneType.Battlefield);
        SpellAbility shock=addCardToZone("Shock",enemy,ZoneType.Hand).getFirstSpellAbility();
        shock.setActivatingPlayer(enemy);assertTrue(shock.canTarget(earlier));shock.getTargets().add(earlier);
        game.getPhaseHandler().setPriority(enemy);assertTrue(shock.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(shock,enemy,false));
        assertTrue(ComputerUtil.playStack(shock,enemy,game));assertEquals(shock.getPayingMana().size(),1);
        settle(game);assertTrue(game.getCardState(earlier).isInZone(ZoneType.Graveyard));
        assertTrue(combat.isBlocked(big));assertTrue(combat.getBlockers(big).isEmpty());
        game.getPhaseHandler().setPriority(actor);game.copyLastState();
        assertTrue(combat.isBlocking(target,small));assertEquals(actor.getSpellsCastThisTurn(),2);
        Game original=game;Player originalActor=actor;Card originalSource=source,originalTarget=target,originalBig=big,originalSmall=small;
        if (world == 1) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);
            actor=copier.find(actor);enemy=copier.find(enemy);source=copier.find(source);
            target=copier.find(target);big=copier.find(big);small=copier.find(small);
        } else if (world == 2) {
            int actorId=actor.getId(),enemyId=enemy.getId();
            game=new GameSnapshot(game).makeCopy();game.setNoGUIUser();
            actor=game.getPlayer(actorId);enemy=game.getPlayer(enemyId);
            source=game.getCardState(source);target=game.getCardState(target);
            big=game.getCardState(big);small=game.getCardState(small);
            assertSame(source.getGame(),game);assertSame(target.getGame(),game);
        }
        assertTrue(game.getCombat().isBlocked(big));assertTrue(game.getCombat().isBlocking(target,small));
        return new Setup(game,actor,enemy,source,target,big,small,original,originalActor,originalSource,originalTarget,originalBig,originalSmall);
    }

    private void assign(Game game,Player actor,Card blocker,Card attacker) {
        SpellAbility orders=addCardToZone("False Orders",actor,ZoneType.Hand).getFirstSpellAbility();
        orders.setActivatingPlayer(actor);assertTrue(orders.canTarget(blocker));orders.getTargets().add(blocker);
        assertTrue(orders.canPlay());assertTrue(ComputerUtilCost.canPayCost(orders,actor,false));
        fixedChoice(game,actor,attacker,() -> {
            assertTrue(ComputerUtil.playStack(orders,actor,game));assertEquals(orders.getPayingMana().size(),1);
            settle(game);
        });
        assertTrue(game.getCombat().isBlocking(blocker,attacker));assertTrue(game.getCombat().isBlocked(attacker));
    }

    private void fixedChoice(Game game,Player actor,Card chosen,Runnable action) {
        int[] observed={0};
        PlayerControllerAi controller=new PlayerControllerAi(game,actor,actor.getLobbyPlayer()) {
            @Override public CardCollectionView chooseCardsForEffect(CardCollectionView options,SpellAbility ability,
                    String title,int min,int max,boolean optional,Map<String,Object> params) {
                assertEquals(ability.getApi(),ApiType.ChooseCard);assertTrue(options.contains(chosen));
                assertEquals(max,1);assertTrue(min==0 || min==1);observed[0]++;
                CardCollection result=new CardCollection();result.add(chosen);return result;
            }
        };
        actor.runWithController(action,controller);assertEquals(observed[0],1);
    }

    private void settle(Game game) {
        for (int guard=0;guard<40;guard++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Blocking history fixture did not settle");
    }

    private record Setup(Game game,Player actor,Player enemy,Card source,Card target,Card big,Card small,
            Game original,Player originalActor,Card originalSource,Card originalTarget,Card originalBig,Card originalSmall) { }
}
