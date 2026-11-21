package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.ability.ApiType;
import forge.game.player.DelayedReveal;
import forge.game.replacement.ReplacementEffect;
import forge.util.collect.FCollectionView;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native copy entry and crew; supplied copy target, library order and discover-to-hand choice. */
public class CommanderDiscoverVehicleTest extends AITest {
    @Test
    public void anUncrewedCopyUsesZeroToughness() {
        Pod pod = pod();
        enter(pod);
        assertFalse(pod.game.getCardState(pod.host).isCreature());
        assertEquals(pod.game.getCardState(pod.host).getNetToughness(), 0);
        settle(pod.game);
        assertTrue(zero(pod).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
    }

    @Test
    public void crewingBeforeDiscoverUsesCreatureToughness() {
        Pod pod = pod();
        enter(pod);
        crew(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Hand));
        assertTrue(zero(pod).isInZone(ZoneType.Library));
    }

    @Test
    public void crewingACopyDoesNotChangeTheOriginalDiscoverNumber() {
        Pod pod = pod();
        enter(pod);
        Pod fork = snapshot(pod);
        crew(fork);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.four).isInZone(ZoneType.Hand));
        assertFalse(pod.game.getCardState(pod.host).isCreature());
        settle(pod.game);
        assertTrue(zero(pod).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
    }

    @Test
    public void aDepartedUncrewedCopyRetainsZeroToughness() {
        Pod pod = pod();
        enter(pod);
        disenchant(pod);
        settle(pod.game);
        assertTrue(zero(pod).isInZone(ZoneType.Hand));
    }

    @Test
    public void aDepartedCrewedCopyRetainsCreatureToughness() {
        Pod pod = pod();
        enter(pod);
        crew(pod);
        disenchant(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Hand));
        assertTrue(zero(pod).isInZone(ZoneType.Library));
    }

    @Test
    public void copiedCrewedDepartureKeepsItsLastCreatureToughness() {
        Pod pod = pod();
        enter(pod);
        crew(pod);
        disenchant(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.four).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Hand));
    }

    @Test
    public void originalCrewAndDepartureDoNotChangeAnUncrewedCopy() {
        Pod pod = pod();
        enter(pod);
        Pod fork = snapshot(pod);
        crew(pod);
        disenchant(pod);
        settle(fork.game);
        assertTrue(zero(fork).isInZone(ZoneType.Hand));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Hand));
    }

    @Test
    public void originalTurnEndingDoesNotExpireCrewInTheCopiedGame() {
        Pod pod = pod();
        enter(pod);
        crew(pod);
        Pod fork = snapshot(pod);
        SpellAbility stop = addCardToZone("Time Stop", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        stop.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(stop, pod.actor, pod.game));
        settle(pod.game);
        assertFalse(pod.game.getCardState(pod.host).isCreature());
        assertTrue(fork.game.getCardState(fork.host).isCreature());
        assertEquals(fork.game.getCardState(fork.host).getNetToughness(), 4);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.four).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
    }

    private void disenchant(Pod pod) {
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility spell = addCardToZone("Disenchant", opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(pod.game.getCardState(pod.host));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Graveyard));
        assertEquals(pod.game.getStack().size(), 1);
    }

    private Card zero(Pod pod) {
        return pod.game.getCardsInGame().stream()
                .filter(card -> card.getOwner().equals(pod.actor) && card.getName().equals("Memnite"))
                .findFirst().orElseThrow();
    }

    private void crew(Pod pod) {
        addCardToZone("Colossal Dreadmaw", pod.actor, ZoneType.Battlefield);
        SpellAbility ability = pod.game.getCardState(pod.host).getSpellAbilities().stream()
                .filter(SpellAbility::isCrew).findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isCreature());
        assertEquals(pod.game.getCardState(pod.host).getNetToughness(), 4);
        assertEquals(pod.game.getStack().size(), 1);
    }
    private Pod snapshot(Pod original) {
        GameCopier copier = new GameCopier(original.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()), find(copy, original.host),
                find(copy, original.six), find(copy, original.four));
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }
    private Card find(Game game, Card card) {
        return game.getCardsInGame().stream().filter(candidate -> candidate.getId() == card.getId())
                .findFirst().orElseThrow();
    }
    private void control(Pod pod) {
        Game game = pod.game;
        Player actor = pod.actor;
        Card host = pod.host;
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmReplacementEffect(ReplacementEffect effect, SpellAbility ability,
                    GameEntity affected, String question) {
                return effect.getHostCard().getId() == host.getId()
                        || super.confirmReplacementEffect(effect, ability, affected, question);
            }
            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> choices,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                if (ability.getHostCard().getId() == host.getId() && ability.getApi() == ApiType.Clone) {
                    return choices.stream().filter(choice -> choice instanceof Card card
                            && card.getName().equals("Pantlaza, Sun-Favored")).findFirst().orElseThrow();
                }
                return super.chooseSingleEntityForEffect(choices, delayed, ability, title, optional, targetedPlayer, params);
            }
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else super.orderAndPlaySimultaneousSa(List.of(ability));
                }
            }
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().getId() == host.getId() || super.confirmTrigger(ability);
            }
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (ability.getHostCard().getId() == host.getId() && params != null && params.get("Card") != null) {
                    return false;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }
        });
    }
    private void enter(Pod pod) {
        SpellAbility spell = pod.host.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Battlefield));
        assertEquals(pod.game.getStack().size(), 1);
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Discover toughness timing"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            if (player != actor) {
                for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
                for (int i = 0; i < 8; i++) addCardToZone("Swamp", player, ZoneType.Battlefield);
                for (int i = 0; i < 2; i++) addCardToZone("Plains", player, ZoneType.Battlefield);
            }
        }
        for (int i = 0; i < 8; i++) addCardToZone("Forest", actor, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
            addCardToZone("Plains", actor, ZoneType.Battlefield);
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        Card six = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Library);
        Card four = addCardToZone("Solemn Simulacrum", actor, ZoneType.Library);
        addCardToZone("Memnite", actor, ZoneType.Library);
        addCardToZone("Forest", actor, ZoneType.Library);
        addCardToZone("Pantlaza, Sun-Favored", game.getPlayers().get(1), ZoneType.Battlefield);
        Card host = addCardToZone("Imposter Mech", actor, ZoneType.Hand);
        Pod pod = new Pod(game, actor, host, six, four);
        control(pod);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), six.getId());
        return pod;
    }
    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }
    private void settle(Game game) {
        queue(game);
        for (int step = 0; step < 16 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
            queue(game);
        }
        assertTrue(game.getStack().isEmpty());
    }
    private record Pod(Game game, Player actor, Card host, Card six, Card four) {}
}
