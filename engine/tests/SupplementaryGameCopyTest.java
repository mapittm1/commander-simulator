package forge.ai;

import java.util.List;
import java.util.Set;

import forge.StaticData;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

/** Preserve real supplementary zones in independent AI game snapshots. */
public class SupplementaryGameCopyTest extends AITest {
    private Card place(Game game, String name, ZoneType zone) {
        Player owner = game.getPhaseHandler().getPlayerTurn();
        PaperCard paper = StaticData.instance().getCommonCards().getCard(name);
        if (paper == null) {
            paper = StaticData.instance().getVariantCards().getCard(name);
        }
        Card card = Card.fromPaperCard(paper, owner);
        owner.getZone(zone).add(card);
        return card;
    }

    private Card copied(Game game, Card original, ZoneType zone) {
        Game snapshot = new GameCopier(game).makeCopy();
        Player owner = snapshot.getPlayers().stream()
                .filter(player -> player.getId() == original.getOwner().getId()).findFirst().orElseThrow();
        assertEquals(owner.getCardsIn(zone).size(), original.getOwner().getCardsIn(zone).size());
        Card copy = owner.getCardsIn(zone).stream()
                .filter(card -> card.getId() == original.getId()).findFirst().orElseThrow();
        assertNotSame(copy, original);
        assertNotSame(copy.getOwner(), original.getOwner());
        assertEquals(copy.getName(), original.getName());
        assertEquals(copy.getPaperCard(), original.getPaperCard());
        assertEquals(copy.getZone().getZoneType(), zone);
        return copy;
    }

    @Test
    public void attractionDeckPreservesFunctionalLightsAndOrder() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        PaperCard paper = StaticData.instance().getVariantCards().getCard("Bumper Cars", "UNF", "202c");
        Card attraction = Card.fromPaperCard(paper, owner);
        owner.getZone(ZoneType.AttractionDeck).add(attraction);
        Card other = place(game, "Information Booth", ZoneType.AttractionDeck);
        Card copy = copied(game, attraction, ZoneType.AttractionDeck);
        assertEquals(copy.getAttractionLights(), Set.of(2, 5, 6));
        assertEquals(copy.getOwner().getCardsIn(ZoneType.AttractionDeck).stream().map(Card::getId).toList(),
                List.of(attraction.getId(), other.getId()));
        copy.setAttractionLights(Set.of(3, 6));
        assertEquals(attraction.getAttractionLights(), Set.of(2, 5, 6));
    }

    @Test
    public void junkyardPreservesDiscardedAttraction() {
        Game game = initAndCreateGame();
        Card original = place(game, "Bumper Cars", ZoneType.Junkyard);
        assertEquals(copied(game, original, ZoneType.Junkyard).getAttractionLights(), original.getAttractionLights());
    }

    @Test
    public void stickerSheetsPreserveSelectedSheet() {
        Game game = initAndCreateGame();
        copied(game, place(game, "Squishy Sphinx Ninja", ZoneType.StickerSheets), ZoneType.StickerSheets);
    }

    @Test
    public void sideboardPreservesOutsideGameCard() {
        Game game = initAndCreateGame();
        copied(game, place(game, "Lurrus of the Dream-Den", ZoneType.Sideboard), ZoneType.Sideboard);
    }

    @Test
    public void planarDeckPreservesPlane() {
        Game game = initAndCreateGame();
        copied(game, place(game, "The Aether Flues", ZoneType.PlanarDeck), ZoneType.PlanarDeck);
    }

    @Test
    public void schemeDeckPreservesScheme() {
        Game game = initAndCreateGame();
        copied(game, place(game, "All in Good Time", ZoneType.SchemeDeck), ZoneType.SchemeDeck);
    }

    @Test
    public void contraptionDeckPreservesContraption() {
        Game game = initAndCreateGame();
        copied(game, place(game, "Accessories to Murder", ZoneType.ContraptionDeck), ZoneType.ContraptionDeck);
    }
}
