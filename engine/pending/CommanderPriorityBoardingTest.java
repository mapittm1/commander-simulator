package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
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

/** Supplied battlefield/decisions and native paid die rolls and responses. */
public class CommanderPriorityBoardingTest extends AITest {
    @Test
    public void exileWaitsForASeparateReflexiveTrigger() {
        Pod pod = pod();
        roll(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library),
                "The reveal resolves before the separately counterable exile choice");
        assertEquals(pod.game.getStack().size(), 1);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void anAcceptedRevealEventuallyExilesTheCard() {
        Pod pod = pod();
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getNumRollsThisTurn(), 1);
    }
    @Test
    public void decliningTheRevealPreservesTheLaterChoice() {
        Pod pod = pod();
        pod.decline[0] = true;
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        pod.decline[0] = false;
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
    }
    @Test
    public void counteringTheRevealPreservesTheLaterChoice() {
        Pod pod = pod();
        roll(pod);
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Exile));
    }
    @Test
    public void counteringTheExileKeepsTheCardAndUsesTheRevealAllowance() {
        Pod pod = pod();
        roll(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1, "The reflexive trigger needs its own stack entry");
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
        untap(pod);
        rollWithoutReveal(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.top).isInZone(ZoneType.Library));
    }
    @Test
    public void anEmptyLibraryDoesNotConsumeTheRevealAllowance() {
        Pod pod = pod(true);
        Card elixir = addCardToZone("Elixir of Immortality", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        roll(pod);
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 0);
        SpellAbility refill = elixir.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        refill.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(refill, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 1);
        assertEquals(pod.actor.getLife(), 45);
        untap(pod);
        roll(pod);
        settle(pod.game);
        assertEquals(pod.actor.getNumRollsThisTurn(), 2);
    }
    private void counterTop(Pod pod) {
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility spell = addCardToZone("Disallow", opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
    }
    private void untap(Pod pod) {
        SpellAbility spell = addCardToZone("Dramatic Reversal", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(!pod.pouch.isTapped());
    }
    private void roll(Pod pod) {
        rollWithoutReveal(pod);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getName(), "Priority Boarding");
    }
    private void rollWithoutReveal(Pod pod) {
        SpellAbility ability = pod.pouch.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.RollDice)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.pouch.isTapped());
    }
    private Pod pod() { return pod(false); }
    private Pod pod(boolean emptyLibrary) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Priority Boarding timing"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            if (!emptyLibrary || player != actor) {
                for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
            }
            for (int i = 0; i < 12; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        Card top = emptyLibrary ? null : actor.getCardsIn(ZoneType.Library).getFirst();
        Card host = addCardToZone("Priority Boarding", actor, ZoneType.Battlefield);
        Card pouch = addCardToZone("Component Pouch", actor, ZoneType.Battlefield);
        boolean[] decline = {false};
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
                return ability.getHostCard().getId() == host.getId() ? !decline[0] : super.confirmTrigger(ability);
            }
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                return ability.getHostCard().getId() == host.getId()
                        || super.confirmAction(ability, mode, message, options, card, params);
            }
        });
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, host, pouch, top, decline);
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
    private record Pod(Game game, Player actor, Card host, Card pouch, Card top, boolean[] decline) {}
}
