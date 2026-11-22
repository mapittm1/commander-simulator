package forge.ai.simulation;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/** Native API budget witness, not a claim about Spectral Shift's printed mode count. */
public class CommanderModalBudgetTest extends AITest {
    @Test(timeOut = 5000)
    public void forcedThirtyTwoModesPrepareOneCombinationWithoutMutatingTheSource() {
        Game game = initAndCreateGame();
        Player actor = game.getPlayers().get(1);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card card = addCardToZone("Spectral Shift", actor, ZoneType.Hand);
        SpellAbility action = card.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.Charm).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        List<OptionalCostValue> entwine = GameActionUtil.getOptionalCostValues(action).stream()
                .filter(cost -> cost.getType() == OptionalCost.Entwine).toList();
        assertEquals(entwine.size(), 1);
        action = GameActionUtil.addOptionalCosts(action, entwine);
        assertTrue(action.isEntwine());
        AbilitySub template = action.getAdditionalAbilityList("Choices").get(0);
        List<AbilitySub> supplied = new ArrayList<>();
        for (int i = 0; i < 32; i++) supplied.add((AbilitySub) template.copy(actor));
        action.setAdditionalAbilityList("Choices", supplied);
        assertEquals(forge.game.ability.effects.CharmEffect.makePossibleOptions(action).size(), 32,
                "Every supplied mode must have a legal target before testing its preparation budget");
        assertNull(action.getSubAbility());
        List<SpellAbility> variants = ModalTextChoices.variants(action);
        assertEquals(variants.size(), 1, "A forced all-mode form has one combination");
        assertNotSame(variants.get(0), action);
        assertEquals(variants.get(0).getChosenList().size(), 32);
        assertNull(action.getSubAbility(), "Preparing modes must leave the source unannounced");
    }
}
