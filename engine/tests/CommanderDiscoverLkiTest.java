package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
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
import static org.testng.Assert.assertTrue;

/** Native paid entry, pump and removal; supplied library order and discover choice. */
public class CommanderDiscoverLkiTest extends AITest {
    @Test
    public void unchangedToughnessFindsTheFourManaCard() {
        Pod pod = pod();
        enter(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Library));
    }
    @Test
    public void aResponseChangesTheToughnessUsedAtResolution() {
        Pod pod = pod();
        enter(pod);
        grow(pod);
        assertEquals(pod.game.getCardState(pod.host).getNetToughness(), 7);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
    }
    @Test
    public void aDepartedDinosaurUsesItsLastBattlefieldToughness() {
        Pod pod = pod();
        enter(pod);
        grow(pod);
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility removal = addCardToZone("Murder", opponent, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(opponent);
        removal.getTargets().add(pod.game.getCardState(pod.host));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, opponent, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Hand),
                "The last battlefield toughness was seven rather than its printed four");
        assertTrue(pod.game.getCardState(pod.four).isInZone(ZoneType.Library));
    }
    @Test
    public void aLaterResponseDoesNotReplaceTheLastKnownToughness() {
        Pod pod = pod();
        enter(pod);
        grow(pod);
        kill(pod);
        SpellAbility spell = addCardToZone("Lightning Bolt", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.game.getPlayers().get(1));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Hand));
    }
    @Test
    public void copiedGamesRetainTheDepartedCreaturesLastKnownToughness() {
        Pod pod = pod();
        enter(pod);
        grow(pod);
        kill(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.six).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Library));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Hand));
    }
    @Test
    public void endingTheOriginalTurnDoesNotChangeTheCopiedLastKnownValue() {
        Pod pod = pod();
        enter(pod);
        grow(pod);
        kill(pod);
        Pod fork = snapshot(pod);
        SpellAbility stop = addCardToZone("Time Stop", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        stop.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(stop, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(fork.game.getStack().size(), 1);
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.six).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.six).isInZone(ZoneType.Library));
    }
    private void kill(Pod pod) {
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility spell = addCardToZone("Murder", opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(pod.game.getCardState(pod.host));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Graveyard));
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
    private void grow(Pod pod) {
        SpellAbility spell = addCardToZone("Giant Growth", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.game.getCardState(pod.host));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
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
        Card host = addCardToZone("Pantlaza, Sun-Favored", actor, ZoneType.Hand);
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
