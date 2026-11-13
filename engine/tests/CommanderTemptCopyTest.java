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
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderTemptCopyTest extends AITest {
    @Test
    public void activeOpponentChoosesFirstAndCasterCopiesResolveFirst() {
        verifyCopies(false, List.of(2, 3, 0), List.of(1, 1, 1, 1, 0, 3, 2));
    }

    @Test
    public void reversingTurnOrderChangesOpponentChoiceAndResolutionOrder() {
        verifyCopies(true, List.of(2, 0, 3), List.of(1, 1, 1, 1, 3, 0, 2));
    }

    private void verifyCopies(boolean reversed, List<Integer> expectedDecisions, List<Integer> expectedCopies) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Tempt with Mayhem"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player caster = game.getPlayers().get(1);
        Player active = game.getPlayers().get(2);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, active, 1);
        if (reversed) {
            game.reverseTurnOrder();
        }

        List<Integer> decisions = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            if (player == caster) {
                continue;
            }
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                        String message, List<String> options, Card cardToShow, Map<String, Object> params) {
                    if ("Do you want to copy the targeted spell?".equals(message)) {
                        int seat = game.getPlayers().indexOf(player);
                        decisions.add(seat);
                        return true;
                    }
                    return super.confirmAction(ability, mode, message, options, cardToShow, params);
                }
            });
        }
        addCardToZone("Island", active, ZoneType.Battlefield);
        addCardToZone("Forest", active, ZoneType.Library);
        Card opt = addCardToZone("Opt", active, ZoneType.Hand);
        SpellAbility original = opt.getFirstSpellAbility();
        original.setActivatingPlayer(active);
        assertTrue(ComputerUtil.playStack(original, active, game));

        for (int index = 0; index < 3; index++) {
            addCardToZone("Mountain", caster, ZoneType.Battlefield);
        }
        Card tempt = addCardToZone("Tempt with Mayhem", caster, ZoneType.Hand);
        SpellAbility offer = tempt.getFirstSpellAbility();
        offer.setActivatingPlayer(caster);
        assertTrue(offer.canTarget(game.getStack().peekAbility()));
        offer.getTargets().add(game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(offer, caster, game));
        assertEquals(game.getStack().size(), 2);

        game.getStack().resolveStack();

        assertEquals(decisions, expectedDecisions);
        List<Integer> copyControllers = new ArrayList<>();
        for (var entry : game.getStack()) {
            SpellAbility spell = entry.getSpellAbility();
            assertEquals(spell.getHostCard().getName(), "Opt");
            if (spell.isCopied()) {
                copyControllers.add(game.getPlayers().indexOf(spell.getActivatingPlayer()));
            }
        }
        assertEquals(copyControllers, expectedCopies);
        assertEquals(game.getStack().size(), expectedCopies.size() + 1);
    }
}
