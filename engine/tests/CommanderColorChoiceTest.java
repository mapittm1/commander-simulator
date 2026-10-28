package forge.ai;

import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/** Local regressions for immutable Commander color choice (CR 903.4b). */
public class CommanderColorChoiceTest extends AITest {
    private static final class Choices extends PlayerControllerAi {
        private final byte fallback;
        int calls;

        Choices(Game game, Player player, byte fallback) {
            super(game, player, player.getLobbyPlayer());
            this.fallback = fallback;
        }

        @Override
        public byte chooseColor(String message, SpellAbility ability, ColorSet options) {
            calls++;
            return fallback;
        }
    }

    private Card commander(Game game, String name, String colors, Choices choices) {
        Player owner = game.getPhaseHandler().getPlayerTurn();
        owner.dangerouslySetController(choices);
        PaperCard paper = StaticData.instance().getCommonCards().getCard(name);
        if (colors != null) {
            paper = paper.copyWithMarkedColors(ColorSet.fromNames(colors.split("")));
        }
        Card card = Card.fromPaperCard(paper, owner);
        owner.initCommanderColor(card);
        owner.getZone(ZoneType.Command).add(card);
        owner.addCommander(card);
        game.getAction().checkStaticAbilities(false);
        return card;
    }

    private void selectedColors(String name) {
        for (String color : List.of("W", "U", "B", "R", "G")) {
            Game game = initAndCreateGame();
            Player owner = game.getPhaseHandler().getPlayerTurn();
            Choices choices = new Choices(game, owner, MagicColor.WHITE);
            Card card = commander(game, name, color, choices);
            assertEquals(choices.calls, 0);
            assertEquals(card.getChosenCommanderColor(), MagicColor.fromName(color));
            assertEquals(card.getColor().getColor(), MagicColor.fromName(color));
            assertEquals(owner.getCommanderColorID().getColor(), MagicColor.fromName(color));
        }
    }

    @Test
    public void prismaticPiperUsesEachSelectedColor() {
        selectedColors("The Prismatic Piper");
    }

    @Test
    public void facelessOneUsesEachSelectedColor() {
        selectedColors("Faceless One");
    }

