package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Exercise printed battlefield and graveyard assimilation through native triggers. */
public class StarTrekAssimilateProbeTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }

        void stabilize() {
            game.getAction().checkStaticAbilities();
            game.getTriggerHandler().resetActiveTriggers();
        }
    }

    private Pod pod(PhaseType phase) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Star Trek Assimilate"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(phase, actor, 1);
        return new Pod(game, actor, game.getPlayers().get(1));
    }

    private void advanceChapter(Pod pod, Card saga) {
        GameEntityCounterTable counters = new GameEntityCounterTable();
        saga.addCounter(CounterEnumType.LORE, 1, pod.actor, counters);
        counters.replaceCounterEffect(pod.game, null);
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getStack().peekAbility().getHostCard() == saga);
        pod.game.getStack().resolveStack();
    }

    private void assertAssimilated(Card creature, Pod pod) {
        assertTrue(creature.getOwner() == pod.opponent);
        assertTrue(creature.getController() == pod.actor);
        assertTrue(creature.isArtifact() && creature.isCreature());
        assertEquals(creature.getType().getCreatureTypes().size(), 1);
        assertTrue(creature.getType().hasCreatureType("Borg"));
        assertEquals(creature.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void assimilatingDryadArborKeepsForestAndSagaReturnsItToOwner() {
        Pod pod = pod(PhaseType.MAIN1);
        Card saga = addCardToZone("The Best of Both Worlds", pod.actor, ZoneType.Battlefield);
        Card arbor = addCardToZone("Dryad Arbor", pod.opponent, ZoneType.Battlefield);
        pod.stabilize();

        advanceChapter(pod, saga);
        assertTrue(pod.game.getCardState(arbor) == arbor);
        assertAssimilated(arbor, pod);
        assertTrue(arbor.isLand() && arbor.getType().hasSubtype("Forest"));
        assertFalse(arbor.getType().hasCreatureType("Dryad"));
        assertTrue(saga.isRemembered(arbor));

        advanceChapter(pod, saga);
        advanceChapter(pod, saga);
        Card returned = pod.game.getCardState(arbor);
        assertTrue(returned != arbor && returned.isInPlay());
        assertTrue(returned.getController() == pod.opponent);
        assertTrue(returned.isLand() && returned.getType().hasSubtype("Forest"));
        assertTrue(returned.getType().hasCreatureType("Dryad"));
        assertFalse(returned.isArtifact() || returned.getType().hasCreatureType("Borg"));
        assertEquals(returned.getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void graveyardAssimilationEntersAsArtifactAndTriggersFireweaver() {
        Pod pod = pod(PhaseType.END_OF_TURN);
        Card source = addCardToZone("Death Is Irrelevant", pod.actor, ZoneType.Battlefield);
        Card weaver = addCardToZone("Reckless Fireweaver", pod.actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", pod.opponent, ZoneType.Graveyard);
        pod.stabilize();
        int life = pod.opponent.getLife();
        long graveyardTimestamp = bear.getGameTimestamp();

        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getStack().peekAbility().getHostCard() == source);
        pod.game.getStack().resolveStack();

        Card entered = pod.game.getCardState(bear);
        assertTrue(entered.isInPlay());
        assertTrue(entered.getGameTimestamp() != graveyardTimestamp);
        assertAssimilated(entered, pod);
        assertFalse(entered.getType().hasCreatureType("Bear"));
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getStack().peekAbility().getHostCard() == weaver);
        pod.game.getStack().resolveStack();
        assertEquals(pod.opponent.getLife(), life - 1);
    }

    @Test
    public void cagePreventsGraveyardAssimilationAndKeepsOwnerAndZone() {
        Pod pod = pod(PhaseType.END_OF_TURN);
        addCardToZone("Death Is Irrelevant", pod.actor, ZoneType.Battlefield);
        addCardToZone("Grafdigger's Cage", pod.opponent, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", pod.opponent, ZoneType.Graveyard);
        pod.stabilize();

        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        pod.game.getStack().resolveStack();
        assertTrue(pod.game.getZoneOf(bear).is(ZoneType.Graveyard));
        assertTrue(bear.getController() == pod.opponent);
        assertFalse(bear.isArtifact());
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 0);
    }

    @Test
    public void assimilatingFromThreeOpponentsCreatesOneBatchedArtifactTrigger() {
        Pod pod = pod(PhaseType.MAIN1);
        Card witness = addCardToZone("Ingenious Artillerist", pod.actor, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Swamp", pod.actor, ZoneType.Battlefield);
        }
        Card spellCard = addCardToZone("Assimilate the Wounded", pod.actor, ZoneType.Hand);
        Card ineligible = addCardToZone("Silvercoat Lion", pod.opponent, ZoneType.Graveyard);
        List<Player> opponents = new ArrayList<>(pod.actor.getOpponents());
        List<Card> targets = new ArrayList<>();
        for (Player opponent : opponents) {
            Card creature = addCardToZone("Grizzly Bears", opponent, ZoneType.Battlefield);
            targets.add(pod.game.getAction().moveToGraveyard(creature, null));
        }
        pod.stabilize();
        List<Integer> before = opponents.stream().map(Player::getLife).toList();

        SpellAbility spell = spellCard.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertFalse(spell.canTarget(ineligible));
        for (Card target : targets) {
            assertTrue(spell.canTarget(target));
            spell.getTargets().add(target);
        }
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        assertEquals(pod.game.getStack().size(), 1);
        pod.game.getStack().resolveStack();
        for (int index = 0; index < targets.size(); index++) {
            Card creature = pod.game.getCardState(targets.get(index));
            assertTrue(creature.isInPlay() && creature.isArtifact());
            assertTrue(creature.getOwner() == opponents.get(index) && creature.getController() == pod.actor);
            assertTrue(creature.getType().hasCreatureType("Borg"));
            assertEquals(creature.getCounters(CounterEnumType.P1P1), 1);
        }
        assertTrue(ineligible.isInZone(ZoneType.Graveyard));
        pod.collectTriggers();
        assertEquals(pod.game.getStack().size(), 1);
        assertTrue(pod.game.getStack().peekAbility().getHostCard() == witness);
        pod.game.getStack().resolveStack();
        for (int index = 0; index < opponents.size(); index++) {
            assertEquals(opponents.get(index).getLife(), before.get(index).intValue() - 3);
        }
    }
}
