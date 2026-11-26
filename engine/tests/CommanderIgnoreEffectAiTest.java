package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.SimulationController;
import forge.ai.simulation.SpellAbilityPicker;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** Full-simulation candidate and paid-preview controls on supplied boards. */
public class CommanderIgnoreEffectAiTest extends AITest {

    private static final class Scene {
        Game game;
        Player actor;
        Player other;
        Card source;
        Card creature;
        List<Card> mana = new ArrayList<>();
        List<Card> grave = new ArrayList<>();
        SpellAbility ignore;
    }

    @DataProvider(name = "ownership")
    public Object[][] ownership() {
        return new Object[][]{{false}, {true}};
    }

    @DataProvider(name = "auras")
    public Object[][] auras() {
        return new Object[][]{{"Volrath's Curse"}, {"Lost in Thought"}};
    }

    private void settle(Game game) {
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
    }

    private Scene scene(String name, int manaSources, boolean opposingOwner) {
        Scene scene = new Scene();
        scene.game = CommanderPayloadGraphTest.newBoard();
        scene.actor = scene.game.getPlayers().getFirst();
        scene.other = scene.game.getPlayers().get(1);
        for (Player player : scene.game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        if (!name.equals("Leonin Arbiter")) {
            scene.creature = addCardToZone("Llanowar Elves", scene.actor, ZoneType.Battlefield);
            scene.creature.setSickness(false);
            addCardToZone("Ornithopter", scene.actor, ZoneType.Battlefield);
        }
        for (int index = 0; index < manaSources; index++) {
            scene.mana.add(addCardToZone("Wastes", scene.actor, ZoneType.Battlefield));
        }
        scene.source = addCardToZone(name, opposingOwner ? scene.other : scene.actor, ZoneType.Battlefield);
        if (name.equals("Volrath's Curse") || name.equals("Lost in Thought")) {
            scene.source.attachToEntity(scene.creature, null);
        }
        for (int index = 0; index < 3; index++) {
            scene.grave.add(addCardToZone("Forest", scene.actor, ZoneType.Graveyard));
        }
        settle(scene.game);
        scene.ignore = scene.source.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.InternalIgnoreEffect)
                .findFirst().orElseThrow();
        scene.ignore.setActivatingPlayer(scene.actor);
        assertTrue(scene.ignore.canPlay());
        return scene;
    }

    private List<SpellAbility> candidates(Scene scene) {
        return new SpellAbilityPicker(scene.actor).getCandidateSpellsAndAbilities();
    }

    private SpellAbility offered(Scene scene) {
        return candidates(scene).stream().filter(ability -> ability.getApi() == ApiType.InternalIgnoreEffect
                && ability.getHostCard().getId() == scene.source.getId()).findFirst().orElse(null);
    }

    private void originalUnchanged(Scene scene) {
        assertEquals(scene.actor.getSacrificedThisTurn().size(), 0);
        for (Card card : scene.mana) {
            assertFalse(card.isTapped());
        }
        for (Card card : scene.grave) {
            assertTrue(card.isInZone(ZoneType.Graveyard));
        }
        assertTrue(scene.source.isInPlay());
    }

    @Test
    public void affordableOwnedArbiterActionIsCandidate() {
        Scene scene = scene("Leonin Arbiter", 2, false);
        assertTrue(scene.actor.hasKeyword("CantSearchLibrary"));
        assertNotNull(offered(scene));
        originalUnchanged(scene);
    }

    @Test
    public void affordableOpposingArbiterActionIsCandidate() {
        Scene scene = scene("Leonin Arbiter", 2, true);
        assertNotNull(offered(scene));
        originalUnchanged(scene);
    }

    @Test
    public void unaffordableArbiterActionIsExcluded() {
        Scene scene = scene("Leonin Arbiter", 1, true);
        assertNull(offered(scene));
        assertTrue(scene.actor.hasKeyword("CantSearchLibrary"));
        originalUnchanged(scene);
    }

    @Test(dataProvider = "auras")
    public void affectedControllerSeesOpposingAuraAction(String name) {
        Scene scene = scene(name, 2, true);
        assertNotNull(offered(scene));
        originalUnchanged(scene);
    }

    @Test
    public void affectedControllerSeesDampingEngineAction() {
        Scene scene = scene("Damping Engine", 2, true);
        assertNotNull(offered(scene));
        originalUnchanged(scene);
    }

    @Test(dataProvider = "ownership")
    public void paidArbiterPreviewUsesCopiedManaAndPlayer(boolean opposingOwner) {
        Scene scene = scene("Leonin Arbiter", 2, opposingOwner);
        SpellAbility action = offered(scene);
        assertNotNull(action);
        GameSimulator preview = new GameSimulator(new SimulationController(new Score(0), 0), scene.game, scene.actor, null);
        assertTrue(preview.applySpellAbility(action));
        Game projected = preview.getSimulatedGameState();
        settle(projected);
        Player actor = projected.getPlayer(scene.actor.getId());
        assertFalse(actor.hasKeyword("CantSearchLibrary"));
        assertTrue(projected.getPlayer(scene.other.getId()).hasKeyword("CantSearchLibrary"));
        for (Card card : scene.mana) {
            assertTrue(preview.getGameCopier().find(card).isTapped());
        }
        assertEquals(actor.getManaPool().totalMana(), 0);
        assertTrue(scene.actor.hasKeyword("CantSearchLibrary"));
        originalUnchanged(scene);
    }

    @Test
    public void paidExilePreviewUsesOnlyCopiedGraveyard() {
        Scene scene = scene("Lost in Thought", 2, true);
        SpellAbility action = offered(scene);
        assertNotNull(action);
        GameSimulator preview = new GameSimulator(new SimulationController(new Score(0), 0), scene.game, scene.actor, null);
        assertTrue(preview.applySpellAbility(action));
        for (Card card : scene.grave) {
            assertTrue(preview.getGameCopier().find(card).isInZone(ZoneType.Exile));
        }
        originalUnchanged(scene);
    }

    @Test
    public void paidSacrificePreviewUsesOnlyCopiedPermanents() {
        Scene scene = scene("Volrath's Curse", 2, true);
        int before = scene.actor.getCardsIn(ZoneType.Battlefield).size();
        SpellAbility action = offered(scene);
        assertNotNull(action);
        GameSimulator preview = new GameSimulator(new SimulationController(new Score(0), 0), scene.game, scene.actor, null);
        assertTrue(preview.applySpellAbility(action));
        Player actor = preview.getSimulatedGameState().getPlayer(scene.actor.getId());
        assertEquals(actor.getCardsIn(ZoneType.Battlefield).size(), before - 1);
        assertEquals(actor.getSacrificedThisTurn().size(), 1);
        assertEquals(scene.actor.getCardsIn(ZoneType.Battlefield).size(), before);
        originalUnchanged(scene);
    }
}
