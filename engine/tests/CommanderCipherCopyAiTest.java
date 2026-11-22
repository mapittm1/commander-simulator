package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native pilot admission and resolution; no scripted targeting, mode, word or encoding choices. */
public class CommanderCipherCopyAiTest extends AITest {
    @Test public void normalPilotCastsUsefulColorCipherCopyDuringFirstStrike() { verifyFreeCopy(false); }
    @Test public void normalPilotCastsUsefulLandCipherCopyDuringFirstStrike() { verifyFreeCopy(true); }
    private void verifyFreeCopy(boolean land) {
        Pod pod = pod(land);
        ai(pod.actor).setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> actions = ai(pod.actor).chooseSpellAbilityToPlay();
        assertNotNull(actions);
        SpellAbility chosen = actions.stream().filter(action -> action.getHostCard().getId() == pod.spell.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(chosen, pod.actor, pod.game));
        settle(pod.game);
        Card encoded = pod.game.getCardState(pod.spell);
        assertTrue(encoded.isInZone(ZoneType.Exile));
        Card encoder = encoded.getEncodingCard();
        assertNotNull(encoder);
        assertSame(encoder, pod.encoder);
        Card witness = addCardToZone(land ? "Crusading Knight" : "Honor of the Pure", pod.actor, ZoneType.Battlefield);
        witness.setSickness(false);
        encoder.addIntrinsicKeyword("Double strike");
        pod.enemy.setLife(land ? 18 : 12, null);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        assertEquals(encoder.getNetPower(), land ? 6 : 2);
        if (land) assertEquals(witness.getNetPower(), 2);
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, pod.actor, 2);
        Combat combat = new Combat(pod.actor);
        for (Card attacker : pod.actor.getCreaturesInPlay()) {
            attacker.setTapped(true);
            combat.addAttacker(attacker, pod.enemy);
            combat.getBandOfAttacker(attacker).setBlocked(false);
        }
        pod.game.getPhaseHandler().setCombat(combat);
        int life = pod.enemy.getLife();
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE, pod.actor, false, 2);
        assertTrue(combat.assignCombatDamage(true));
        combat.dealAssignedDamage();
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().collectTriggerForWaiting();
        pod.game.getTriggerHandler().runWaitingTriggers();
        pod.game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(pod.enemy.getLife(), life - encoder.getNetPower());
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.enemy.getLife() > 0);
        settle(pod.game);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2,
                "The normal pilot must actually cast the beneficial free cipher copy before regular combat damage");
        assertEquals(land ? witness.getNetPower() : encoder.getNetPower(), land ? 6 : 3);
        assertTrue(pod.game.getCardState(encoded).isInZone(ZoneType.Exile));
        assertSame(pod.game.getCardState(encoded).getEncodingCard(), encoder);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    private void verify(boolean land, AIOption mode, boolean copied) {
        Pod pod = pod(land);
        ai(pod.actor).setUseSimulation(mode);
        List<SpellAbility> actions = ai(pod.actor).chooseSpellAbilityToPlay();
        assertTrue(actions != null && actions.stream().anyMatch(action -> action.getHostCard().getId() == pod.spell.getId()),
                "Native pilot should choose the legal beneficial text change, mode=" + mode + ", land=" + land);
        SpellAbility chosen = actions.stream().filter(action -> action.getHostCard().getId() == pod.spell.getId()).findFirst().orElseThrow();
        assertTrue(chosen.canPlay());
        assertTrue(chosen.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(chosen, pod.actor, pod.game));
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            settle(copy);
            assertEquals(copier.find(pod.encoder).getNetPower(), land ? 6 : 2);
            assertEquals(pod.encoder.getNetPower(), land ? 2 : 1);
            Card encoded = copy.getCardState(copier.find(pod.spell));
            assertTrue(encoded.isInZone(ZoneType.Exile));
            assertSame(encoded.getEncodingCard().getGame(), copy);
            assertTrue(pod.spell.isInZone(ZoneType.Stack));
        }
        settle(pod.game);
        assertEquals(pod.encoder.getNetPower(), land ? 6 : 2);
        Card encoded = pod.game.getCardState(pod.spell);
        assertTrue(encoded.isInZone(ZoneType.Exile));
        assertNotNull(encoded.getEncodingCard());
        assertSame(encoded.getEncodingCard().getGame(), pod.game);
        assertTrue(encoded.getEncodingCard().getEncodedCards().contains(encoded));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
    }

    private AiController ai(Player player) { return ((PlayerControllerAi) player.getController()).getAi(); }

    private Pod pod(boolean land) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("trait-pilot-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Trait Doctoring native pilot research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card encoder = addCardToZone(land ? "Crusading Knight" : "Invisible Stalker", actor, ZoneType.Battlefield);
        encoder.setSickness(false);
        if (land) {
            for (int i = 0; i < 4; i++) addCardToZone("Island", enemy, ZoneType.Battlefield);
        } else {
            addCardToZone("Honor of the Pure", actor, ZoneType.Battlefield);
            for (int i = 0; i < 2; i++) {
                Card creature = addCardToZone("Coral Merfolk", actor, ZoneType.Battlefield);
                creature.setSickness(false);
            }
        }
        addCardToZone("Omniscience", actor, ZoneType.Hand);
        addCardToZone("Enter the Infinite", actor, ZoneType.Hand);
        Card spell = addCardToZone("Trait Doctoring", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        assertEquals(encoder.getNetPower(), land ? 2 : 1);
        return new Pod(game, actor, enemy, encoder, spell);
    }

    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native pilot research stack did not settle");
    }
    private record Pod(Game game, Player actor, Player enemy, Card encoder, Card spell) { }
}
