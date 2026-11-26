package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.CardType;
import forge.card.CardTypeView;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.card.CardTraitChanges;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.replacement.ReplacementResult;
import forge.game.replacement.ReplacementType;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollection;
import forge.util.collect.FCollectionView;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertTrue;

/** Supplied-board phase replacement controls, not complete legal turn histories. */
public class CommanderPhaseReplacementTest extends AITest {
    private final List<GameCopier> copies = new ArrayList<>();
    private static final class Scene {
        Game game;
        Player actor;
        Player other;
        Card plain;
    }
    private Scene scene() {
        Scene scene = new Scene();
        scene.game = CommanderPayloadGraphTest.newBoard();
        scene.actor = scene.game.getPlayers().getFirst();
        scene.other = scene.game.getPlayers().get(1);
        scene.plain = addCardToZone("Grizzly Bears", scene.actor, ZoneType.Battlefield);
        scene.game.getAction().checkStaticAbilities(false);
        scene.game.getTriggerHandler().resetActiveTriggers();
        scene.game.copyLastState();
        return scene;
    }
    private ReplacementResult phase(Game game, Player player, PhaseType phase) {
        Map<AbilityKey, Object> parameters = AbilityKey.mapFromAffected(player);
        parameters.put(AbilityKey.Phase, phase);
        return game.getReplacementHandler().run(ReplacementType.BeginPhase, parameters);
    }
    private ReplacementEffect skip(Card host) {
        return ReplacementHandler.parseReplacement(
                "Event$ BeginPhase | ActiveZones$ Battlefield | Phase$ Upkeep | Skip$ True",
                host, true);
    }
    private CardTraitChanges traits(ReplacementEffect effect) {
        return new CardTraitChanges(List.of(), List.of(), List.of(effect), List.of(), null);
    }
    @AfterMethod
    public void releaseCopies() {
        for (GameCopier copier : copies) {
            copier.release();
        }
        copies.clear();
    }
    @Test
    public void ordinaryUpkeepIsNotSkipped() {
        Scene scene = scene();
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
    }
    @Test
    public void eonHubSkipsBothPlayersUpkeeps() {
        Scene scene = scene();
        Card hub = addCardToZone("Eon Hub", scene.actor, ZoneType.Battlefield);
        assertTrue(hub.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
        assertEquals(phase(scene.game, scene.other, PhaseType.UPKEEP), ReplacementResult.Skipped);
    }
    @Test
    public void eonHubDoesNotSkipDraw() {
        Scene scene = scene();
        addCardToZone("Eon Hub", scene.actor, ZoneType.Battlefield);
        assertEquals(phase(scene.game, scene.actor, PhaseType.DRAW), ReplacementResult.NotReplaced);
    }
    @Test
    public void eonHubInHandDoesNotSkipUpkeep() {
        Scene scene = scene();
        addCardToZone("Eon Hub", scene.actor, ZoneType.Hand);
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
    }
    @Test
    public void solitaryConfinementSkipsControllersDraw() {
        Scene scene = scene();
        addCardToZone("Solitary Confinement", scene.actor, ZoneType.Battlefield);
        assertEquals(phase(scene.game, scene.actor, PhaseType.DRAW), ReplacementResult.Skipped);
    }
    @Test
    public void solitaryConfinementDoesNotSkipOpponentsDraw() {
        Scene scene = scene();
        addCardToZone("Solitary Confinement", scene.actor, ZoneType.Battlefield);
        assertEquals(phase(scene.game, scene.other, PhaseType.DRAW), ReplacementResult.NotReplaced);
    }
    @Test
    public void replacementAddedAfterNegativeProbeIsVisible() {
        Scene scene = scene();
        scene.plain.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase);
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
        scene.plain.addChangedCardTraits(traits(skip(scene.plain)), scene.game.getNextTimestamp(), 0L, false);
        assertTrue(scene.plain.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
    }
    @Test
    public void replacementRemovedAfterPositiveProbeIsNotReused() {
        Scene scene = scene();
        long timestamp = scene.game.getNextTimestamp();
        scene.plain.addChangedCardTraits(traits(skip(scene.plain)), timestamp, 0L, false);
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
        assertTrue(scene.plain.removeChangedCardTraits(timestamp, 0L));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
    }
    @Test
    public void textChangedReplacementIsNotSkipped() {
        Scene scene = scene();
        scene.plain.getChangedCardTraitsByText().put(scene.game.getNextTimestamp(), 0L, traits(skip(scene.plain)));
        assertTrue(scene.plain.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
    }
    private void copiedHub(int depth) {
        Scene scene = scene();
        Card hub = addCardToZone("Eon Hub", scene.actor, ZoneType.Battlefield);
        scene.game.copyLastState();
        Game game = scene.game;
        Player actor = scene.actor;
        Card mapped = hub;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            copies.add(copier);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            mapped = copier.find(mapped);
        }
        assertNotSame(mapped, hub);
        assertEquals(mapped.getId(), hub.getId());
        assertTrue(mapped.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase));
        assertEquals(phase(game, actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
        assertTrue(hub.isInZone(ZoneType.Battlefield));
    }
    @Test
    public void copiedEonHubRetainsReplacement() {
        copiedHub(1);
    }
    @Test
    public void repeatedCopyRetainsPhaseReplacement() {
        copiedHub(2);
    }
    private static final class CallbackCard extends Card {
        ReplacementEffect extra;
        int reads;
        CallbackCard(Game game, Player owner) {
            super(game.nextCardId(), null, game);
            setOwner(owner);
            setName("Phase callback contract");
            setType(CardType.parse("Artifact", false));
        }
        @Override
        public FCollectionView<ReplacementEffect> getReplacementEffects() {
            reads++;
            FCollection<ReplacementEffect> result = new FCollection<>(super.getReplacementEffects());
            if (extra != null) {
                result.add(extra);
            }
            return result;
        }
    }
    @Test
    public void cardSubclassReplacementCallbackStillRuns() {
        Scene scene = scene();
        CallbackCard host = new CallbackCard(scene.game, scene.actor);
        scene.actor.getZone(ZoneType.Battlefield).add(host);
        host.extra = skip(host);
        int before = host.reads;
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.Skipped);
        assertTrue(host.reads > before);
    }
    @Test
    public void unknownTypeImplementationRetainsGathering() throws Exception {
        Scene scene = scene();
        CardType type = CardType.parse("Creature", false);
        CardTypeView view = (CardTypeView) Proxy.newProxyInstance(
                CardTypeView.class.getClassLoader(),
                new Class<?>[]{CardTypeView.class},
                (proxy, method, arguments) -> method.invoke(type, arguments));
        Field field = CardState.class.getDeclaredField("changedType");
        field.setAccessible(true);
        field.set(scene.plain.getCurrentState(), view);
        assertTrue(scene.plain.getCurrentState().mayHaveReplacementEffects(ReplacementType.BeginPhase));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
    }
    @Test
    public void nonPhaseCounterReplacementsRemainDiscoverable() {
        Scene scene = scene();
        scene.plain.setCounters(CounterEnumType.SHIELD, 1);
        scene.plain.setCounters(CounterEnumType.STUN, 1);
        scene.plain.setCounters(CounterEnumType.FINALITY, 1);
        EnumSet<ReplacementType> modes = EnumSet.noneOf(ReplacementType.class);
        for (ReplacementEffect effect : scene.plain.getReplacementEffects()) {
            modes.add(effect.getMode());
            assertTrue(scene.plain.getCurrentState().mayHaveReplacementEffects(effect.getMode()));
        }
        assertTrue(modes.containsAll(EnumSet.of(ReplacementType.DamageDone,
                ReplacementType.Destroy, ReplacementType.Untap, ReplacementType.Moved)));
        assertTrue(scene.plain.getCurrentState().mayHaveReplacementEffects(null));
        assertEquals(phase(scene.game, scene.actor, PhaseType.UPKEEP), ReplacementResult.NotReplaced);
    }
}
