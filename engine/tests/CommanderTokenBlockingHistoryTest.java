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

/** Actual paid token-entry paths retain departed blockers across original and copied games. */
public class CommanderTokenBlockingHistoryTest extends AITest {
    @Test public void originalTokenOnlyState() { check("one", 0, false); }
    @Test public void originalTokenOnlyDamage() { check("one", 0, true); }
    @Test public void originalEarlierCreatureDiedState() { check("died", 0, false); }
    @Test public void originalEarlierCreatureDiedDamage() { check("died", 0, true); }
    @Test public void originalTokenEnteredAlreadyBlockedThenDiedState() { check("tokenDied", 0, false); }
    @Test public void originalTokenEnteredAlreadyBlockedThenDiedDamage() { check("tokenDied", 0, true); }
    @Test public void copiedTokenOnlyState() { check("one", 1, false); }
    @Test public void copiedTokenOnlyDamage() { check("one", 1, true); }
    @Test public void copiedEarlierCreatureDiedState() { check("died", 1, false); }
    @Test public void copiedEarlierCreatureDiedDamage() { check("died", 1, true); }
    @Test public void copiedTokenEnteredAlreadyBlockedThenDiedState() { check("tokenDied", 1, false); }
    @Test public void copiedTokenEnteredAlreadyBlockedThenDiedDamage() { check("tokenDied", 1, true); }
    @Test public void commonTokenOnlyState() { check("one", 2, false); }
    @Test public void commonTokenOnlyDamage() { check("one", 2, true); }
    @Test public void commonEarlierCreatureDiedState() { check("died", 2, false); }
    @Test public void commonEarlierCreatureDiedDamage() { check("died", 2, true); }
    @Test public void commonTokenEnteredAlreadyBlockedThenDiedState() { check("tokenDied", 2, false); }
    @Test public void commonTokenEnteredAlreadyBlockedThenDiedDamage() { check("tokenDied", 2, true); }

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
        boolean shouldRemainBlocked=!mode.equals("one");
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
            assertEquals(setup.original.getCombat().getBlockers(setup.originalBig).size(),1);
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
        addCardToZone("Mountain",actor,ZoneType.Battlefield);
        for (int i=0;i<3;i++) addCardToZone("Forest",actor,ZoneType.Battlefield);
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
        combat.fireTriggersForUnblockedAttackers(game);
        if (mode.equals("died")) {
            assign(game,actor,earlier,big);
            kill(game,enemy,earlier);
            assertTrue(combat.isBlocked(big));assertTrue(combat.getBlockers(big).isEmpty());
        } else if (mode.equals("tokenDied")) {
            assign(game,actor,target,big);
        }
        game.getPhaseHandler().setPriority(actor);
        SpellAbility foliage=addCardToZone("Flash Foliage",actor,ZoneType.Hand).getFirstSpellAbility();
        foliage.setActivatingPlayer(actor);assertTrue(foliage.canTarget(big));foliage.getTargets().add(big);
        assertTrue(foliage.canPlay());assertTrue(ComputerUtilCost.canPayCost(foliage,actor,false));
        int handBefore=actor.getCardsIn(ZoneType.Hand).size();
        assertTrue(ComputerUtil.playStack(foliage,actor,game));assertEquals(foliage.getPayingMana().size(),3);
        settle(game);assertEquals(actor.getCardsIn(ZoneType.Hand).size(),handBefore);
        Card token=combat.getBlockers(big).stream().filter(Card::isToken).findFirst().orElseThrow();
        assertEquals(token.getName(),"Saproling Token");assertTrue(token.getType().hasSubtype("Saproling"));assertTrue(token.isInZone(ZoneType.Battlefield));
        assertTrue(combat.isBlocking(token,big));assertTrue(combat.isBlocked(big));
        if (mode.equals("tokenDied")) {
            assertEquals(combat.getBlockers(big).size(),2);kill(game,enemy,token);
            assertTrue(combat.isBlocking(target,big));
        } else {
            target=token;
        }
        assertTrue(combat.isBlocked(big));assertFalse(combat.isBlocked(small));
        assertEquals(combat.getBlockers(big).size(),1);
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
        return mode.equals("one") ? 1 : 2;
    }

    private void kill(Game game,Player enemy,Card victim) {
        addCardToZone("Mountain",enemy,ZoneType.Battlefield);
        SpellAbility shock=addCardToZone("Shock",enemy,ZoneType.Hand).getFirstSpellAbility();
        shock.setActivatingPlayer(enemy);assertTrue(shock.canTarget(victim));shock.getTargets().add(victim);
        game.getPhaseHandler().setPriority(enemy);assertTrue(shock.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(shock,enemy,false));
        assertTrue(ComputerUtil.playStack(shock,enemy,game));assertEquals(shock.getPayingMana().size(),1);
        settle(game);
        if (victim.isToken()) assertFalse(game.getCardState(victim).isInZone(ZoneType.Battlefield));
        else assertTrue(game.getCardState(victim).isInZone(ZoneType.Graveyard));
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
