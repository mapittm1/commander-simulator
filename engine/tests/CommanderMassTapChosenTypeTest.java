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
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** A resolving instant must retain its chosen type in a native copied game. */
public class CommanderMassTapChosenTypeTest extends AITest {
    @Test public void aMidResolutionCopyRetainsTheChosenCardType() {
        Pod pod = pod(false);
        AiController brain = ((PlayerControllerAi) pod.actor.getController()).getAi();
        PlayerControllerAi controller = new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public AiController getAi() { return brain; }
            @Override public String chooseSomeType(String kind, SpellAbility action, Collection<String> options, boolean optional) {
                assertTrue(options.contains("Land")); return "Land";
            }
            @Override public boolean chooseBinary(SpellAbility action, String prompt, PlayerController.BinaryChoiceType kind, Boolean defaultValue) {
                if (action.getApi() == ApiType.TapOrUntapAll) {
                    assertTrue(action.getHostCard().isInZone(ZoneType.Stack));
                    assertEquals(action.getHostCard().getChosenType(), "Land");
                    forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
                    Game copy = copier.makeCopy(null, pod.actor);
                    Card copiedCard = copier.find(action.getHostCard());
                    assertSame(copiedCard.getGame(), copy);
                    assertEquals(copiedCard.getChosenType(), "Land", "A stack-zone choice must survive the game copy");
                    SpellAbility copiedAction = copier.find(action);
                    assertSame(copiedAction.getHostCard(), copiedCard);
                    assertEquals(copiedAction.getHostCard().getChosenType(), "Land");
                    return false;
                }
                return super.chooseBinary(action, prompt, kind, defaultValue);
            }
        };
        pod.actor.runWithController(() -> {
            assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, pod.action, null)); settle(pod.game);
        }, controller);
    }
    @Test public void chosenTypesSurviveAHandCopy() { copiedChoices(ZoneType.Hand); }
    @Test public void chosenTypesSurviveAStackCopy() { copiedChoices(ZoneType.Stack); }
    @Test public void chosenTypesSurviveABattlefieldCopy() { copiedChoices(ZoneType.Battlefield); }
    @Test public void chosenTypesSurviveAGraveyardCopy() { copiedChoices(ZoneType.Graveyard); }
    @Test public void chosenTypesSurviveAnExileCopy() { copiedChoices(ZoneType.Exile); }
    @Test public void chosenTypesSurviveALibraryCopy() { copiedChoices(ZoneType.Library); }

    /** Raw card-state contract; secondary choices here are explicit fixture state, not new Oracle text. */
    private void copiedChoices(ZoneType zone) {
        Pod pod = pod(false);
        Card original = addCardToZone("Arcane Adaptation", pod.actor, zone == ZoneType.Stack ? ZoneType.Hand : zone);
        if (zone == ZoneType.Stack) {
            pod.actor.getZone(ZoneType.Hand).remove(original); pod.game.getStackZone().add(original);
        }
        original.setChosenType("Merfolk"); original.setChosenType2("Wizard");
        forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
        Game game = copier.makeCopy(null, pod.actor); Card copy = copier.find(original);
        assertNotSame(copy, original); assertSame(copy.getGame(), game);
        assertEquals(copy.getChosenType(), "Merfolk"); assertEquals(copy.getChosenType2(), "Wizard");
        copy.setChosenType("Warrior"); copy.setChosenType2("Rogue");
        assertEquals(original.getChosenType(), "Merfolk"); assertEquals(original.getChosenType2(), "Wizard");
    }

    private Pod pod(boolean opposing) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("mass-tap-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid and triggered mass tap choices"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 4; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) addCardToZone("Mountain", actor, ZoneType.Battlefield).setTapped(true);
        Card lotus = addCardToZone("Gilded Lotus", actor, ZoneType.Battlefield); lotus.setTapped(true);
        Card ownCreature = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield); ownCreature.setSickness(false);
        Card enemyCreature = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield); enemyCreature.setSickness(false);
        Card spell = addCardToZone("Turnabout", actor, ZoneType.Hand);
        Card followup = addCardToZone("Rorix Bladewing", actor, ZoneType.Hand); actor.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        SpellAbility action = spell.getFirstSpellAbility(); action.setActivatingPlayer(actor);
        action.getSubAbility().getTargets().add(opposing ? enemy : actor);
        return new Pod(game, actor, enemy, lotus, ownCreature, enemyCreature, spell, followup, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Mass tap stack did not settle");
    }
    private record Pod(Game game, Player actor, Player enemy, Card lotus, Card ownCreature, Card enemyCreature, Card spell, Card followup, SpellAbility action) { }
}
