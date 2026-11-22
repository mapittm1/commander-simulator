package forge.ai;

import forge.card.ColorSet;
import forge.card.MagicColor;
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
import forge.ai.simulation.GameCopier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Real Crystal Spray rules, with explicit choices independent of AI admission policy. */
public class CommanderCrystalSprayRulesTest extends AITest {
    @Test public void paidColorChangeDrawsAndExpires() { verifyExpiration(false); }
    @Test public void paidLandChangeDrawsAndExpires() { verifyExpiration(true); }
    @Test public void copiedColorExpirationLeavesOriginalEffectActive() { verifyCopiedExpiration(false); }
    @Test public void copiedLandExpirationLeavesOriginalEffectActive() { verifyCopiedExpiration(true); }

    @Test public void targetLeavingBattlefieldCancelsTheCantrip() {
        Pod pod = pod(false);
        cast(pod);
        pod.game.getAction().moveToGraveyard(pod.target, null);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
        assertEquals(pod.beneficiary.getNetPower(), 2);
        assertPaid(pod);
    }

    @Test public void targetLeavingAndReturningDoesNotRestoreLegality() {
        Pod pod = pod(false);
        cast(pod);
        Card departed = pod.game.getAction().moveToGraveyard(pod.target, null);
        Card returned = pod.game.getAction().moveToPlay(departed, pod.actor, null, Map.of());
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertTrue(returned.isInZone(ZoneType.Battlefield));
        assertTrue(returned.getChangedTextColorWords().isEmpty());
        assertEquals(pod.beneficiary.getNetPower(), 2);
        assertPaid(pod);
    }

    @Test public void copiedPendingIllegalTargetDoesNotAlterOriginal() {
        Pod pod = pod(false);
        cast(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        copy.getAction().moveToGraveyard(copier.find(pod.target), null);
        settle(copy);
        assertEquals(copy.getPlayer(pod.actor.getId()).getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.target.isInZone(ZoneType.Battlefield));
        assertEquals(pod.beneficiary.getNetPower(), 2);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.beneficiary.getNetPower(), 3);
    }

    @Test public void legalNoEffectChangeStillDrawsOneCard() {
        Pod pod = pod(false);
        pod.action.resetTargets();
        pod.action.getTargets().add(pod.beneficiary);
        cast(pod);
        settle(pod.game);
        assertEquals(pod.beneficiary.getNetPower(), 2);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertDrawn(pod);
        assertPaid(pod);
    }

    @Test public void twoManaCannotPayForTheSpell() {
        Pod pod = pod(false);
        Card land = pod.actor.getLandsInPlay().get(0);
        land.setTapped(true);
        assertFalse(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertTrue(pod.game.getStack().isEmpty());
    }

    @Test public void playersAndCardsInHandAreNotLegalTargets() {
        Pod pod = pod(false);
        assertFalse(pod.action.canTarget(pod.enemy));
        Card hand = addCardToZone("Grizzly Bears", pod.enemy, ZoneType.Hand);
        assertFalse(pod.action.canTarget(hand));
        assertTrue(pod.action.canTarget(pod.target));
    }

    @Test public void drawRestrictionDoesNotCancelTheTextChange() {
        Pod pod = pod(false);
        addCardToZone("Spirit of the Labyrinth", pod.enemy, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.actor.drawCards(1);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        cast(pod);
        settle(pod.game);
        assertEquals(pod.beneficiary.getNetPower(), 3);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertPaid(pod);
    }

    @Test public void castTriggerStillDrawsWhenTheSpellTargetBecomesIllegal() {
        Pod pod = pod(false);
        addCardToZone("Archmage Emeritus", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        cast(pod);
        pod.game.getAction().moveToGraveyard(pod.target, null);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7,
                "The cast trigger draws once; the illegal spell must not draw again");
        assertEquals(pod.beneficiary.getNetPower(), 2);
        assertPaid(pod);
    }

    @Test public void changingAPendingCounterspellSavesItsBlueTargetAndDraws() {
        Pod pod = pod(false);
        for (int i = 0; i < 3; i++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        for (int i = 0; i < 2; i++) addCardToZone("Island", pod.enemy, ZoneType.Battlefield);
        SpellAbility draw = addCardToZone("Divination", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        draw.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(draw, pod.actor, pod.game));
        SpellAbility counter = addCardToZone("Gainsay", pod.enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(pod.enemy);
        counter.getTargets().add(draw);
        assertTrue(ComputerUtil.playStack(counter, pod.enemy, pod.game));
        pod.action.resetTargets();
        pod.action.getTargets().add(counter);
        choices(pod, false, true);
        cast(pod);
        pod.game.getStack().resolveStack();
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.game.getCardState(counter.getHostCard()).getChangedTextColorWords().get("Blue"),
                "Red", "The modal child must rewrite its parent spell target");
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 5,
                "Crystal Spray draws one, then the saved Divination draws two");
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 6L);
        assertTrue(pod.game.getCardState(draw.getHostCard()).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(counter.getHostCard()).isInZone(ZoneType.Graveyard));
    }

    @Test public void changingAPendingLandWordSpellChangesItsActualVictims() {
        Pod pod = pod(false);
        for (int i = 0; i < 4; i++) addCardToZone("Mountain", pod.enemy, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", pod.enemy, ZoneType.Battlefield);
        SpellAbility destruction = addCardToZone("Boil", pod.enemy, ZoneType.Hand).getFirstSpellAbility();
        destruction.setActivatingPlayer(pod.enemy);
        assertTrue(ComputerUtil.playStack(destruction, pod.enemy, pod.game));
        pod.action.resetTargets();
        pod.action.getTargets().add(destruction);
        choices(pod, true, true);
        cast(pod);
        pod.game.getStack().resolveStack();
        assertEquals(pod.game.getCardState(destruction.getHostCard()).getChangedTextTypeWords().get("Island"),
                "Swamp", "The type mode must rewrite its actual parent spell target");
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.actor.getLandsInPlay().size(), 3);
        assertTrue(pod.game.getCardState(swamp).isInZone(ZoneType.Graveyard));
        assertPaid(pod);
    }

    private void verifyExpiration(boolean land) {
        Pod pod = pod(land);
        cast(pod);
        settle(pod.game);
        assertEquals(pod.beneficiary.getNetPower(), land ? 4 : 3);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertDrawn(pod);
        assertPaid(pod);
        pod.game.getEndOfTurn().executeUntil();
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.beneficiary.getNetPower(), 2);
        assertTrue(pod.target.getChangedTextColorWords().isEmpty());
        assertTrue(pod.target.getChangedTextTypeWords().isEmpty());
    }

