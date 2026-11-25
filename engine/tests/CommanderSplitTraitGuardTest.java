package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.CardStateName;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCloneStates;
import forge.game.card.CardState;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityDevotion;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Structural live-state controls; not printed-card or paid-history coverage. */
public class CommanderSplitTraitGuardTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream()
                .filter(player -> player.getName().equals("split-0")).findFirst().orElseThrow();
    }

    private Game board(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("split-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Live split trait state"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 12; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private void addTrait(Card host, CardState state, boolean replacement) {
        if (replacement) {
            ReplacementEffect effect = ReplacementHandler.parseReplacement(
                    "Event$ Tap | ActiveZones$ Battlefield | ValidCard$ Card.Self | Layer$ CantHappen",
                    host, true);
            state.addReplacementEffect(effect);
        } else {
            state.addStaticAbility(new StaticAbility(
                    "Mode$ Devotion | ValidPlayer$ You | Value$ 2", host, state));
        }
    }

    private void visible(Game game, Card host, CardState observed,
            boolean replacement, boolean expected) {
        if (replacement) {
            assertEquals(observed.getReplacementEffects().size(), expected ? 1 : 0);
            assertEquals(host.canTap(), !expected);
        } else {
            assertEquals(observed.getStaticAbilities().size(), expected ? 1 : 0);
            assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), expected ? 2 : 0);
        }
    }

    /** A live callback can replace the active clone map during the first split lookup. */
    private static final class SwitchingCloneStates extends CardCloneStates {
        private final Card host;
        private final CardCloneStates next;
        private final long timestamp;
        private int switches;

        SwitchingCloneStates(Card host, CardCloneStates next, long timestamp) {
            super(host, null);
            this.host = host;
            this.next = next;
            this.timestamp = timestamp;
        }

        @Override
        public boolean containsKey(Object key) {
            if (key == CardStateName.LeftSplit && switches == 0) {
                switches++;
                host.getCloneStates().put(timestamp, next);
            }
            return false;
        }
    }

    private void check(String mode, boolean replacement, int depth) {
        Game game = board(depth);
        Card host = actor(game).getCardsIn(ZoneType.Battlefield).getFirst();
        CardState observed = host.getCurrentState();
        visible(game, host, observed, replacement, false);
        CardStateName side = mode.endsWith("Right")
                ? CardStateName.RightSplit : CardStateName.LeftSplit;
        long timestamp = game.getNextTimestamp();
        if (mode.startsWith("original") || mode.equals("cloneHidesOriginal")) {
            host.addAlternateState(side, false);
            addTrait(host, host.getOriginalState(side), replacement);
            visible(game, host, observed, replacement, true);
            if (mode.equals("cloneHidesOriginal")) {
                CardCloneStates shadow = new CardCloneStates(host, null);
                shadow.put(CardStateName.Original, observed);
                host.getCloneStates().put(timestamp, shadow);
                visible(game, host, observed, replacement, false);
                host.getCloneStates().clear();
                visible(game, host, observed, replacement, true);
            }
            host.getStates().remove(side);
            visible(game, host, observed, replacement, false);
        } else {
            if (mode.equals("opaqueSwitch")) side = CardStateName.RightSplit;
            CardCloneStates shadow = new CardCloneStates(host, null);
            shadow.put(CardStateName.Original, observed);
            CardState split = new CardState(host, side);
            addTrait(host, split, replacement);
            shadow.put(side, split);
            if (mode.equals("opaqueSwitch")) {
                SwitchingCloneStates callback = new SwitchingCloneStates(host, shadow, timestamp);
                callback.put(CardStateName.Original, observed);
                host.getCloneStates().put(timestamp, callback);
                visible(game, host, observed, replacement, true);
                assertEquals(callback.switches, 1);
            } else {
                host.getCloneStates().put(timestamp, shadow);
                visible(game, host, observed, replacement, true);
            }
            shadow.remove(side);
            visible(game, host, observed, replacement, false);
            shadow.put(side, split);
            visible(game, host, observed, replacement, true);
            host.getCloneStates().clear();
            visible(game, host, observed, replacement, false);
        }
    }

    @Test
    public void originalLeftStaticDepth0() {
        check("originalLeft", false, 0);
    }

    @Test
    public void originalLeftStaticDepth1() {
        check("originalLeft", false, 1);
    }

    @Test
    public void originalLeftStaticDepth2() {
        check("originalLeft", false, 2);
    }

    @Test
    public void originalLeftReplacementDepth0() {
        check("originalLeft", true, 0);
    }

    @Test
    public void originalLeftReplacementDepth1() {
        check("originalLeft", true, 1);
    }

    @Test
    public void originalLeftReplacementDepth2() {
        check("originalLeft", true, 2);
    }

    @Test
    public void originalRightStaticDepth0() {
        check("originalRight", false, 0);
    }

    @Test
    public void originalRightStaticDepth1() {
        check("originalRight", false, 1);
    }

    @Test
    public void originalRightStaticDepth2() {
        check("originalRight", false, 2);
    }

    @Test
    public void originalRightReplacementDepth0() {
        check("originalRight", true, 0);
    }

    @Test
    public void originalRightReplacementDepth1() {
        check("originalRight", true, 1);
    }

    @Test
    public void originalRightReplacementDepth2() {
        check("originalRight", true, 2);
    }

    @Test
    public void cloneLeftStaticDepth0() {
        check("cloneLeft", false, 0);
    }

    @Test
    public void cloneLeftStaticDepth1() {
        check("cloneLeft", false, 1);
    }

    @Test
    public void cloneLeftStaticDepth2() {
        check("cloneLeft", false, 2);
    }

    @Test
    public void cloneLeftReplacementDepth0() {
        check("cloneLeft", true, 0);
    }

    @Test
    public void cloneLeftReplacementDepth1() {
        check("cloneLeft", true, 1);
    }

    @Test
    public void cloneLeftReplacementDepth2() {
        check("cloneLeft", true, 2);
    }

    @Test
    public void cloneRightStaticDepth0() {
        check("cloneRight", false, 0);
    }

    @Test
    public void cloneRightStaticDepth1() {
        check("cloneRight", false, 1);
    }

    @Test
    public void cloneRightStaticDepth2() {
        check("cloneRight", false, 2);
    }

    @Test
    public void cloneRightReplacementDepth0() {
        check("cloneRight", true, 0);
    }

    @Test
    public void cloneRightReplacementDepth1() {
        check("cloneRight", true, 1);
    }

    @Test
    public void cloneRightReplacementDepth2() {
        check("cloneRight", true, 2);
    }

    @Test
    public void cloneHidesOriginalStaticDepth0() {
        check("cloneHidesOriginal", false, 0);
    }

    @Test
    public void cloneHidesOriginalStaticDepth1() {
        check("cloneHidesOriginal", false, 1);
    }

    @Test
    public void cloneHidesOriginalStaticDepth2() {
        check("cloneHidesOriginal", false, 2);
    }

    @Test
    public void cloneHidesOriginalReplacementDepth0() {
        check("cloneHidesOriginal", true, 0);
    }

    @Test
    public void cloneHidesOriginalReplacementDepth1() {
        check("cloneHidesOriginal", true, 1);
    }

    @Test
    public void cloneHidesOriginalReplacementDepth2() {
        check("cloneHidesOriginal", true, 2);
    }

    @Test
    public void opaqueSwitchStaticDepth0() {
        check("opaqueSwitch", false, 0);
    }

    @Test
    public void opaqueSwitchStaticDepth1() {
        check("opaqueSwitch", false, 1);
    }

    @Test
    public void opaqueSwitchStaticDepth2() {
        check("opaqueSwitch", false, 2);
    }

    @Test
    public void opaqueSwitchReplacementDepth0() {
        check("opaqueSwitch", true, 0);
    }

    @Test
    public void opaqueSwitchReplacementDepth1() {
        check("opaqueSwitch", true, 1);
    }

    @Test
    public void opaqueSwitchReplacementDepth2() {
        check("opaqueSwitch", true, 2);
    }

}
