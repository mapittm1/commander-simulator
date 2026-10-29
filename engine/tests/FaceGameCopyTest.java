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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Real alternate-face casts must keep the same pending object in AI snapshots. */
public class FaceGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("face" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Face fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0));
        for (Player player : game.getPlayers()) {
            addCardToZone("Forest", player, ZoneType.Library);
        }
        return game;
    }

    private SpellAbility alternate(Card card, CardStateName face, Player owner) {
        SpellAbility ability = card.getState(face).getSpellAbilities().stream()
                .filter(SpellAbility::isSpell).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private void pay(Game game, SpellAbility ability, int mana) {
        assertTrue(ability.canPlay(), "Alternate-face admission");
        assertTrue(ComputerUtil.playStack(ability, ability.getActivatingPlayer(), game),
                "Alternate-face payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Game snapshot(Game source) {
        return new GameCopier(source).withStack(true).makeCopy();
    }

    private void assertPendingFace(Game source, Game copy, CardStateName face) {
        Card original = source.getStack().peekAbility().getHostCard();
        Card mapped = copy.getStack().peekAbility().getHostCard();
        assertEquals(original.getCurrentStateName(), face, "Source face fixture");
        assertEquals(mapped.getCurrentStateName(), face, "Copied pending face");
        assertEquals(mapped.isBackSide(), original.isBackSide());
        assertEquals(mapped.isFaceDown(), original.isFaceDown());
        assertEquals(mapped.getName(), original.getName());
        assertEquals(mapped.getView().getCurrentState().getName(), mapped.getName());
        assertEquals(mapped.getType().toString(), original.getType().toString());
        assertEquals(mapped.getCMC(), original.getCMC());
        assertEquals(mapped.getGameTimestamp(), original.getGameTimestamp());
        assertEquals(mapped.getLayerTimestamp(), original.getLayerTimestamp());
        assertFalse(mapped.isLKI());
        assertSame(mapped.getGame(), copy);
    }

    @Test
    public void adventureFaceAndExileDestinationSurviveCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card bear = addCard("Grizzly Bears", opponent);
        addCards("Island", 2, owner);
        Card borrower = addCardToZone("Brazen Borrower", owner, ZoneType.Hand);
        SpellAbility theft = alternate(borrower, CardStateName.Secondary, owner);
        theft.getTargets().add(bear);
        pay(game, theft, 2);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.Secondary);
        copy.getStack().resolveStack();
        assertTrue(copy.getPlayer(opponent.getId()).getCardsIn(ZoneType.Hand).stream()
                .anyMatch(card -> card.getId() == bear.getId()));
        assertTrue(copy.getPlayer(owner.getId()).getCardsIn(ZoneType.Exile).stream()
                .anyMatch(Card::isOnAdventure));
        assertEquals(game.getStack().size(), 1);
        assertTrue(bear.isInPlay());
    }

    @Test
    public void modalBackFaceResolvesAsTheSelectedPermanent() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        for (String land : List.of("Plains", "Island", "Swamp", "Mountain", "Forest")) {
            addCard(land, owner);
        }
        Card esika = addCardToZone("Esika, God of the Tree", owner, ZoneType.Hand);
        SpellAbility bridge = alternate(esika, CardStateName.Backside, owner);
        pay(game, bridge, 5);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.Backside);
        copy.getStack().resolveStack();
        assertTrue(copy.getPlayer(owner.getId()).getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getName().equals("The Prismatic Bridge")
                        && card.isEnchantment() && !card.isCreature()));
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void rightSplitFaceKeepsItsSpellCharacteristics() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card target = addCard("Forest", game.getPlayers().get(1));
        addCards("Island", 2, owner);
        Card split = addCardToZone("Fire // Ice", owner, ZoneType.Hand);
        SpellAbility ice = alternate(split, CardStateName.RightSplit, owner);
        ice.getTargets().add(target);
        pay(game, ice, 2);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.RightSplit);
        copy.getStack().resolveStack();
        Card mapped = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == target.getId()).findFirst().orElseThrow();
        assertTrue(mapped.isTapped());
        assertEquals(copy.getPlayer(owner.getId()).getCardsIn(ZoneType.Hand).size(), 1);
        assertFalse(target.isTapped());
        assertEquals(game.getStack().size(), 1);
    }

    @org.testng.annotations.DataProvider(name = "faceDownForms")
    public Object[][] faceDownForms() {
        return new Object[][] {{"Willbender", false}, {"Dog Walker", true}};
    }

    @Test(dataProvider = "faceDownForms")
    public void faceDownCastingKeepsItsHiddenCharacteristics(String name, boolean ward) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Island", 3, owner);
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        SpellAbility morph = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isCastFaceDown()).findFirst().orElseThrow();
        morph.setActivatingPlayer(owner);
        pay(game, morph, 3);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.FaceDown);
        assertEquals(game.getStack().peekAbility().getHostCard()
                .hasKeyword(forge.game.keyword.Keyword.WARD), ward);
        assertEquals(copy.getStack().peekAbility().getHostCard()
                .hasKeyword(forge.game.keyword.Keyword.WARD), ward);
        copy.getStack().resolveStack();
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertTrue(resolved.isFaceDown());
        assertEquals(resolved.getNetPower(), 2);
        assertEquals(resolved.getNetToughness(), 2);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void prototypeRetainsItsPaidCostColorAndSize() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Swamp", 3, owner);
        Card card = addCardToZone("Phyrexian Fleshgorger", owner, ZoneType.Hand);
        SpellAbility prototype = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.hasParam("Prototype")).findFirst().orElseThrow();
        prototype.setActivatingPlayer(owner);
        pay(game, prototype, 3);
        Card pending = game.getStack().peekAbility().getHostCard();
        assertEquals(pending.getNetPower(), 3, "Paid prototype fixture");
        assertEquals(pending.getCMC(), 3);
        Game copy = snapshot(game);
        Card mapped = copy.getStack().peekAbility().getHostCard();
        assertEquals(mapped.getNetPower(), 3);
        assertEquals(mapped.getView().getCurrentState().getPower(), 3);
        assertEquals(mapped.getNetToughness(), 3);
        assertEquals(mapped.getCMC(), 3);
        assertEquals(mapped.getColor(), pending.getColor());
        assertEquals(mapped.getPrototypeTimestamp(), pending.getPrototypeTimestamp());
        copy.getStack().resolveStack();
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertEquals(resolved.getNetPower(), 3);
        assertEquals(resolved.getNetToughness(), 3);
        assertEquals(resolved.getCMC(), 3);
        Game again = snapshot(copy);
        Card copiedPermanent = again.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertEquals(copiedPermanent.getNetPower(), 3);
        assertEquals(copiedPermanent.getCMC(), 3);
    }

    @Test
    public void bestowRemainsAnAuraUntilResolution() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 5, owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card card = addCardToZone("Boon Satyr", owner, ZoneType.Hand);
        SpellAbility bestow = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isBestow()).findFirst().orElseThrow();
        bestow.setActivatingPlayer(owner);
        bestow.getTargets().add(bear);
        pay(game, bestow, 5);
        Card pending = game.getStack().peekAbility().getHostCard();
        assertTrue(pending.isAura());
        assertFalse(pending.isCreature());
        Game copy = snapshot(game);
        Card mapped = copy.getStack().peekAbility().getHostCard();
        assertTrue(mapped.isAura());
        assertFalse(mapped.isCreature());
        assertTrue(mapped.isBestowed());
        assertEquals(mapped.getBestowTimestamp(), pending.getBestowTimestamp());
        copy.getStack().resolveStack();
        copy.getAction().checkStateEffects(true);
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertTrue(resolved.isAura());
        assertTrue(resolved.isAttachedToEntity());
        assertEquals(((Card)resolved.getEntityAttachedTo()).getNetPower(), 6);
        assertEquals(bear.getNetPower(), 2);
    }

    @org.testng.annotations.DataProvider(name = "frontFaces")
    public Object[][] frontFaces() {
        return new Object[][] {
            {"Brazen Borrower", "Island", 3, 3},
            {"Esika, God of the Tree", "Forest", 3, 1}
        };
    }

    @Test(dataProvider = "frontFaces")
    public void ordinaryFrontFaceRemainsAvailable(String name, String land, int mana, int power) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards(land, mana, owner);
        SpellAbility spell = addCardToZone(name, owner, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(owner);
        pay(game, spell, mana);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.Original);
        copy.getStack().resolveStack();
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
        assertEquals(resolved.getNetPower(), power);
        assertEquals(game.getStack().size(), 1);
    }

    @Test
    public void leftSplitFaceDealsTheDeclaredDividedDamage() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        addCards("Mountain", 2, owner);
        Card split = addCardToZone("Fire // Ice", owner, ZoneType.Hand);
        SpellAbility fire = alternate(split, CardStateName.LeftSplit, owner);
        fire.clearTargets();
        fire.getTargets().add(target);
        fire.getTargets().addDividedAllocation(target, 2);
        pay(game, fire, 2);
        Game copy = snapshot(game);
        assertPendingFace(game, copy, CardStateName.LeftSplit);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(target.getId()).getLife(), 38);
        assertEquals(target.getLife(), 40);
    }

    @Test
    public void resolvedAdventureRetainsItsPermissionAfterRepeatedSnapshots() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Island", 5, owner);
        Card bear = addCard("Grizzly Bears", game.getPlayers().get(1));
        Card borrower = addCardToZone("Brazen Borrower", owner, ZoneType.Hand);
        SpellAbility theft = alternate(borrower, CardStateName.Secondary, owner);
        theft.getTargets().add(bear);
        pay(game, theft, 2);
        game.getStack().resolveStack();
        Card onAdventure = owner.getCardsIn(ZoneType.Exile).iterator().next();
        assertTrue(onAdventure.isOnAdventure(), "Resolved native Adventure fixture");
        Game copy = snapshot(snapshot(game));
        Player mappedOwner = copy.getPlayer(owner.getId());
        Card mapped = mappedOwner.getCardsIn(ZoneType.Exile).iterator().next();
        assertTrue(mapped.isOnAdventure(), "Copied Adventure provenance");
        assertSame(mapped.getExiledWith().getGame(), copy);
        assertTrue(mapped.getExiledWith().isLKI());
        assertEquals(mapped.getExiledWith().getCurrentStateName(), CardStateName.Secondary);
        SpellAbility creature = mapped.getAllPossibleAbilities(mappedOwner, false).stream()
                .filter(sa -> sa.isSpell() && !sa.isAdventure() && sa.canPlay())
                .findFirst().orElseThrow();
        creature.setActivatingPlayer(mappedOwner);
        pay(copy, creature, 3);
        copy.getStack().resolveStack();
        assertTrue(mappedOwner.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getName().equals("Brazen Borrower")));
        assertEquals(owner.getCardsIn(ZoneType.Exile).size(), 1);
    }

    @Test
    public void bestowWithAnInvalidatedTargetBecomesACreature() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 5, owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card satyr = addCardToZone("Boon Satyr", owner, ZoneType.Hand);
        SpellAbility bestow = satyr.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isBestow()).findFirst().orElseThrow();
        bestow.setActivatingPlayer(owner);
        bestow.getTargets().add(bear);
        pay(game, bestow, 5);
        game.getAction().moveToGraveyard(bear, null);
        Game copy = snapshot(game);
        assertTrue(copy.getStack().peekAbility().getHostCard().isAura());
        copy.getStack().resolveStack();
        Card resolved = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == satyr.getId()).findFirst().orElseThrow();
        assertTrue(resolved.isCreature());
        assertFalse(resolved.isAura());
        assertFalse(resolved.isBestowed());
        assertEquals(resolved.getNetPower(), 4);
        assertEquals(resolved.getNetToughness(), 2);
        assertTrue(game.getStack().peekAbility().getHostCard().isBestowed());
    }

    @Test
    public void bestowAndCloneContainersAreIndependentAfterCopy() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Forest", 5, owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card card = addCardToZone("Boon Satyr", owner, ZoneType.Hand);
        SpellAbility bestow = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isBestow()).findFirst().orElseThrow();
        bestow.setActivatingPlayer(owner);
        bestow.getTargets().add(bear);
        pay(game, bestow, 5);
        Card original = game.getStack().peekAbility().getHostCard();
        Game copy = snapshot(game);
        Card mapped = copy.getStack().peekAbility().getHostCard();
        mapped.unanimateBestow(false);
        assertFalse(mapped.isAura());
        assertTrue(mapped.isCreature());
        assertTrue(original.isBestowed());
        assertTrue(original.isAura());
        assertFalse(original.isCreature());
    }

    @Test
    public void prototypeCloneGraphDoesNotRetainOriginalAbilityObjects() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Swamp", 3, owner);
        Card card = addCardToZone("Phyrexian Fleshgorger", owner, ZoneType.Hand);
        SpellAbility prototype = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.hasParam("Prototype")).findFirst().orElseThrow();
        prototype.setActivatingPlayer(owner);
        pay(game, prototype, 3);
        Card original = game.getStack().peekAbility().getHostCard();
        Game copy = snapshot(game);
        Card mapped = copy.getStack().peekAbility().getHostCard();
        assertEquals(mapped.getCloneStates().size(), 1);
        forge.game.card.CardCloneStates states = mapped.getCloneStates().values().iterator().next();
        assertSame(states.getOrigin().getGame(), copy);
        assertSame(states.getSource(), copy.getStack().peekAbility());
        assertSame(states.getSource().getHostCard().getGame(), copy);
        assertNotSame(states, original.getCloneStates().values().iterator().next());
        assertNotSame(mapped.getCurrentState(), original.getCurrentState());
        mapped.removeCloneStates();
        assertEquals(original.getNetPower(), 3);
        assertEquals(original.getCMC(), 3);
        assertEquals(original.getCloneStates().size(), 1);
    }

    @Test
    public void paidDisguiseTurnUpCleansOnlyTheCopiedFaceDownState() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        addCards("Island", 3, owner);
        Card card = addCardToZone("Dog Walker", owner, ZoneType.Hand);
        SpellAbility disguise = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isCastFaceDown()).findFirst().orElseThrow();
        disguise.setActivatingPlayer(owner);
        pay(game, disguise, 3);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        addCards("Plains", 2, owner);
        Card source = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertTrue(source.hasKeyword(forge.game.keyword.Keyword.WARD));
        Game copy = snapshot(game);
        Player copiedOwner = copy.getPlayer(owner.getId());
        Card mapped = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        SpellAbility turnUp = mapped.getAllPossibleAbilities(copiedOwner, false).stream()
                .filter(sa -> sa.hasParam("DisguiseUp")).findFirst().orElseThrow();
        turnUp.setActivatingPlayer(copiedOwner);
        assertTrue(turnUp.canPlay());
        assertTrue(ComputerUtil.playNoStack(copiedOwner, turnUp, copy, false));
        assertEquals(turnUp.getPayingMana().size(), 2);
        assertFalse(mapped.isFaceDown());
        assertEquals(mapped.getName(), "Dog Walker");
        assertEquals(mapped.getNetPower(), 3);
        assertEquals(mapped.getView().getCurrentState().getPower(), 3);
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.WARD));
        mapped.turnFaceDownNoUpdate();
        mapped.updateKeywordsCache();
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.WARD), "Fresh face-down state");
        assertTrue(source.isFaceDown());
        assertTrue(source.hasKeyword(forge.game.keyword.Keyword.WARD), "Source callback was not run");
    }

    @Test
    public void unpaidWardCountersTheTargetingSpellInTheCopiedGame() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCards("Island", 3, owner);
        Card card = addCardToZone("Dog Walker", owner, ZoneType.Hand);
        SpellAbility disguise = card.getAllPossibleAbilities(owner, false).stream()
                .filter(sa -> sa.isSpell() && sa.isCastFaceDown()).findFirst().orElseThrow();
        disguise.setActivatingPlayer(owner);
        pay(game, disguise, 3);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        Card victim = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == card.getId()).findFirst().orElseThrow();
        assertTrue(victim.hasKeyword(forge.game.keyword.Keyword.WARD));
        addCards("Mountain", 3, opponent);
        SpellAbility bolt = addCardToZone("Lightning Bolt", opponent, ZoneType.Hand).getFirstSpellAbility();
        bolt.setActivatingPlayer(opponent);
        bolt.getTargets().add(victim);
        pay(game, bolt, 1);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 2);
        assertTrue(game.getStack().peekAbility().isTrigger());
        SpellAbility response = addCardToZone("Lightning Strike", opponent, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(opponent);
        response.getTargets().add(owner);
        pay(game, response, 2);
        Game copy = snapshot(game);
        assertEquals(copy.getStack().size(), 3);
        copy.getStack().resolveStack();
        assertEquals(copy.getPlayer(owner.getId()).getLife(), 37);
        copy.getStack().resolveStack();
        assertTrue(copy.getStack().isEmpty(), "Ward counters the unpaid Bolt");
        assertTrue(copy.getPlayer(opponent.getId()).getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(candidate -> candidate.getName().equals("Lightning Bolt")));
        Card protectedCard = copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(candidate -> candidate.getId() == victim.getId()).findFirst().orElseThrow();
        assertTrue(protectedCard.isFaceDown());
        assertEquals(protectedCard.getDamage(), 0);
        assertEquals(game.getStack().size(), 3);
        assertEquals(owner.getLife(), 40);
    }
}
