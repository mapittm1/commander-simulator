package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Full typed ward costs: independent native casts and nonspending affordability forecasts. */
public class CommanderTypedWardTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("typed-0")).findFirst().orElseThrow();
    }

    private boolean paid(String mode) {
        return !List.of("waterShort", "lifeShortMana", "lifePolicyShort", "multiLifePolicy", "altNone").contains(mode);
    }

    private int initialLife(String mode) {
        return mode.equals("lifePolicyShort") ? 5 : mode.equals("multiLifePolicy") ? 7
                : mode.equals("multiLifePaid") ? 8 : 100;
    }

    private int artifacts(String mode) {
        return mode.equals("waterPaid") ? 5 : mode.startsWith("water") ? 4 : mode.startsWith("multi") ? 2 : 1;
    }

    private int forests(String mode) {
        return mode.equals("waterMixed") || mode.equals("lifeShortMana") ? 1
                : mode.startsWith("water") || mode.equals("altDiscard") || mode.equals("altNone") ? 0
                : mode.startsWith("multi") ? 4 : 2;
    }

    private Game setup(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("typed-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Full typed ward costs"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        actor(game).setLife(initialLife(mode), null);
        addCardToZone("Crashing Wave", actor(game), ZoneType.Hand);
        if (mode.equals("altDiscard")) addCardToZone("Forest", actor(game), ZoneType.Hand);
        for (int index = 0; index < 2; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        for (int index = 0; index < forests(mode); index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        for (int index = 0; index < artifacts(mode); index++) addCardToZone("Welding Jar", actor(game), ZoneType.Battlefield);
        Player opponent = game.getPlayers().get(1);
        addCardToZone(mode.startsWith("water") ? "The Unagi of Kyoshi Island"
                : mode.startsWith("alt") ? "Titania, Rugged Rumbler" : "Gisa, the Hellraiser", opponent, ZoneType.Battlefield);
        if (mode.startsWith("multi")) addCardToZone("Captain Howler, Sea Scourge", opponent, ZoneType.Battlefield);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) result.put(card.getId(), card.getZone().getZoneType() + ":" + card.isTapped() + ":" + card.isUsedToPay());
        for (Player player : game.getPlayers()) result.put(-player.getId(), "life=" + player.getLife() + ":pool=" + player.getManaPool().totalMana());
        return result;
    }

    private void check(String mode, int depth, boolean ordered) {
        Game original = setup(mode);
        Map<Integer, String> untouched = state(original);
        Game game = snapshot(original, depth);
        Player payer = actor(game);
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).stream().filter(c -> c.getName().equals("Crashing Wave"))
                .findFirst().orElseThrow().getFirstSpellAbility();
        spell.setActivatingPlayer(payer);
        int chosen = mode.startsWith("multi") ? 2 : 1;
        spell.setXManaCostPaid(chosen);
        List<Card> targets = game.getPlayers().get(1).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).toList();
        assertEquals(targets.size(), chosen);
        for (Card card : targets) spell.getTargets().add(card);
        assertTrue(spell.isTargetNumberValid());
        Map<Integer, String> before = state(game);
        if (!ordered) {
            assertEquals(ComputerUtilCost.canPayCost(spell, payer, false), paid(mode),
                    "Forecast every full ward cost using independent resources and the existing four-life policy");
            assertEquals(state(game), before);
            assertEquals(spell.getXManaCostPaid(), Integer.valueOf(chosen));
        } else {
            // Casting without the AI preflight is legal even if a later ward payment fails.
            Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            assertEquals(spell.getPayingMana().size(), 2);
            assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isArtifact() && c.isTapped()).count(), (long) chosen);
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().add(spell);
            GameSimulator.resolveStack(game, payer);
            assertEquals(targets.stream().allMatch(Card::isTapped), paid(mode), "Native paid/countered result must agree with the full typed cost");
            assertEquals(targets.stream().mapToInt(c -> c.getCounters(CounterEnumType.STUN)).sum(), paid(mode) ? 3 : 0);
            int expectedLife = initialLife(mode) - (mode.equals("lifePaid") ? 2
                    : mode.equals("multiLifePaid") ? 4 : mode.equals("multiLifePolicy") ? 2 : 0);
            assertEquals(payer.getLife(), expectedLife);
            long expectedForestTaps = mode.equals("waterMixed") && paid(mode) ? 1
                    : mode.equals("lifePaid") || mode.equals("multiLifePolicy") || mode.equals("altMana") ? 2
                    : mode.equals("multiLifePaid") ? 4 : 0;
            assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals("Forest") && c.isTapped()).count(), expectedForestTaps);
            if (mode.startsWith("water")) assertEquals(payer.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(c -> c.isArtifact() && c.isTapped()).count(), paid(mode) ? (long) artifacts(mode) : (long) chosen);
            if (mode.equals("altDiscard")) assertEquals(payer.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getName().equals("Forest")).count(), 1L);
            assertEquals(payer.getManaPool().totalMana(), 0);
        }
        if (depth > 0) assertEquals(state(original), untouched);
    }

    @Test
    public void waterPaidPreviewDepth0() {
        check("waterPaid", 0, false);
    }

    @Test
    public void waterPaidPreviewDepth1() {
        check("waterPaid", 1, false);
    }

    @Test
    public void waterPaidPreviewDepth2() {
        check("waterPaid", 2, false);
    }

    @Test
    public void waterPaidNativeDepth0() {
        check("waterPaid", 0, true);
    }

    @Test
    public void waterPaidNativeDepth1() {
        check("waterPaid", 1, true);
    }

    @Test
    public void waterPaidNativeDepth2() {
        check("waterPaid", 2, true);
    }

    @Test
    public void waterShortPreviewDepth0() {
        check("waterShort", 0, false);
    }

    @Test
    public void waterShortPreviewDepth1() {
        check("waterShort", 1, false);
    }

    @Test
    public void waterShortPreviewDepth2() {
        check("waterShort", 2, false);
    }

    @Test
    public void waterShortNativeDepth0() {
        check("waterShort", 0, true);
    }

    @Test
    public void waterShortNativeDepth1() {
        check("waterShort", 1, true);
    }

    @Test
    public void waterShortNativeDepth2() {
        check("waterShort", 2, true);
    }

    @Test
    public void waterMixedPreviewDepth0() {
        check("waterMixed", 0, false);
    }

    @Test
    public void waterMixedPreviewDepth1() {
        check("waterMixed", 1, false);
    }

    @Test
    public void waterMixedPreviewDepth2() {
        check("waterMixed", 2, false);
    }

    @Test
    public void waterMixedNativeDepth0() {
        check("waterMixed", 0, true);
    }

    @Test
    public void waterMixedNativeDepth1() {
        check("waterMixed", 1, true);
    }

    @Test
    public void waterMixedNativeDepth2() {
        check("waterMixed", 2, true);
    }

    @Test
    public void lifePaidPreviewDepth0() {
        check("lifePaid", 0, false);
    }

    @Test
    public void lifePaidPreviewDepth1() {
        check("lifePaid", 1, false);
    }

    @Test
    public void lifePaidPreviewDepth2() {
        check("lifePaid", 2, false);
    }

    @Test
    public void lifePaidNativeDepth0() {
        check("lifePaid", 0, true);
    }

    @Test
    public void lifePaidNativeDepth1() {
        check("lifePaid", 1, true);
    }

    @Test
    public void lifePaidNativeDepth2() {
        check("lifePaid", 2, true);
    }

    @Test
    public void lifeShortManaPreviewDepth0() {
        check("lifeShortMana", 0, false);
    }

    @Test
    public void lifeShortManaPreviewDepth1() {
        check("lifeShortMana", 1, false);
    }

    @Test
    public void lifeShortManaPreviewDepth2() {
        check("lifeShortMana", 2, false);
    }

    @Test
    public void lifeShortManaNativeDepth0() {
        check("lifeShortMana", 0, true);
    }

    @Test
    public void lifeShortManaNativeDepth1() {
        check("lifeShortMana", 1, true);
    }

    @Test
    public void lifeShortManaNativeDepth2() {
        check("lifeShortMana", 2, true);
    }

    @Test
    public void lifePolicyShortPreviewDepth0() {
        check("lifePolicyShort", 0, false);
    }

    @Test
    public void lifePolicyShortPreviewDepth1() {
        check("lifePolicyShort", 1, false);
    }

    @Test
    public void lifePolicyShortPreviewDepth2() {
        check("lifePolicyShort", 2, false);
    }

    @Test
    public void lifePolicyShortNativeDepth0() {
        check("lifePolicyShort", 0, true);
    }

    @Test
    public void lifePolicyShortNativeDepth1() {
        check("lifePolicyShort", 1, true);
    }

    @Test
    public void lifePolicyShortNativeDepth2() {
        check("lifePolicyShort", 2, true);
    }

    @Test
    public void multiLifePaidPreviewDepth0() {
        check("multiLifePaid", 0, false);
    }

    @Test
    public void multiLifePaidPreviewDepth1() {
        check("multiLifePaid", 1, false);
    }

    @Test
    public void multiLifePaidPreviewDepth2() {
        check("multiLifePaid", 2, false);
    }

    @Test
    public void multiLifePaidNativeDepth0() {
        check("multiLifePaid", 0, true);
    }

    @Test
    public void multiLifePaidNativeDepth1() {
        check("multiLifePaid", 1, true);
    }

    @Test
    public void multiLifePaidNativeDepth2() {
        check("multiLifePaid", 2, true);
    }

    @Test
    public void multiLifePolicyPreviewDepth0() {
        check("multiLifePolicy", 0, false);
    }

    @Test
    public void multiLifePolicyPreviewDepth1() {
        check("multiLifePolicy", 1, false);
    }

    @Test
    public void multiLifePolicyPreviewDepth2() {
        check("multiLifePolicy", 2, false);
    }

    @Test
    public void multiLifePolicyNativeDepth0() {
        check("multiLifePolicy", 0, true);
    }

    @Test
    public void multiLifePolicyNativeDepth1() {
        check("multiLifePolicy", 1, true);
    }

    @Test
    public void multiLifePolicyNativeDepth2() {
        check("multiLifePolicy", 2, true);
    }

    @Test
    public void altManaPreviewDepth0() {
        check("altMana", 0, false);
    }

    @Test
    public void altManaPreviewDepth1() {
        check("altMana", 1, false);
    }

    @Test
    public void altManaPreviewDepth2() {
        check("altMana", 2, false);
    }

    @Test
    public void altManaNativeDepth0() {
        check("altMana", 0, true);
    }

    @Test
    public void altManaNativeDepth1() {
        check("altMana", 1, true);
    }

    @Test
    public void altManaNativeDepth2() {
        check("altMana", 2, true);
    }

    @Test
    public void altDiscardPreviewDepth0() {
        check("altDiscard", 0, false);
    }

    @Test
    public void altDiscardPreviewDepth1() {
        check("altDiscard", 1, false);
    }

    @Test
    public void altDiscardPreviewDepth2() {
        check("altDiscard", 2, false);
    }

    @Test
    public void altDiscardNativeDepth0() {
        check("altDiscard", 0, true);
    }

    @Test
    public void altDiscardNativeDepth1() {
        check("altDiscard", 1, true);
    }

    @Test
    public void altDiscardNativeDepth2() {
        check("altDiscard", 2, true);
    }

    @Test
    public void altNonePreviewDepth0() {
        check("altNone", 0, false);
    }

    @Test
    public void altNonePreviewDepth1() {
        check("altNone", 1, false);
    }

    @Test
    public void altNonePreviewDepth2() {
        check("altNone", 2, false);
    }

    @Test
    public void altNoneNativeDepth0() {
        check("altNone", 0, true);
    }

    @Test
    public void altNoneNativeDepth1() {
        check("altNone", 1, true);
    }

    @Test
    public void altNoneNativeDepth2() {
        check("altNone", 2, true);
    }
}
