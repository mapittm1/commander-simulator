package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Controlled paid Trait Doctoring rules; independent of normal AI admission. */
public class CommanderTraitDoctoringRulesTest extends AITest {
    @Test public void paidColorChangeCanDeclineEncoding() { verifyPaid(false, false); }
    @Test public void paidLandChangeCanDeclineEncoding() { verifyPaid(true, false); }
    @Test public void paidColorChangeCanEncodeOnItsCreature() { verifyPaid(false, true); }
    @Test public void paidLandChangeCanEncodeOnItsCreature() { verifyPaid(true, true); }

    @Test public void invalidTargetCancelsBothTextChangeAndEncoding() {
        Pod pod = pod(false, true);
        cast(pod);
        pod.game.getAction().moveToGraveyard(pod.target, null);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
        assertTrue(pod.encoder.getEncodedCards().isEmpty());
        assertEquals(pod.encoder.getNetPower(), 1);
        assertPaid(pod);
    }

    @Test public void counteredSpellDoesNotEncode() {
        Pod pod = pod(false, true);
        cast(pod);
        SpellAbility counter = addCardToZone("Counterspell", pod.enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(pod.enemy);
        counter.getTargets().add(pod.action);
        assertTrue(ComputerUtil.playStack(counter, pod.enemy, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
        assertTrue(pod.encoder.getEncodedCards().isEmpty());
        assertEquals(pod.encoder.getNetPower(), 1);
        assertPaid(pod);
        assertEquals(pod.enemy.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L);
    }

    @Test public void noCreatureAllowsTextChangeButNotEncoding() {
        Pod pod = pod(false, true);
        pod.game.getAction().moveToGraveyard(pod.encoder, null);
        cast(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
        assertEquals(pod.target.getChangedTextColorWords().get("White"), "Blue");
        assertTrue(pod.actor.getCreaturesInPlay().isEmpty());
        assertPaid(pod);
    }

    @Test public void normalHandCastIsAllowedInOwnEmptyMainPhase() {
        Pod pod = pod(false, false);
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
    }

    @Test public void normalHandCastIsRejectedDuringCombat() {
        Pod pod = pod(false, false);
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, pod.actor, 2);
        assertFalse(pod.action.canPlay());
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }

    @Test public void normalHandCastIsRejectedOnAnOpponentsTurn() {
        Pod pod = pod(false, false);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, pod.enemy, 2);
        assertFalse(pod.action.canPlay());
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }

    @Test public void normalHandCastIsRejectedWithAnotherSpellOnTheStack() {
        Pod pod = pod(false, false);
        SpellAbility pending = addCardToZone("Memnite", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        pending.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(pending, pod.actor, pod.game));
        assertEquals(pod.game.getStack().size(), 1);
        assertFalse(pod.action.canPlay());
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }

    @Test public void copiedPendingEncodingLeavesOriginalPending() {
        Pod pod = pod(false, true);
        cast(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Player actor = copy.getPlayer(pod.actor.getId());
        installChoices(copy, actor, false, true);
        settle(copy);
        Card encoded = copy.getCardState(copier.find(pod.spell));
        Card encoder = copier.find(pod.encoder);
        assertEncoded(copy, encoded, encoder);
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.encoder.getEncodedCards().isEmpty());
        assertTrue(pod.target.getChangedTextColorWords().isEmpty());
        settle(pod.game);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
    }

    @Test public void copiedResolvedEncodingMapsBothLinksIntoTheCopy() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        Card original = pod.game.getCardState(pod.spell);
        assertEncoded(pod.game, original, pod.encoder);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card encoded = copy.getCardState(copier.find(original));
        Card encoder = copier.find(pod.encoder);
        assertEncoded(copy, encoded, encoder);
        assertEncoded(pod.game, original, pod.encoder);
    }

    @Test public void textExpirationDoesNotEndEncoding() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        assertEquals(pod.encoder.getNetPower(), 2);
        pod.game.getEndOfTurn().executeUntil();
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 1);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
    }

    @Test public void encodedCombatDamageCreatesOneCipherTrigger() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
    }

    @Test public void copiedEncodedCombatDamageCreatesOneCipherTrigger() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Player actor = copy.getPlayer(pod.actor.getId());
        installChoices(copy, actor, false, true);
        assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), copier.find(pod.encoder), 1);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.enemy.getLife(), 40);
    }

    @Test public void declinedEncodingCreatesNoCombatDamageTrigger() {
        Pod pod = pod(false, false);
        cast(pod);
        settle(pod.game);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 0);
    }

    @Test public void encoderZoneRoundtripEndsEncoding() { verifyEncoderRoundtrip(false); }
    @Test public void copiedEncoderZoneRoundtripDoesNotRestoreEncoding() { verifyEncoderRoundtrip(true); }
    @Test public void encodedSourceZoneRoundtripEndsEncoding() { verifySourceRoundtrip(false); }
    @Test public void copiedEncodedSourceRoundtripDoesNotRestoreEncoding() { verifySourceRoundtrip(true); }
    @Test public void twoEncodedSourcesCreateTwoCombatTriggers() { verifyMultipleSources(false); }
    @Test public void copiedMultipleSourcesKeepTwoCombatTriggers() { verifyMultipleSources(true); }

    @Test public void removingCopiedEncodedSourceLeavesOriginalEncodingUsable() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card encoded = copy.getCardState(copier.find(pod.game.getCardState(pod.spell)));
        Card encoder = copier.find(pod.encoder);
        assertEncoded(copy, encoded, encoder);
        Player actor = copy.getPlayer(pod.actor.getId());
        installChoices(copy, actor, false, true);
        copy.getAction().moveToGraveyard(encoded, null);
        assertTrue(encoder.getEncodedCards().isEmpty());
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), encoder, 0);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
    }

    @Test public void removingCopiedEncoderLeavesOriginalEncodingUsable() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card encoded = copy.getCardState(copier.find(pod.game.getCardState(pod.spell)));
        Card encoder = copier.find(pod.encoder);
        assertEncoded(copy, encoded, encoder);
        copy.getAction().moveToGraveyard(encoder, null);
        assertNull(encoded.getEncodingCard());
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
    }

    private void verifyEncoderRoundtrip(boolean copied) {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        Card departed = pod.game.getAction().moveToGraveyard(pod.encoder, null);
        Card returned = pod.game.getAction().moveToPlay(departed, pod.actor, null, Map.of());
        returned.setSickness(false);
        assertTrue(returned.getEncodedCards().isEmpty());
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Exile));
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            Player actor = copy.getPlayer(pod.actor.getId());
            installChoices(copy, actor, false, true);
            assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), copier.find(returned), 0);
            assertTrue(pod.game.getStack().isEmpty());
            assertEquals(pod.enemy.getLife(), 40);
        } else {
            assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, returned, 0);
        }
    }

    private void verifySourceRoundtrip(boolean copied) {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        Card departed = pod.game.getAction().moveToGraveyard(pod.game.getCardState(pod.spell), null);
        Card returned = pod.game.getAction().exile(departed, null, Map.of());
        assertTrue(returned.isInZone(ZoneType.Exile));
        assertTrue(pod.encoder.getEncodedCards().isEmpty());
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            Player actor = copy.getPlayer(pod.actor.getId());
            installChoices(copy, actor, false, true);
            assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), copier.find(pod.encoder), 0);
            assertTrue(pod.game.getStack().isEmpty());
            assertEquals(pod.enemy.getLife(), 40);
        } else {
            assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 0);
        }
    }

    private void verifyMultipleSources(boolean copied) {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        for (int i = 0; i < 4; i++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card second = addCardToZone("Last Thoughts", pod.actor, ZoneType.Hand);
        SpellAbility action = second.getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.encoder.getEncodedCards().size(), 2);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 5L);
        assertSame(pod.game.getCardState(second).getEncodingCard(), pod.encoder);
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            Player actor = copy.getPlayer(pod.actor.getId());
            installChoices(copy, actor, false, true);
            assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), copier.find(pod.encoder), 2);
            assertTrue(pod.game.getStack().isEmpty());
            assertEquals(pod.encoder.getEncodedCards().size(), 2);
            assertEquals(pod.enemy.getLife(), 40);
        } else {
            assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 2);
        }
    }

    private void assertCipherTriggerAfterDamage(Game game, Player actor, Player enemy, Card encoder, int triggers) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        Combat combat = new Combat(actor);
        encoder.setTapped(true);
        combat.addAttacker(encoder, enemy);
        combat.getBandOfAttacker(encoder).setBlocked(false);
        game.getPhaseHandler().setCombat(combat);
        int life = enemy.getLife();
        int damage = encoder.getNetPower();
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 2);
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(enemy.getLife(), life - damage, "Native combat damage must actually happen");
        assertEquals(game.getStack().size(), triggers,
                "The encoded creature's native combat-damage trigger must be retained");
    }

    private void verifyPaid(boolean land, boolean encode) {
        Pod pod = pod(land, encode);
        cast(pod);
        settle(pod.game);
        assertEquals(pod.encoder.getNetPower(), land ? 4 : 2);
        if (encode) {
            assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        } else {
            assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
            assertTrue(pod.encoder.getEncodedCards().isEmpty());
        }
        assertPaid(pod);
    }

    private void assertEncoded(Game game, Card encoded, Card encoder) {
        assertTrue(encoded.isInZone(ZoneType.Exile));
        assertSame(encoded.getGame(), game);
        assertSame(encoder.getGame(), game);
        assertSame(encoded.getEncodingCard(), encoder, "The encoded source must refer to this game's creature");
        assertEquals(encoder.getEncodedCards().size(), 1);
        assertSame(encoder.getEncodedCards().get(0), encoded, "The creature must refer to this game's exile card");
    }

    private void assertPaid(Pod pod) {
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8, "Trait Doctoring has no card-draw instruction");
    }

    private void cast(Pod pod) {
        assertTrue(pod.action.canPlay());
        assertTrue(pod.action.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
    }

    private void installChoices(Game game, Player actor, boolean land, boolean encode) {
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells,
                    SpellAbility action, String title, Map<String, Object> params) {
                if (action.getApi() != ApiType.GenericChoice) return super.chooseSingleSpellForEffect(spells, action, title, params);
                return spells.stream().filter(option -> option.hasParam(land ? "ChangeTypeWord" : "ChangeColorWord"))
                        .findFirst().orElseThrow();
            }
            @Override public byte chooseColor(String title, SpellAbility action, ColorSet options) {
                return options.hasAnyColor(MagicColor.WHITE) ? MagicColor.WHITE : MagicColor.BLUE;
            }
            @Override public String chooseSomeType(String kind, SpellAbility action,
                    Collection<String> options, boolean optional) {
                return options.contains("Swamp") ? "Swamp" : "Island";
            }
            @Override public boolean confirmTrigger(WrappedAbility wrapper) {
                return wrapper.getWrappedAbility().getApi() == ApiType.Play || super.confirmTrigger(wrapper);
            }
            @Override public boolean confirmAction(SpellAbility action, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                return action.getApi() == ApiType.Encode ? encode : super.confirmAction(action, mode, message, options, card, params);
            }
        });
    }

    private Pod pod(boolean land, boolean encode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("trait-rules-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Trait Doctoring paid encoding"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 2; i++) addCardToZone("Island", enemy, ZoneType.Battlefield);
        Card encoder = addCardToZone(land ? "Crusading Knight" : "Invisible Stalker", actor, ZoneType.Battlefield);
        encoder.setSickness(false);
        Card target = land ? encoder : addCardToZone("Honor of the Pure", actor, ZoneType.Battlefield);
        Card spell = addCardToZone("Trait Doctoring", actor, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(target);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        installChoices(game, actor, land, encode);
        return new Pod(game, actor, enemy, encoder, target, spell, action);
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
        throw new AssertionError("Trait Doctoring rules stack did not settle");
    }

    private record Pod(Game game, Player actor, Player enemy, Card encoder,
                       Card target, Card spell, SpellAbility action) { }
}
