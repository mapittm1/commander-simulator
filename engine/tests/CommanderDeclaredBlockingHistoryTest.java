package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
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

/** Confirmed native declarations must record the same history as paid blocking effects. */
public class CommanderDeclaredBlockingHistoryTest extends AITest {
    @Test public void originalOneDeclaredState() { check("one", 0, false); }
    @Test public void originalOneDeclaredDamage() { check("one", 0, true); }
    @Test public void originalTwoDeclaredPresentState() { check("present", 0, false); }
    @Test public void originalTwoDeclaredPresentDamage() { check("present", 0, true); }
    @Test public void originalOtherDeclaredDiedState() { check("died", 0, false); }
    @Test public void originalOtherDeclaredDiedDamage() { check("died", 0, true); }
    @Test public void copiedOneDeclaredState() { check("one", 1, false); }
    @Test public void copiedOneDeclaredDamage() { check("one", 1, true); }
    @Test public void copiedTwoDeclaredPresentState() { check("present", 1, false); }
    @Test public void copiedTwoDeclaredPresentDamage() { check("present", 1, true); }
    @Test public void copiedOtherDeclaredDiedState() { check("died", 1, false); }
    @Test public void copiedOtherDeclaredDiedDamage() { check("died", 1, true); }
    @Test public void commonOneDeclaredState() { check("one", 2, false); }
    @Test public void commonOneDeclaredDamage() { check("one", 2, true); }
    @Test public void commonTwoDeclaredPresentState() { check("present", 2, false); }
    @Test public void commonTwoDeclaredPresentDamage() { check("present", 2, true); }
    @Test public void commonOtherDeclaredDiedState() { check("died", 2, false); }
    @Test public void commonOtherDeclaredDiedDamage() { check("died", 2, true); }

    private void check(String mode, int world, boolean damageOnly) {
        Setup setup=setup(mode,world);Combat combat=setup.game.getCombat();
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
        boolean shouldRemainBlocked=!mode.equals("one") && !mode.equals("fresh");
        if (!damageOnly) assertEquals(combat.isBlocked(setup.big),shouldRemainBlocked,
                "Another creature blocked this attacker earlier in this combat, even if it died");
        System.out.println("BLOCK_HISTORY "+mode+" world="+world+" damageOnly="+damageOnly
                +" blockedFlag="+combat.isBlocked(setup.big));
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,setup.enemy,false,3);
        assertTrue(combat.assignCombatDamage(false));combat.dealAssignedDamage();settle(setup.game);
        assertEquals(setup.actor.hasLost(),!shouldRemainBlocked);
        if (shouldRemainBlocked) assertEquals(setup.actor.getLife(),3,"Both attackers remain blocked");
        if (world != 0) {
            assertEquals(setup.originalActor.getLife(),3);assertFalse(setup.originalActor.hasLost());
            assertFalse(setup.originalSource.isTapped());assertTrue(setup.original.getStack().isEmpty());
            assertTrue(setup.original.getCombat().isBlocking(setup.originalTarget,setup.originalBig));
            assertEquals(setup.original.getCombat().getBlockers(setup.originalBig).size(),mode.equals("present") ? 2 : 1);
            assertEquals(setup.originalActor.getSpellsCastThisTurn(),spellCount(mode));
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
        assertTrue(CombatUtil.canBlock(big,target,combat));combat.addBlocker(big,target);
        if (!mode.equals("one")) {
            assertTrue(CombatUtil.canBlock(big,earlier,combat));combat.addBlocker(big,earlier);
        }
        // Native commit hook documented for use after the declaration action is finalized.
        combat.fireTriggersForUnblockedAttackers(game);
        game.getPhaseHandler().setPriority(actor);
        assertTrue(combat.isBlocked(big));assertFalse(combat.isBlocked(small));
        assertEquals(combat.getBlockers(big).size(),mode.equals("one") || mode.equals("blink") ? 1 : 2);
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
        if (mode.equals("fresh")) {
            Combat previous=combat;previous.endCombat();
            assertTrue(previous.getAttackers().isEmpty());
            game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS,enemy,false,3);
            combat=new Combat(enemy);game.getPhaseHandler().setCombat(combat);
            for (Card attacker:List.of(big,small)) {
                attacker.setTapped(false);assertTrue(CombatUtil.canAttack(attacker,actor));
                combat.addAttacker(attacker,actor);attacker.setTapped(true);
                CombatUtil.checkDeclaredAttacker(game,attacker,combat,true);combat.setBlocked(attacker,false);
            }
            game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,enemy,false,3);
            game.getPhaseHandler().setPriority(actor);assign(game,actor,target,big);
            assertNotSame(combat,previous);assertEquals(combat.getBlockers(big).size(),1);
        } else if (mode.equals("blink")) {
            int physicalId=target.getId();long oldTimestamp=target.getGameTimestamp();
            addCardToZone("Plains",actor,ZoneType.Battlefield);
            SpellAbility blink=addCardToZone("Cloudshift",actor,ZoneType.Hand).getFirstSpellAbility();
            blink.setActivatingPlayer(actor);assertTrue(blink.canTarget(target));blink.getTargets().add(target);
            assertTrue(blink.canPlay());assertTrue(ComputerUtilCost.canPayCost(blink,actor,false));
            assertTrue(ComputerUtil.playStack(blink,actor,game));assertEquals(blink.getPayingMana().size(),1);
            settle(game);target=game.getCardState(target);
            assertTrue(target.isInZone(ZoneType.Battlefield));assertEquals(target.getId(),physicalId);
            assertNotEquals(target.getGameTimestamp(),oldTimestamp,"The returned creature is a new zone object");
            assertTrue(combat.getBlockers(big).isEmpty());assertTrue(combat.isBlocked(big));
            assign(game,actor,target,big);assertEquals(combat.getBlockers(big).size(),1);
        }
        game.getPhaseHandler().setPriority(actor);game.copyLastState();
        assertTrue(combat.isBlocking(target,big));assertEquals(actor.getSpellsCastThisTurn(),spellCount(mode));
        Game original=game;Player originalActor=actor;Card originalSource=source,originalTarget=target,originalBig=big;
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
        assertTrue(game.getCombat().isBlocked(big));assertTrue(game.getCombat().isBlocking(target,big));
        return new Setup(game,actor,enemy,source,target,big,small,original,originalActor,originalSource,originalTarget,originalBig);
    }

    private int spellCount(String mode) {
        return 0; // The blocker declaration is not a spell cast.
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