    @Test
    public void unmarkedCommanderStillUsesNativeColorChoice() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Choices choices = new Choices(game, owner, MagicColor.GREEN);
        Card card = commander(game, "The Prismatic Piper", null, choices);
        assertEquals(choices.calls, 1);
        assertEquals(card.getChosenCommanderColor(), MagicColor.GREEN);
        assertEquals(owner.getCommanderColorID().getColor(), MagicColor.GREEN);
    }

    @Test
    public void multipleMarkedColorsCannotGrantMultipleColors() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Choices choices = new Choices(game, owner, MagicColor.BLUE);
        Card card = commander(game, "The Prismatic Piper", "WR", choices);
        assertEquals(choices.calls, 1);
        assertEquals(card.getChosenCommanderColor(), MagicColor.BLUE);
        assertEquals(owner.getCommanderColorID().getColor(), MagicColor.BLUE);
    }

    @Test
    public void ordinaryCommanderCannotAcquireAColorThroughMetadata() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Choices choices = new Choices(game, owner, MagicColor.BLUE);
        Card card = commander(game, "Ghalta, Primal Hunger", "R", choices);
        assertEquals(choices.calls, 0);
        assertEquals(card.getChosenCommanderColor(), (byte) 0);
        assertFalse(card.hasChosenColor());
        assertEquals(owner.getCommanderColorID().getColor(), MagicColor.GREEN);
    }

    @Test
    public void partnerAndCommandTowerIncludeTheSelectedColor() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Choices choices = new Choices(game, owner, MagicColor.WHITE);
        commander(game, "The Prismatic Piper", "G", choices);
        commander(game, "Rograkh, Son of Rohgahh", null, choices);
        assertEquals(owner.getCommanderColorID().getColor(), (byte) (MagicColor.RED | MagicColor.GREEN));
        Card tower = Card.fromPaperCard(StaticData.instance().getCommonCards().getCard("Command Tower"), owner);
        owner.getZone(ZoneType.Battlefield).add(tower);
        assertEquals(tower.getManaAbilities().get(0).getManaPart().getComboColors(tower.getManaAbilities().get(0)), "R G");
    }

    @Test
    public void logicalAndLastKnownCopiesRetainThePregameChoice() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card card = commander(game, "The Prismatic Piper", "G", new Choices(game, owner, MagicColor.WHITE));
        Card copy = new CardCopyService(card).copyCard(false, owner);
        assertEquals(copy.getChosenCommanderColor(), MagicColor.GREEN);
        assertEquals(new CardCopyService(card).getLKICopy().getChosenCommanderColor(), MagicColor.GREEN);
        Game snapshot = new GameCopier(game).makeCopy();
        snapshot.getAction().checkStaticAbilities(false);
        Card copied = snapshot.getPlayers().stream().flatMap(p -> p.getCommanders().stream())
                .filter(c -> c.getName().equals("The Prismatic Piper")).findFirst().orElseThrow();
        assertEquals(copied.getChosenCommanderColor(), MagicColor.GREEN);
        assertEquals(copied.getColor().getColor(), MagicColor.GREEN);
        assertEquals(copied.getOwner().getCommanderColorID().getColor(), MagicColor.GREEN);
    }

    @Test
    public void laterOrdinaryColorChoiceCannotRewritePregameColor() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card card = commander(game, "The Prismatic Piper", "G", new Choices(game, owner, MagicColor.WHITE));
        card.setChosenColors(List.of("White"));
        game.getAction().checkStaticAbilities(false);
        assertEquals(card.getChosenCommanderColor(), MagicColor.GREEN);
        assertEquals(card.getColor().getColor(), MagicColor.GREEN);
        assertEquals(owner.getCommanderColorID().getColor(), MagicColor.GREEN);
    }

    @Test
    public void copyingCharacteristicsDoesNotCopyPregameDesignation() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Card card = commander(game, "The Prismatic Piper", "G", new Choices(game, owner, MagicColor.WHITE));
        Card characteristics = CardCopyService.copyStats(card, owner, true);
        assertEquals(characteristics.getChosenCommanderColor(), (byte) 0);
    }

    @Test
    public void nativeDeckReaderRetainsExplicitColorForPregameSetup() {
        Game game = initAndCreateGame();
        Player owner = game.getPhaseHandler().getPlayerTurn();
        Choices choices = new Choices(game, owner, MagicColor.WHITE);
        owner.dangerouslySetController(choices);
        boolean originalArtSelection = StaticData.instance().isEnabledCardArtSmartSelection();
        StaticData.instance().setEnableSmartCardArtSelection(true);
        try {
            Deck deck = DeckSerializer.fromSections(Map.of(
                "metadata", List.of("Name=ColorFixture"),
                "Commander", List.of("1 Rograkh, Son of Rohgahh", "1 The Prismatic Piper|#{markedColors=G}"),
                "Main", List.of("98 Forest")
            ));
            PaperCard piper = deck.getCommanders().stream().filter(c -> c.getName().equals("The Prismatic Piper")).findFirst().orElseThrow();
            assertEquals(piper.getMarkedColors().getColor(), MagicColor.GREEN);
            RegisteredPlayer registered = new RegisteredPlayer(deck);
            registered.assignCommander();
            owner.initVariantsZones(registered);
            game.getAction().checkStaticAbilities(false);
            assertEquals(owner.getCommanders().size(), 2);
            Card selected = owner.getCommanders().stream().filter(c -> c.getName().equals("The Prismatic Piper")).findFirst().orElseThrow();
            assertEquals(selected.getChosenCommanderColor(), MagicColor.GREEN);
            assertEquals(owner.getCommanderColorID().getColor(), (byte) (MagicColor.RED | MagicColor.GREEN));
            assertEquals(choices.calls, 0);
        } finally {
            StaticData.instance().setEnableSmartCardArtSelection(originalArtSelection);
        }
    }
}