    private void verifyCopiedExpiration(boolean land) {
        Pod pod = pod(land);
        cast(pod);
        settle(pod.game);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        assertEquals(copier.find(pod.beneficiary).getNetPower(), land ? 4 : 3);
        copy.getEndOfTurn().executeUntil();
        copy.getAction().checkStateEffects(true);
        assertEquals(copier.find(pod.beneficiary).getNetPower(), 2);
        assertEquals(pod.beneficiary.getNetPower(), land ? 4 : 3);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        pod.game.getEndOfTurn().executeUntil();
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.beneficiary.getNetPower(), 2);
    }

    private void assertDrawn(Pod pod) {
        assertTrue(pod.actor.getCardsIn(ZoneType.Hand).stream()
                .anyMatch(card -> card.getId() == pod.top.getId()));
    }

    private void assertPaid(Pod pod) {
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 3L);
    }

    private void cast(Pod pod) {
        assertTrue(pod.action.canPlay());
        assertTrue(pod.action.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
    }

    private void choices(Pod pod, boolean land, boolean counterspell) {
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells,
                    SpellAbility action, String title, Map<String, Object> params) {
                return spells.stream().filter(option -> option.hasParam(land ? "ChangeTypeWord" : "ChangeColorWord"))
                        .findFirst().orElseThrow();
            }
            @Override public byte chooseColor(String title, SpellAbility action, ColorSet options) {
                byte original = counterspell ? MagicColor.BLUE : MagicColor.WHITE;
                return options.hasAnyColor(original) ? original : counterspell ? MagicColor.RED : MagicColor.BLUE;
            }
            @Override public String chooseSomeType(String kind, SpellAbility action,
                    Collection<String> options, boolean optional) {
                String original = counterspell ? "Island" : "Swamp";
                return options.contains(original) ? original : counterspell ? "Swamp" : "Island";
            }
        });
    }

    private Pod pod(boolean land) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("crystal-rules-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Crystal Spray native rules"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 3; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card target;
        Card beneficiary;
        if (land) {
            target = addCardToZone("Crusading Knight", actor, ZoneType.Battlefield);
            beneficiary = target;
            for (int i = 0; i < 2; i++) addCardToZone("Island", enemy, ZoneType.Battlefield);
        } else {
            target = addCardToZone("Honor of the Pure", actor, ZoneType.Battlefield);
            beneficiary = addCardToZone("Coral Merfolk", actor, ZoneType.Battlefield);
        }
        Card spell = addCardToZone("Crystal Spray", actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(target);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        Pod pod = new Pod(game, actor, enemy, target, beneficiary, spell, action,
                actor.getCardsIn(ZoneType.Library).get(0));
        choices(pod, land, false);
        return pod;
    }

    private void settle(Game game) {
        for (int i = 0; i < 32; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Crystal Spray rules stack did not settle");
    }

    private record Pod(Game game, Player actor, Player enemy, Card target, Card beneficiary,
                       Card spell, SpellAbility action, Card top) { }
}
