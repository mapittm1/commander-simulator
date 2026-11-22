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
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Held-out repeated payment through the normal native gameplay entry point. */
public class CommanderRepeatedDiscardEntrypointTest extends AITest {
    @Test public void repeatedActivationsChooseFreshDiscardPayments() {
        Pod pod = pod("Mind Over Matter");
        chooseAndPlay(pod, pod.source, ApiType.TapOrUntap);
        assertTrue(pod.game.getCardState(pod.fodder).isInZone(ZoneType.Graveyard));
        assertFalse(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action));
        chooseAndPlay(pod, pod.followup, null);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
        assertTrue(pod.mana.isTapped());

        Card secondPayment = addCardToZone("Forest", pod.actor, ZoneType.Hand);
        Card secondFollowup = addCardToZone("Ball Lightning", pod.actor, ZoneType.Hand);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        chooseAndPlay(pod, pod.source, ApiType.TapOrUntap);
        assertTrue(pod.game.getCardState(secondPayment).isInZone(ZoneType.Graveyard));
        assertFalse(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action));
        chooseAndPlay(pod, secondFollowup, null);
        assertTrue(pod.game.getCardState(secondFollowup).isInZone(ZoneType.Battlefield));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2);
        assertEquals(pod.actor.getDiscardedThisTurn().size(), 2);
    }

    private void chooseAndPlay(Pod pod, Card host, ApiType api) {
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertNotNull(actions);
        SpellAbility selected = actions.stream().filter(action -> action.getHostCard().getId() == host.getId()
                && (api == null || action.getApi() == api)).findFirst().orElseThrow();
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, selected, null));
        settle(pod.game);
    }
    private Pod pod(String sourceName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("activated-untap-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid activated untaps"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        boolean strings = sourceName.equals("Puppet Strings");
        for (int i = 0; i < (strings ? 2 : 1); i++) addCardToZone("Wastes", actor, ZoneType.Battlefield);
        if (sourceName.equals("Trickster Mage")) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card source = addCardToZone(sourceName, actor, ZoneType.Battlefield); source.setSickness(false);
        Card mana = addCardToZone(strings ? "Priest of Titania" : "Gilded Lotus", actor, ZoneType.Battlefield); mana.setSickness(false); mana.setTapped(true);
        if (strings) for (int i = 0; i < 3; i++) addCardToZone("Elvish Visionary", actor, ZoneType.Battlefield);
        Card followup = addCardToZone(strings ? "Questing Beast" : "Hellrider", actor, ZoneType.Hand);
        Card fodder = (sourceName.equals("Mind Over Matter") || sourceName.equals("Trickster Mage")) ? addCardToZone("Forest", actor, ZoneType.Hand) : null;
        if (sourceName.equals("Mind Over Matter") || sourceName.equals("Trickster Mage")) actor.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        SpellAbility action = source.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.TapOrUntap).findFirst().orElseThrow();
        action.setActivatingPlayer(actor); return new Pod(game, actor, source, mana, followup, fodder, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Activated untap stack did not settle");
    }
    private record Pod(Game game, Player actor, Card source, Card mana, Card followup, Card fodder, SpellAbility action) { }
}
