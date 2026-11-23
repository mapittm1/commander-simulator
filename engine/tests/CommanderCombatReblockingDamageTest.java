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

/** Independently witness real damage without masking the original blocked-state contracts. */
public class CommanderCombatReblockingDamageTest extends AITest {
    @Test public void originalOrdersRealDamage() { paid(false, false, false); }
    @Test public void originalOrdersDepartedBlocker() { paid(false, false, true); }
    @Test public void originalWarlordRealDamage() { paid(true, false, false); }
    @Test public void originalWarlordDepartedBlocker() { paid(true, false, true); }
    @Test public void copiedOrdersRealDamage() { paid(false, true, false); }
    @Test public void copiedOrdersDepartedBlocker() { paid(false, true, true); }
    @Test public void copiedWarlordRealDamage() { paid(true, true, false); }
    @Test public void copiedWarlordDepartedBlocker() { paid(true, true, true); }

    private Scenario scenario(boolean warlord, boolean copied) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index=0; index<4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("reblocking-"+index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Reblocking"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player enemy=game.getPlayers().get(0), actor=game.getPlayers().get(1);
        for (Player player:game.getPlayers()) {
            for (int index=0; index<12; index++) addCardToZone("Shock", player, ZoneType.Library);
        }
        actor.setLife(3, null);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        Card source=addCardToZone(warlord ? "Balduvian Warlord" : "False Orders", actor,
                warlord ? ZoneType.Battlefield : ZoneType.Hand); source.setSickness(false);
        Card blocker=addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card big=addCardToZone("Craw Wurm", enemy, ZoneType.Battlefield);
        Card small=addCardToZone("Runeclaw Bear", enemy, ZoneType.Battlefield);
        big.setSickness(false);small.setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, enemy, 3);
        Combat combat=new Combat(enemy); game.getPhaseHandler().setCombat(combat);
        for (Card attacker:List.of(big,small)) {
            assertTrue(CombatUtil.canAttack(attacker, actor));
            combat.addAttacker(attacker,actor);attacker.setTapped(true);
            CombatUtil.checkDeclaredAttacker(game,attacker,combat,true);
            combat.setBlocked(attacker,false);
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS,enemy,false,3);
        combat.addBlocker(small,blocker);combat.setBlocked(small,true);
        game.getPhaseHandler().setPriority(actor);game.copyLastState();
        Game original=game;Player originalActor=actor;Card originalSource=source;
        Card originalBlocker=blocker, originalSmall=small, originalBig=big;
        if (copied) {
            GameCopier copier=new GameCopier(game).withStack(true);game=copier.makeCopy(null,actor);
            actor=copier.find(actor);enemy=copier.find(enemy);source=copier.find(source);
            blocker=copier.find(blocker);big=copier.find(big);small=copier.find(small);
        }
        assertTrue(game.getCombat().isBlocking(blocker,small));
        assertFalse(game.getCombat().isBlocked(big));
        assertEquals(ComputerUtilCombat.lifeThatWouldRemain(actor,game.getCombat()),-3);
        return new Scenario(game,actor,enemy,source,blocker,big,small,original,originalActor,
                originalSource,originalBlocker,originalSmall,originalBig);
    }

    private SpellAbility action(Scenario setup) {
        SpellAbility action=setup.source.getSpellAbilities().stream()
                .filter(sa -> sa.getApi()==ApiType.RemoveFromCombat).findFirst().orElseThrow();
        action.setActivatingPlayer(setup.actor);
        assertTrue(action.canTarget(setup.blocker));action.getTargets().add(setup.blocker);
        assertTrue(action.canPlay());assertTrue(ComputerUtilCost.canPayCost(action,setup.actor,false));
        return action;
    }

    private void paid(boolean warlord, boolean copied, boolean shoot) {
        Scenario setup=scenario(warlord,copied);SpellAbility action=action(setup);
        int[] observed={0};
        PlayerControllerAi controller=new PlayerControllerAi(setup.game,setup.actor,setup.actor.getLobbyPlayer()) {
            @Override public CardCollectionView chooseCardsForEffect(CardCollectionView options,
                    SpellAbility choice,String title,int min,int max,boolean optional,Map<String,Object> params) {
                assertEquals(choice.getApi(),ApiType.ChooseCard);assertTrue(options.contains(setup.big));
                assertEquals(min,warlord ? 1 : 0);assertEquals(max,1);observed[0]++;
                CardCollection chosen=new CardCollection();chosen.add(setup.big);return chosen;
            }
        };
        setup.actor.runWithController(() -> {
            assertTrue(ComputerUtil.playStack(action,setup.actor,setup.game));
            assertEquals(action.getPayingMana().size(),warlord ? 0 : 1);
            if (warlord) assertTrue(setup.source.isTapped());
            settle(setup.game);
        },controller);
        assertEquals(observed[0],1,"Witness the actual printed choice, not a prepopulated chosen-card shortcut");
        witness(setup,shoot);independent(setup);
    }

    private void witness(Scenario setup, boolean shoot) {
        Combat combat=setup.game.getCombat();
        assertFalse(combat.isBlocking(setup.blocker,setup.small));assertFalse(combat.isBlocked(setup.small));
        assertTrue(combat.isBlocking(setup.blocker,setup.big));
        assertEquals(combat.getBlockers(setup.big).size(),1);
        assertSame(combat.getBlockers(setup.big).get(0),setup.blocker);
        System.out.println("REBLOCK_DAMAGE "+setup.source.getName()+" copied="+(setup.game!=setup.original)
                +" blockerDeparts="+shoot+" blockedFlag="+combat.isBlocked(setup.big));
        if (shoot) {
            addCardToZone("Mountain",setup.enemy,ZoneType.Battlefield);
            SpellAbility shock=addCardToZone("Shock",setup.enemy,ZoneType.Hand).getFirstSpellAbility();
            shock.setActivatingPlayer(setup.enemy);assertTrue(shock.canTarget(setup.blocker));
            shock.getTargets().add(setup.blocker);setup.game.getPhaseHandler().setPriority(setup.enemy);
            assertTrue(shock.canPlay());assertTrue(ComputerUtilCost.canPayCost(shock,setup.enemy,false));
            assertTrue(ComputerUtil.playStack(shock,setup.enemy,setup.game));assertEquals(shock.getPayingMana().size(),1);
            settle(setup.game);
            assertTrue(setup.game.getCardState(setup.blocker).isInZone(ZoneType.Graveyard));
            assertTrue(combat.getBlockers(setup.big).isEmpty());
        }
        // The original20 contracts still require the correct flag. These additional
        // contracts independently witness actual damage before/after the blocker departs.
        damage(setup);assertFalse(setup.actor.hasLost(),"The large attacker became blocked during the printed effect");
        assertEquals(setup.actor.getLife(),1,"Only the newly unblocked smaller attacker deals2 damage");
    }

    private void damage(Scenario setup) {
        setup.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE,setup.enemy,false,3);
        assertTrue(setup.game.getCombat().assignCombatDamage(false));
        setup.game.getCombat().dealAssignedDamage();settle(setup.game);
    }

    private void settle(Game game) {
        for (int guard=0;guard<40;guard++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;game.getStack().resolveStack();
        }
        fail("Reblocking fixture did not settle");
    }

    private void independent(Scenario setup) {
        if (setup.game==setup.original) return;
        assertEquals(setup.originalActor.getLife(),3);assertFalse(setup.originalActor.hasLost());
        assertFalse(setup.originalSource.isTapped());assertTrue(setup.original.getStack().isEmpty());
        assertTrue(setup.original.getCombat().isBlocking(setup.originalBlocker,setup.originalSmall));
        assertFalse(setup.original.getCombat().isBlocked(setup.originalBig));
    }

    private record Scenario(Game game,Player actor,Player enemy,Card source,Card blocker,Card big,Card small,
            Game original,Player originalActor,Card originalSource,Card originalBlocker,Card originalSmall,Card originalBig) { }
}
