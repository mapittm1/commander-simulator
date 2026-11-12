package forge.ai;

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
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Exercise actual Beam me up payments and the replacement as the spell leaves the stack. */
public class StarTrekBeamResolutionTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent, Card draw, Card spell) {
        void assertZone(Card card, ZoneType zone) {
            assertEquals(game.getZoneOf(game.getCardState(card)).getZoneType(), zone);
        }
    }

    private Pod pod(ZoneType origin) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Beam me up"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        Card draw = addCardToZone("Forest", actor, ZoneType.Library);
        Card spell = addCardToZone("Open Communications", actor, origin);
        game.getAction().checkStateEffects(true);
        return new Pod(game, actor, opponent, draw, spell);
    }

    private void payBeam(Pod pod) {
        Card creature = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        SpellAbility beam = pod.spell.getAllPossibleAbilities(pod.actor, true).stream()
                .filter(SpellAbility::isBeamMeUp).findFirst().orElseThrow();
        beam.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, beam, null));
        pod.assertZone(creature, ZoneType.Hand);
        pod.assertZone(pod.spell, ZoneType.Stack);
        assertTrue(pod.game.getStack().peekAbility().isBeamMeUp());
        assertEquals(pod.game.getStack().peekAbility().getPayingMana().size(), 3);
    }

    private void answer(Pod pod, String name) {
        addCardToZone("Island", pod.opponent, ZoneType.Battlefield);
        addCardToZone("Tundra", pod.opponent, ZoneType.Battlefield);
        Card answer = addCardToZone(name, pod.opponent, ZoneType.Hand);
        SpellAbility spell = answer.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.opponent);
        SpellAbility target = pod.game.getStack().peekAbility();
        assertTrue(spell.canTarget(target));
        spell.getTargets().add(target);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.opponent, spell, null));
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getId(), answer.getId());
        pod.game.getStack().resolveStack();
    }

    @Test
    public void paidBeamDrawsAndExilesOnResolution() {
        Pod pod = pod(ZoneType.Graveyard);
        payBeam(pod);
        pod.game.getStack().resolveStack();
        pod.assertZone(pod.draw, ZoneType.Hand);
        pod.assertZone(pod.spell, ZoneType.Exile);
    }

    @Test
    public void paidBeamExilesWhenCountered() {
        Pod pod = pod(ZoneType.Graveyard);
        payBeam(pod);
        answer(pod, "Counterspell");
        pod.assertZone(pod.draw, ZoneType.Library);
        pod.assertZone(pod.spell, ZoneType.Exile);
        assertTrue(pod.game.getStack().isEmpty());
    }

    @Test
    public void paidBeamExilesInsteadOfReturningToHand() {
        Pod pod = pod(ZoneType.Graveyard);
        addCardToZone("Plains", pod.opponent, ZoneType.Library);
        payBeam(pod);
        answer(pod, "Reprieve");
        pod.assertZone(pod.draw, ZoneType.Library);
        pod.assertZone(pod.spell, ZoneType.Exile);
        assertTrue(pod.game.getStack().isEmpty());
    }

    @Test
    public void regularHandCastGoesToGraveyard() {
        Pod pod = pod(ZoneType.Hand);
        SpellAbility spell = pod.spell.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null));
        pod.game.getStack().resolveStack();
        pod.assertZone(pod.draw, ZoneType.Hand);
        pod.assertZone(pod.spell, ZoneType.Graveyard);
    }
}
