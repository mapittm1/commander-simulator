package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.keyword.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityDevotion;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Live positive keyword traits retain list contracts, actual effects and copied hosts. */
public class CommanderKeywordStaticCacheTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Card creature(Player player) { return player.getCreaturesInPlay().get(0); }

    private Game board(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("keyword-static-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Keyword static contracts"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40);
            for (int i = 0; i < 12; i++) addCardToZone("Forest", player, ZoneType.Library);
            addCardToZone("Grizzly Bears", player, ZoneType.Battlefield).setSickness(false);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers();
        return copy(game, depth);
    }

    private Game copy(Game game, int depth) {
        for (int i = 0; i < depth; i++) {
            game.copyLastState();
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        return game;
    }

    private StaticAbility devotion(Card host, int value) {
        return new StaticAbility("Mode$ Devotion | ValidPlayer$ You | Value$ " + value,
                host, host.getCurrentState());
    }

    private void observe(Game game, int expected) {
        assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), expected,
                "The live gathered abilities have their actual native effect");
    }

    private void check(String mode) {
        Game game = board(2); Card host = creature(actor(game));
        KeywordCollection keywords = new KeywordCollection();
        KeywordInterface flying = keywords.add("Flying"); flying.createTraits(host, true);
        host.getCurrentState().setCachedKeywords(keywords);
        List<StaticAbility> list = (List<StaticAbility>) flying.getStaticAbilities();
        assertFalse(list.isEmpty(), "Use a genuinely positive native Flying trait");
        StaticAbility flight = list.get(0), first = devotion(host, 2), second = devotion(host, 3);
        assertTrue(host.getStaticAbilities().contains(flight)); observe(game, 0);
        if (mode.equals("combat")) {
            Card blocker = creature(game.getPlayers().get(1));
            Combat combat = new Combat(actor(game)); game.getPhaseHandler().setCombat(combat);
            combat.addAttacker(host, game.getPlayers().get(1));
            game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor(game), false, 5);
            assertFalse(CombatUtil.canBlock(host, blocker, combat));
            list.clear(); assertTrue(CombatUtil.canBlock(host, blocker, combat));
            list.add(flight); assertFalse(CombatUtil.canBlock(host, blocker, combat)); return;
        }
        if (mode.equals("membership")) {
            KeywordInterface extra = Keyword.getInstance("Haste"); extra.getStaticAbilities().add(first);
            Collection<KeywordInterface> view = keywords.getValues(Keyword.HASTE);
            observe(game, 0); assertTrue(view.add(extra)); observe(game, 2);
            assertTrue(view.remove(extra)); observe(game, 0); view.add(extra); observe(game, 2); return;
        }
        list.add(first); observe(game, 2);
        switch (mode) {
            case "append" -> { list.remove(first); observe(game, 0); list.add(first); observe(game, 2); }
            case "set" -> { list.set(1, second); observe(game, 3); list.set(1, first); observe(game, 2); }
            case "iterator" -> {
                ListIterator<StaticAbility> iterator = list.listIterator(1); assertSame(iterator.next(), first);
                iterator.set(second); observe(game, 3); iterator.remove(); observe(game, 0);
                iterator.add(first); observe(game, 2);
            }
            case "sublist" -> {
                list.add(second); observe(game, 5); list.subList(1, list.size()).clear(); observe(game, 0);
                list.subList(1, 1).add(first); observe(game, 2);
            }
            case "replace" -> {
                list.replaceAll(value -> value == first ? second : value); observe(game, 3);
                list.replaceAll(value -> value == second ? first : value); observe(game, 2);
            }
            case "sort" -> {
                list.add(second); observe(game, 5); list.sort((left, right) -> Integer.compare(right.getId(), left.getId()));
                assertEquals(new ArrayList<>(host.getStaticAbilities()), list, "Preserve gathered ordering");
            }
            case "result" -> {
                List<StaticAbility> result = (List<StaticAbility>) host.getStaticAbilities();
                result.clear(); result.add(second); observe(game, 2);
                assertTrue(host.getStaticAbilities().contains(first)); assertFalse(list.contains(second));
            }
            case "parameter" -> { first.getMapParams().put("Value", "3"); observe(game, 3); }
            case "duplicates" -> {
                list.add(first); list.add(null); observe(game, 2); list.remove(first); observe(game, 2);
                list.remove(first); observe(game, 0); list.remove(null); list.add(first); observe(game, 2);
            }
            default -> throw new AssertionError(mode);
        }
    }

    private static final class MutatingGetter extends SimpleKeyword {
        final Card host; final StaticAbility effect; final List<StaticAbility> values = new ArrayList<>();
        boolean fired;
        MutatingGetter(Card host, StaticAbility effect) {
            this.host = host; this.effect = effect; initialize("Flying", Keyword.FLYING, "");
        }
        @Override public Collection<StaticAbility> getStaticAbilities() {
            if (!fired) { fired = true; host.addStaticAbility(effect); }
            return values;
        }
    }

    @Test public void nativeFlyingChangesActualBlocking() { check("combat"); }
    @Test public void appendedTraitsStayLive() { check("append"); }
    @Test public void sameLengthReplacementStaysLive() { check("set"); }
    @Test public void iteratorMutationsStayLive() { check("iterator"); }
    @Test public void sublistMutationsStayLive() { check("sublist"); }
    @Test public void replacementOperatorStaysLive() { check("replace"); }
    @Test public void sortingPreservesGatheredOrder() { check("sort"); }
    @Test public void returnedMutableCopiesStayIndependent() { check("result"); }
    @Test public void abilityParametersRemainLive() { check("parameter"); }
    @Test public void retainedMembershipViewsStayLive() { check("membership"); }
    @Test public void duplicatesAndNullsKeepNativeBehavior() { check("duplicates"); }
    @Test public void opaqueGetterKeepsNativeMutationOrdering() {
        Game game = board(2); Card host = creature(actor(game)); StaticAbility effect = devotion(host, 2);
        KeywordCollection keywords = new KeywordCollection(); keywords.insert(new MutatingGetter(host, effect));
        host.getCurrentState().setCachedKeywords(keywords);
        assertFalse(host.getStaticAbilities().contains(effect), "Copy intrinsic traits before the opaque getter runs");
        assertTrue(host.getStaticAbilities().contains(effect)); observe(game, 2);
    }

    private void copiedHosts(int depth) {
        Game original = board(0); Card source = addCardToZone("Furnace Whelp", actor(original), ZoneType.Battlefield);
        original.getAction().checkStaticAbilities(false); original.getTriggerHandler().resetActiveTriggers();
        StaticAbility originalFlight = source.getStaticAbilities().stream()
                .filter(value -> value.checkMode(StaticAbilityMode.CantBlockBy)).findFirst().orElseThrow();
        assertSame(originalFlight.getHostCard().getGame(), original);
        Game branch = copy(original, depth);
        Card copied = actor(branch).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == source.getId()).findFirst().orElseThrow();
        StaticAbility branchFlight = copied.getStaticAbilities().stream()
                .filter(value -> value.checkMode(StaticAbilityMode.CantBlockBy)).findFirst().orElseThrow();
        assertSame(branchFlight.getHostCard().getGame(), branch); assertNotSame(branchFlight, originalFlight);
        assertEquals(branchFlight.getHostCard().getId(), source.getId());
        assertSame(originalFlight.getHostCard().getGame(), original);
    }
    @Test public void warmedKeywordHostsRemapAfterOneCopy() { copiedHosts(1); }
    @Test public void warmedKeywordHostsRemapAfterTwoCopies() { copiedHosts(2); }
}
