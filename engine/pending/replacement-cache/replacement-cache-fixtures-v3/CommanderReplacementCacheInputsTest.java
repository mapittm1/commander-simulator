package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.player.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** A cached replacement list must equal a fresh rebuild of the same state. */
public class CommanderReplacementCacheInputsTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream()
                .filter(player -> player.getName().equals("cache-0")).findFirst().orElseThrow();
    }

    private List<String> signature(CardState state, boolean rulesHost) {
        List<String> result = new ArrayList<>();
        for (ReplacementEffect effect : state.getReplacementEffects(rulesHost)) {
            result.add(effect.getMode() + ":" + new TreeMap<>(effect.getMapParams()));
        }
        Collections.sort(result);
        return result;
    }

    private void check(String subtype, boolean gain, boolean rulesHost, int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("cache-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game original = new Game(seats, rules, new Match(rules, seats, "Replacement cache inputs"));
        original.setNoGUIUser();
        original.setAge(GameStage.Play);
        Card originalCard = addCardToZone("Opt", actor(original), ZoneType.Hand);
        original.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, actor(original), 1);
        original.copyLastState();
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        Card card = actor(game).getCardsIn(ZoneType.Hand).getFirst();
        CardState state = card.getCurrentState();
        String initial = gain ? "Instant" : "Instant " + subtype;
        String changed = gain ? "Instant " + subtype : "Instant";
        state.setType(CardType.parse(initial, false));
        card.updateKeywordsCache();
        assertEquals(signature(state, rulesHost).size(), rulesHost && !gain ? 1 : 0);
        state.setType(CardType.parse(changed, false));
        List<String> cached = signature(state, rulesHost);
        // Refresh the keyword cache without changing any semantic input.
        card.updateKeywordsCache();
        List<String> rebuilt = signature(state, rulesHost);
        assertEquals(rebuilt.size(), rulesHost && gain ? 1 : 0);
        assertEquals(cached, rebuilt, subtype + " gain=" + gain + " rulesHost=" + rulesHost);
        if (depth > 0) assertFalse(originalCard.getCurrentState().getType().hasSubtype(subtype));
    }
    @Test public void adventureGainRulesDepth0() { check("Adventure", true, true, 0); }
    @Test public void adventureGainRulesDepth1() { check("Adventure", true, true, 1); }
    @Test public void adventureGainRulesDepth2() { check("Adventure", true, true, 2); }
    @Test public void adventureGainNoRulesDepth0() { check("Adventure", true, false, 0); }
    @Test public void adventureGainNoRulesDepth1() { check("Adventure", true, false, 1); }
    @Test public void adventureGainNoRulesDepth2() { check("Adventure", true, false, 2); }
    @Test public void adventureRemoveRulesDepth0() { check("Adventure", false, true, 0); }
    @Test public void adventureRemoveRulesDepth1() { check("Adventure", false, true, 1); }
    @Test public void adventureRemoveRulesDepth2() { check("Adventure", false, true, 2); }
    @Test public void adventureRemoveNoRulesDepth0() { check("Adventure", false, false, 0); }
    @Test public void adventureRemoveNoRulesDepth1() { check("Adventure", false, false, 1); }
    @Test public void adventureRemoveNoRulesDepth2() { check("Adventure", false, false, 2); }
    @Test public void omenGainRulesDepth0() { check("Omen", true, true, 0); }
    @Test public void omenGainRulesDepth1() { check("Omen", true, true, 1); }
    @Test public void omenGainRulesDepth2() { check("Omen", true, true, 2); }
    @Test public void omenGainNoRulesDepth0() { check("Omen", true, false, 0); }
    @Test public void omenGainNoRulesDepth1() { check("Omen", true, false, 1); }
    @Test public void omenGainNoRulesDepth2() { check("Omen", true, false, 2); }
    @Test public void omenRemoveRulesDepth0() { check("Omen", false, true, 0); }
    @Test public void omenRemoveRulesDepth1() { check("Omen", false, true, 1); }
    @Test public void omenRemoveRulesDepth2() { check("Omen", false, true, 2); }
    @Test public void omenRemoveNoRulesDepth0() { check("Omen", false, false, 0); }
    @Test public void omenRemoveNoRulesDepth1() { check("Omen", false, false, 1); }
    @Test public void omenRemoveNoRulesDepth2() { check("Omen", false, false, 2); }
    @Test public void vengeanceGainRulesDepth0() { check("Vengeance", true, true, 0); }
    @Test public void vengeanceGainRulesDepth1() { check("Vengeance", true, true, 1); }
    @Test public void vengeanceGainRulesDepth2() { check("Vengeance", true, true, 2); }
    @Test public void vengeanceGainNoRulesDepth0() { check("Vengeance", true, false, 0); }
    @Test public void vengeanceGainNoRulesDepth1() { check("Vengeance", true, false, 1); }
    @Test public void vengeanceGainNoRulesDepth2() { check("Vengeance", true, false, 2); }
    @Test public void vengeanceRemoveRulesDepth0() { check("Vengeance", false, true, 0); }
    @Test public void vengeanceRemoveRulesDepth1() { check("Vengeance", false, true, 1); }
    @Test public void vengeanceRemoveRulesDepth2() { check("Vengeance", false, true, 2); }
    @Test public void vengeanceRemoveNoRulesDepth0() { check("Vengeance", false, false, 0); }
    @Test public void vengeanceRemoveNoRulesDepth1() { check("Vengeance", false, false, 1); }
    @Test public void vengeanceRemoveNoRulesDepth2() { check("Vengeance", false, false, 2); }
}
