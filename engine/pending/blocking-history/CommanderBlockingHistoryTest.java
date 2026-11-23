package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
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

/** Establish blocker history through paid printed effects, not manually forged history fields. */
public class CommanderBlockingHistoryTest extends AITest {
    @Test public void originalOneEverState() { check("one", false, false); }
    @Test public void originalOneEverDamage() { check("one", false, true); }
    @Test public void originalOtherPresentState() { check("present", false, false); }
    @Test public void originalOtherPresentDamage() { check("present", false, true); }
    @Test public void originalOtherDiedState() { check("died", false, false); }
    @Test public void originalOtherDiedDamage() { check("died", false, true); }
    @Test public void copiedOneEverState() { check("one", true, false); }
    @Test public void copiedOneEverDamage() { check("one", true, true); }
    @Test public void copiedOtherPresentState() { check("present", true, false); }
    @Test public void copiedOtherPresentDamage() { check("present", true, true); }
    @Test public void copiedOtherDiedState() { check("died", true, false); }
    @Test public void copiedOtherDiedDamage() { check("died", true, true); }

    private void check(String mode, boolean copied, boolean damageOnly) {
        Setup setup=setup(mode,copied);Combat combat=setup.game.getCombat();
        SpellAbility action=setup.source.getSpellAbilities().stream()
                .filter(sa -> sa.getApi()==ApiType.RemoveFromCombat).findFirst().orElseThrow();
        action.setActivatingPlayer(setup.actor);assertTrue(action.canTarget(setup.target));
        action.getTargets().add(setup.target);assertTrue(action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(action,setup.actor,false));
        fixedChoice(setup.game,setup.actor,setup.small,() -> {
            assertTrue(ComputerUtil.playStack(action,setup.actor,setup.game));
            assertEquals(action.getPayingMana().size(),0);assertTrue(setup.source.isTapped());
            settle(setup.game);
        });
        assertFalse(combat.isBlocking(setup.target,setup.big));
        assertTrue(combat.isBlocking(setup.target,setup.small));assertTrue(combat.isBlocked(setup.small));
        assertSame(combat.getBlockers(setup.small).get(0),setup.target);
        boolean shouldRemainBlocked=!mode.equals("one");
        if (!damageOnly) assertEquals(combat.isBlocked(setup.big),shouldRemainBlocked,
                "Another creature blocked this attacker earlier in this combat, even if it died");
        System.out.println("BLOCK_HISTORY "+mode+" copied="+copied+" damageOnly="+damageOnly
                +" blockedFlag="+combat.isBlocked(setup.big));
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,setup.enemy,false,3);
        assertTrue(combat.assignCombatDamage(false));combat.dealAssignedDamage();settle(setup.game);
        assertEquals(setup.actor.hasLost(),!shouldRemainBlocked);
        if (shouldRemainBlocked) assertEquals(setup.actor.getLife(),3,"Both attackers remain blocked");
        if (copied) {
            assertEquals(setup.originalActor.getLife(),3);assertFalse(setup.originalActor.hasLost());
            assertFalse(setup.originalSource.isTapped());assertTrue(setup.original.getStack().isEmpty());
            assertTrue(setup.original.getCombat().isBlocking(setup.originalTarget,setup.originalBig));
            assertEquals(setup.original.getCombat().getBlockers(setup.originalBig).size(),mode.equals("present") ? 2 : 1);
            assertEquals(setup.originalActor.getSpellsCastThisTurn(),mode.equals("one") ? 1 : 2);
        }
    }

    private Setup setup(String mode, boolean copied) {
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
        for (int i=0;i<2;i++) addCardToZone("Mountain",actor,ZoneType.Battlefield);
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
        assign(game,actor,target,big);
        if (!mode.equals("one")) assign(game,actor,earlier,big);
        assertTrue(combat.isBlocked(big));assertFalse(combat.isBlocked(small));
        assertEquals(combat.getBlockers(big).size(),mode.equals("one") ? 1 : 2);
        if (mode.equals("died")) {
            addCardToZone("Mountain",enemy,ZoneType.Battlefield);
            SpellAbility shock=addCardToZone("Shock",enemy,ZoneType.Hand).getFirstSpellAbility();
            shock.setActivatingPlayer(enemy);assertTrue(shock.canTarget(earlier));shock.getTargets().add(earlier);
            game.getPhaseHandler().setPriority(enemy);assertTrue(shock.canPlay());
            assertTrue(ComputerUtilCost.canPayCost(shock,enemy,false));
            assertTrue(ComputerUtil.playStack(shock,enemy,game));assertEquals(shock.getPayingMana().size(),1);
            settle(game);assertTrue(game.getCardState(earlier).isInZone(ZoneType.Graveyard));
            assertTrue(combat.isBlocked(big));assertEquals(combat.getBlockers(big).size(),1);
        }
        game.getPhaseHandler().setPriority(actor);game.copyLastState();
        assertTrue(combat.isBlocking(target,big));assertEquals(actor.getSpellsCastThisTurn(),mode.equals("one") ? 1 : 2);
        Game original=game;Player originalActor=actor;Card originalSource=source,originalTarget=target,originalBig=big;
        if (copied) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);
            actor=copier.find(actor);enemy=copier.find(enemy);source=copier.find(source);
            target=copier.find(target);big=copier.find(big);small=copier.find(small);
        }
        assertTrue(game.getCombat().isBlocked(big));assertTrue(game.getCombat().isBlocking(target,big));
        return new Setup(game,actor,enemy,source,target,big,small,original,originalActor,originalSource,originalTarget,originalBig);
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
            Game original,Player originalActor,Card originalSource,Card originalTarget,Card originalBig) { }
}
