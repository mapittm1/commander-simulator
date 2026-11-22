package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.DelayedReveal;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Cipher casting, ownership and type composition under controlled choices; not an AI admission test. */
public class CommanderCipherCastingTest extends AITest {
    @Test public void combatCipherCastsAFreeCopyWithoutReencoding() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        List<SpellAbility> offered = new ArrayList<>();
        Card target = addCardToZone("Honor of the Pure", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 2);
        installCastingChoices(pod.game, pod.actor, target, offered, true);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
        pod.game.getStack().resolveStack();
        assertEquals(offered.size(), 1);
        assertEquals(pod.game.getStack().size(), 1, "The native trigger must actually cast the copy");
        settle(pod.game);
        assertEquals(pod.encoder.getNetPower(), 3, "The cast copy must resolve its text change");
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2);
    }

    @Test public void counteringCipherCopyLeavesTheEncodedCardInExile() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        List<SpellAbility> offered = new ArrayList<>();
        Card target = addCardToZone("Honor of the Pure", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 2);
        installCastingChoices(pod.game, pod.actor, target, offered, true);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
        pod.game.getStack().resolveStack();
        assertEquals(offered.size(), 1);
        SpellAbility counter = addCardToZone("Counterspell", pod.enemy, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(pod.enemy);
        counter.getTargets().add(offered.get(0));
        assertTrue(counter.canPlay());
        assertTrue(counter.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(counter, pod.enemy, pod.game));
        settle(pod.game);
        assertEquals(pod.encoder.getNetPower(), 2, "Countering the copy prevents its text change");
        assertTrue(pod.game.getCardState(counter.getHostCard()).isInZone(ZoneType.Graveyard));
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        assertEquals(pod.enemy.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L);
    }

    @Test public void decliningCipherCopyLeavesTheEncodingUsable() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        List<SpellAbility> offered = new ArrayList<>();
        installCastingChoices(pod.game, pod.actor, pod.target, offered, false);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
        settle(pod.game);
        assertEquals(offered.size(), 1);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        Card target = addCardToZone("Honor of the Pure", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 2);
        installCastingChoices(pod.game, pod.actor, target, offered, true);
        pod.encoder.setTapped(false);
        assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
        settle(pod.game);
        assertEquals(offered.size(), 2);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2);
        assertEquals(pod.encoder.getNetPower(), 3);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
    }

    @Test public void repeatedCombatHitsCanCastTwoCopiesFromOneEncoding() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        List<SpellAbility> offered = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Card target = addCardToZone("Honor of the Pure", pod.actor, ZoneType.Battlefield);
            pod.game.getAction().checkStateEffects(true);
            installCastingChoices(pod.game, pod.actor, target, offered, true);
            pod.encoder.setTapped(false);
            assertCipherTriggerAfterDamage(pod.game, pod.actor, pod.enemy, pod.encoder, 1);
            settle(pod.game);
            assertEquals(pod.encoder.getNetPower(), 3 + i);
        }
        assertEquals(offered.size(), 2);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 3);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    @Test public void copiedGameCastsItsOwnCipherCopyWithoutChangingOriginal() {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        Card target = addCardToZone("Honor of the Pure", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 2);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Player actor = copy.getPlayer(pod.actor.getId());
        List<SpellAbility> offered = new ArrayList<>();
        installCastingChoices(copy, actor, copier.find(target), offered, true);
        assertCipherTriggerAfterDamage(copy, actor, copy.getPlayer(pod.enemy.getId()), copier.find(pod.encoder), 1);
        copy.getStack().resolveStack();
        assertEquals(offered.size(), 1);
        assertEquals(copy.getStack().size(), 1);
        assertSame(offered.get(0).getHostCard().getGame(), copy);
        settle(copy);
        assertEquals(copier.find(pod.encoder).getNetPower(), 3);
        assertEquals(pod.encoder.getNetPower(), 2);
        assertEquals(actor.getSpellsCastThisTurn(), 2);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertEquals(pod.enemy.getLife(), 40);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), pod.encoder);
    }

    @Test public void changedControllerCanCastTheEncodedCopy() { verifyChangedController(false); }
    @Test public void copiedChangedControllerCanCastWithoutChangingOriginal() { verifyChangedController(true); }
    @Test public void temporarilyNoncreatureEncoderRetainsItsEncoding() { verifyTemporaryCreature(false); }
    @Test public void copiedNoncreatureEncoderCanAnimateAndTrigger() { verifyTemporaryCreature(true); }

    private void verifyChangedController(boolean copied) {
        Pod pod = pod(false, true);
        cast(pod);
        settle(pod.game);
        pod.encoder.setController(pod.enemy, pod.game.getNextTimestamp());
        pod.game.getAction().controllerChangeZoneCorrection(pod.encoder);
        pod.encoder.setSickness(false);
        pod.game.getAction().checkStateEffects(true);
        assertEquals(pod.encoder.getNetPower(), 1);
        Card target = addCardToZone("Honor of the Pure", pod.enemy, ZoneType.Battlefield);
        Game game = pod.game;
        Card encoder = pod.encoder, encoded = game.getCardState(pod.spell);
        Player caster = pod.enemy, defender = pod.actor;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            encoder = copier.find(encoder);
            encoded = copier.find(encoded);
            target = copier.find(target);
            caster = game.getPlayer(caster.getId());
            defender = game.getPlayer(defender.getId());
        }
        assertSame(encoder.getController(), caster);
        assertSame(encoded.getOwner(), defender);
        List<SpellAbility> offered = new ArrayList<>();
        installCastingChoices(game, caster, target, offered, true);
        assertCipherTriggerAfterDamage(game, caster, defender, encoder, 1);
        assertSame(game.getStack().peekAbility().getActivatingPlayer(), caster);
        settle(game);
        assertEquals(offered.size(), 1);
        assertSame(offered.get(0).getActivatingPlayer(), caster);
        assertEquals(encoder.getNetPower(), 2);
        assertEquals(caster.getSpellsCastThisTurn(), 1);
        assertEquals(defender.getSpellsCastThisTurn(), 1);
        assertEquals(caster.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
        assertEncoded(game, game.getCardState(encoded), encoder);
        if (copied) {
            assertEquals(pod.encoder.getNetPower(), 1);
            assertEquals(pod.actor.getLife(), 40);
            assertEquals(pod.enemy.getSpellsCastThisTurn(), 0);
            assertTrue(pod.game.getStack().isEmpty());
        }
    }

    private void verifyTemporaryCreature(boolean copied) {
        Pod original = pod(false, true);
        original.game.getAction().moveToGraveyard(original.encoder, null);
        for (int i = 0; i < 3; i++) addCardToZone("Island", original.actor, ZoneType.Battlefield);
        Card encoder = addCardToZone("Blinkmoth Nexus", original.actor, ZoneType.Battlefield);
        encoder.setSickness(false);
        animate(original.game, encoder, original.actor);
        Pod pod = new Pod(original.game, original.actor, original.enemy, encoder,
                original.target, original.spell, original.action);
        cast(pod);
        settle(pod.game);
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), encoder);
        pod.game.getEndOfTurn().executeUntil();
        pod.game.getAction().checkStateEffects(true);
        assertFalse(encoder.isCreature());
        assertEncoded(pod.game, pod.game.getCardState(pod.spell), encoder);
        Game game = pod.game;
        Player actor = pod.actor, enemy = pod.enemy;
        Card encoded = game.getCardState(pod.spell), target = pod.target;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            encoder = copier.find(encoder);
            encoded = copier.find(encoded);
            target = copier.find(target);
            actor = game.getPlayer(actor.getId());
            enemy = game.getPlayer(enemy.getId());
            assertFalse(encoder.isCreature());
            assertEncoded(game, encoded, encoder);
        }
        animate(game, encoder, actor);
        encoder.setTapped(false);
        List<SpellAbility> offered = new ArrayList<>();
        installCastingChoices(game, actor, target, offered, true);
        assertCipherTriggerAfterDamage(game, actor, enemy, encoder, 1);
        settle(game);
        assertEquals(offered.size(), 1);
        assertEquals(actor.getSpellsCastThisTurn(), 2);
        assertEncoded(game, game.getCardState(encoded), encoder);
        if (copied) {
            assertFalse(pod.encoder.isCreature());
            assertEquals(pod.enemy.getLife(), 40);
            assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
            assertTrue(pod.game.getStack().isEmpty());
        }
    }

    private void animate(Game game, Card land, Player actor) {
        SpellAbility action = land.getSpellAbilities().stream()
                .filter(option -> option.isActivatedAbility() && option.getApi() == ApiType.Animate)
                .findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtil.playStack(action, actor, game));
        settle(game);
        assertTrue(land.isCreature());
    }

    private void installCastingChoices(Game game, Player actor, Card target,
                                       List<SpellAbility> offered, boolean accept) {
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override public <T extends GameEntity> T chooseSingleEntityForEffect(
                    FCollectionView<T> options, DelayedReveal reveal, SpellAbility action,
                    String title, boolean optional, Player targetedPlayer, Map<String, Object> params) {
                if (action.getApi() == ApiType.Play) {
                    assertEquals(options.size(), 1, "The native cipher trigger offers its one encoded card");
                    assertSame(options.get(0).getGame(), game);
                    return options.get(0);
                }
                return super.chooseSingleEntityForEffect(options, reveal, action, title, optional, targetedPlayer, params);
            }

            @Override public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells,
                    SpellAbility action, String title, Map<String, Object> params) {
                if (action.getApi() == ApiType.GenericChoice) {
                    return spells.stream().filter(option -> option.hasParam("ChangeColorWord"))
                            .findFirst().orElseThrow();
                }
                return super.chooseSingleSpellForEffect(spells, action, title, params);
            }
            @Override public byte chooseColor(String title, SpellAbility action, ColorSet options) {
                return options.hasAnyColor(MagicColor.WHITE) ? MagicColor.WHITE : MagicColor.BLUE;
            }
            @Override public boolean confirmTrigger(WrappedAbility wrapper) {
                return wrapper.getWrappedAbility().getApi() == ApiType.Play || super.confirmTrigger(wrapper);
            }
            @Override public boolean confirmAction(SpellAbility action, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                return action.getApi() == ApiType.Play || action.getApi() == ApiType.Encode
                        || super.confirmAction(action, mode, message, options, card, params);
            }
            @Override public boolean playSaFromPlayEffect(SpellAbility action) {
                assertTrue(action.getHostCard().isToken(), "Cipher offers a copied card, not the exiled original");
                assertSame(action.getHostCard().getGame(), game);
                assertSame(action.getActivatingPlayer(), actor);
                assertTrue(game.getStack().isResolving(), "The native trigger grants this cast during resolution");
                assertTrue(action.getPayCosts().getTotalMana().isZero());
                offered.add(action);
                if (!accept) return false;
                action.resetTargets();
                action.getTargets().add(target);
                assertTrue(action.canTarget(target));
                assertTrue(action.isTargetNumberValid());
                return ComputerUtil.playStack(action, actor, game);
            }
        });
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

    private void assertEncoded(Game game, Card encoded, Card encoder) {
        assertTrue(encoded.isInZone(ZoneType.Exile));
        assertSame(encoded.getGame(), game);
        assertSame(encoder.getGame(), game);
        assertSame(encoded.getEncodingCard(), encoder, "The encoded source must refer to this game's creature");
        assertEquals(encoder.getEncodedCards().size(), 1);
        assertSame(encoder.getEncodedCards().get(0), encoded, "The creature must refer to this game's exile card");
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
