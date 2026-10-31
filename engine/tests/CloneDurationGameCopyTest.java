package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
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

/** Temporary copy frames, restored memory and keyword deadlines belong to the copied world. */
public class CloneDurationGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("clone" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Clone fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 4; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void pay(Game game, SpellAbility ability, Player owner, int mana) {
        ability.setActivatingPlayer(owner);
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native action payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Card cast(Game game, String name, Player owner, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        return find(game, card.getId());
    }

    private Card find(Game game, int identity) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity)
                .findFirst().orElseThrow();
    }

    private SpellAbility activated(Card card, ApiType api, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private Game snapshot(Game game) {
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        return copy;
    }

    private void triggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }



    /** Run native phase hooks, not individual counter-reset methods. No priority is offered. */
    private void nextTurn(Game game) {
        int previousTurn = game.getPhaseHandler().getTurn();
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)), "Native turn advance");
        settle(game);
        assertEquals(game.getPhaseHandler().getTurn(), previousTurn + 1);
        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.MAIN1);
    }

    private void settle(Game game) {
        triggers(game);
        int guard = 0;
        while (!game.getStack().isEmpty()) {
            assertTrue(++guard < 40, "Bounded fixture trigger resolution");
            game.getStack().resolveStack();
            triggers(game);
        }
    }

    private void round(Game game, int ownerId) {
        for (int seat = 0; seat < 4; seat++) {
            nextTurn(game);
        }
        assertEquals(game.getPhaseHandler().getPlayerTurn().getId(), ownerId);
    }



    private void advance(Game game, PhaseType phase) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(phase, () -> settle(game)));
        settle(game);
    }

    private Player prepare(Game game, String land, int count) {
        Player owner = game.getPlayers().get(0);
        for (int i = 0; i < count; i++) addCardToZone(land, owner, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, owner, 1);
        return owner;
    }

    private void targeted(Game game, String name, Player owner, Card target, int mana) {
        Card spell = addCardToZone(name, owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(target);
        pay(game, spell.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        settle(game);
    }

    private Card mirror(Game game, Player owner, Card target) {
        Card mirror = cast(game, "Mirage Mirror", owner, 3);
        SpellAbility ability = activated(mirror, ApiType.Clone, owner);
        ability.getTargets().add(target);
        pay(game, ability, owner, 2);
        game.getStack().resolveStack();
        settle(game);
        return mirror;
    }

    @org.testng.annotations.DataProvider(name = "copyKinds")
    public Object[][] copyKinds() { return new Object[][] {{"Serra Angel"}, {"Forest"}, {"Sol Ring"}, {"Glorious Anthem"}}; }

    @Test(dataProvider = "copyKinds")
    public void mirageMirrorReturnsToItsArtifactFrame(String name) {
        Game game = pod();
        Player owner = prepare(game, "Island", 5);
        Card target = addCardToZone(name, game.getPlayers().get(1), ZoneType.Battlefield);
        Card mirror = mirror(game, owner, target);
        assertEquals(mirror.getName(), name);
        Game copy = snapshot(game);
        Card copied = find(copy, mirror.getId());
        assertEquals(copied.getName(), name);
        nextTurn(copy);
        assertEquals(copied.getName(), "Mirage Mirror");
        assertTrue(copied.isArtifact());
        assertFalse(copied.isCreature());
        assertFalse(copied.isLand());
        assertTrue(copied.getCloneStates().isEmpty());
        assertEquals(mirror.getName(), name);
        assertEquals(mirror.getCloneStates().size(), 1);
    }

    @Test
    public void mirrorweaveRestoresAllAffectedCreaturesInOnlyTheCopy() {
        Game game = pod();
        Player owner = prepare(game, "Plains", 4);
        Card first = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", game.getPlayers().get(1), ZoneType.Battlefield);
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        targeted(game, "Mirrorweave", owner, angel, 4);
        assertEquals(first.getName(), "Serra Angel");
        assertEquals(second.getName(), "Serra Angel");
        Game copy = snapshot(game);
        nextTurn(copy);
        assertEquals(find(copy, first.getId()).getName(), "Grizzly Bears");
        assertEquals(find(copy, second.getId()).getName(), "Runeclaw Bear");
        assertEquals(find(copy, angel.getId()).getName(), "Serra Angel");
        assertEquals(first.getName(), "Serra Angel");
        assertEquals(second.getName(), "Serra Angel");
    }

    @Test
    public void shapesharerExpiresAtItsControllersNextTurnRatherThanTheNextEndStep() {
        Game game = pod();
        Player owner = prepare(game, "Island", 5);
        Card source = cast(game, "Shapesharer", owner, 2);
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        SpellAbility ability = activated(source, ApiType.Pump, owner);
        ability.getTargets().add(source);
        ability.getSubAbility().getTargets().add(angel);
        pay(game, ability, owner, 3);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(source.getName(), "Serra Angel");
        Game copy = snapshot(game);
        Card copied = find(copy, source.getId());
        for (int i = 0; i < 3; i++) {
            nextTurn(copy);
            assertEquals(copied.getName(), "Serra Angel");
        }
        nextTurn(copy);
        assertEquals(copied.getName(), "Shapesharer");
        assertEquals(copied.getNetPower(), 1);
        assertFalse(copied.hasKeyword(forge.game.keyword.Keyword.FLYING));
        assertEquals(source.getName(), "Serra Angel");
    }

    @Test
    public void vesuvanCopyFrameEndsOnTheCopiedNativeFacedownEvent() {
        Game game = pod();
        Player owner = prepare(game, "Island", 5);
        addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        Card source = cast(game, "Vesuvan Shapeshifter", owner, 5);
        assertEquals(source.getName(), "Serra Angel");
        assertEquals(source.getCloneStates().size(), 1);
        Game copy = snapshot(game);
        Card copied = find(copy, source.getId());
        assertTrue(copied.turnFaceDown(true));
        settle(copy);
        assertTrue(copied.isFaceDown());
        assertEquals(copied.getNetPower(), 2);
        assertTrue(copied.getCloneStates().isEmpty());
        assertEquals(source.getName(), "Serra Angel");
        assertFalse(source.isFaceDown());
        assertEquals(source.getCloneStates().size(), 1);
    }

    @Test
    public void eiganjoTokensLoseOnlyTheirTemporaryHasteAndMenace() {
        Game game = pod();
        Player owner = prepare(game, "Plains", 2);
        addCardToZone("Mountain", owner, ZoneType.Battlefield);
        addCardToZone("Mountain", owner, ZoneType.Battlefield);
        Card spell = addCardToZone("Eiganjo Uprising", owner, ZoneType.Hand);
        spell.getFirstSpellAbility().setXManaCostPaid(2);
        pay(game, spell.getFirstSpellAbility(), owner, 4);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 2L);
        Game copy = snapshot(game);
        nextTurn(copy);
        for (Card token : copy.getPlayer(owner.getId()).getCardsIn(ZoneType.Battlefield)) {
            if (!token.isToken()) continue;
            assertFalse(token.hasKeyword(forge.game.keyword.Keyword.HASTE));
            assertFalse(token.hasKeyword(forge.game.keyword.Keyword.MENACE));
            assertTrue(token.hasKeyword(forge.game.keyword.Keyword.VIGILANCE));
            assertEquals(token.getNetPower(), 2);
        }
        for (Card token : owner.getCardsIn(ZoneType.Battlefield)) {
            if (!token.isToken()) continue;
            assertTrue(token.hasKeyword(forge.game.keyword.Keyword.HASTE));
            assertTrue(token.hasKeyword(forge.game.keyword.Keyword.MENACE));
        }
    }

    @org.testng.annotations.DataProvider(name = "memoryOwners")
    public Object[][] memoryOwners() { return new Object[][] {{false}, {true}}; }

    @Test(dataProvider = "memoryOwners")
    public void savedCopyMemoryIsReboundAndFiltersOnlyEliminatedOwnersCards(boolean eliminate) {
        Game game = pod();
        Player owner = prepare(game, "Plains", 4);
        Player other = game.getPlayers().get(2);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card imprint = addCardToZone("Forest", owner, ZoneType.Exile);
        Card othersCard = addCardToZone("Mountain", other, ZoneType.Exile);
        bear.addImprintedCard(imprint);
        bear.addImprintedCard(othersCard);
        bear.addRemembered(imprint);
        bear.addRemembered(othersCard);
        bear.addRemembered(other);
        bear.addRemembered("ignored-scalar");
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        targeted(game, "Mirrorweave", owner, angel, 4);
        Game copy = snapshot(game);
        Card copied = find(copy, bear.getId());
        copied.addRemembered("while-copied");
        if (eliminate) {
            copy.getPlayer(other.getId()).concede();
            settle(copy);
            assertFalse(copy.getPlayer(other.getId()).isInGame());
        }
        nextTurn(copy);
        assertEquals(copied.getName(), "Grizzly Bears");
        assertEquals(copied.getImprintedCards().size(), eliminate ? 1 : 2);
        assertTrue(copied.getImprintedCards().contains(find(copy, imprint.getId())));
        assertTrue(copied.isRemembered(copy.getPlayer(other.getId())));
        assertTrue(copied.isRemembered(find(copy, imprint.getId())));
        assertFalse(copied.isRemembered("ignored-scalar"));
        assertFalse(copied.isRemembered("while-copied"));
        for (Object remembered : copied.getRemembered()) {
            if (remembered instanceof Card card) assertSame(card.getGame(), copy);
            if (remembered instanceof Player player) assertSame(player.getGame(), copy);
        }
        assertEquals(com.google.common.collect.Iterables.size(copied.getRemembered()), eliminate ? 2 : 3);
        assertTrue(other.isInGame());
        assertEquals(bear.getName(), "Serra Angel");
        assertFalse(bear.getRemembered().iterator().hasNext());
    }

    @Test
    public void overlappingMirrorFramesBothExpireWithoutRemovingTheOriginalFrames() {
        Game game = pod();
        Player owner = prepare(game, "Island", 7);
        Card source = cast(game, "Mirage Mirror", owner, 3);
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        for (Card target : new Card[] {angel, bear}) {
            SpellAbility ability = activated(source, ApiType.Clone, owner);
            ability.getTargets().add(target);
            pay(game, ability, owner, 2);
        }
        game.getStack().resolveStack();
        game.getStack().resolveStack();
        settle(game);
        assertEquals(source.getName(), "Serra Angel");
        assertEquals(source.getCloneStates().size(), 2);
        Game copy = snapshot(game);
        nextTurn(copy);
        assertEquals(find(copy, source.getId()).getName(), "Mirage Mirror");
        assertTrue(find(copy, source.getId()).getCloneStates().isEmpty());
        assertEquals(source.getCloneStates().size(), 2);
    }

    @Test
    public void departedCopyIncarnationCannotOverwriteTheNewArtifactsMemory() {
        Game game = pod();
        Player owner = prepare(game, "Plains", 6);
        Card angel = addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        Card source = mirror(game, owner, angel);
        targeted(game, "Cloudshift", owner, source, 1);
        Card current = find(game, source.getId());
        assertEquals(current.getName(), "Mirage Mirror");
        current.addRemembered("new-incarnation");
        Game copy = snapshot(game);
        nextTurn(copy);
        assertEquals(find(copy, source.getId()).getName(), "Mirage Mirror");
        assertTrue(find(copy, source.getId()).isRemembered("new-incarnation"));
        assertTrue(current.isRemembered("new-incarnation"));
    }

    @Test
    public void cursedMirrorLosesItsTemporaryCopyAndHasteTogether() {
        Game game = pod();
        Player owner = prepare(game, "Mountain", 3);
        addCardToZone("Serra Angel", owner, ZoneType.Battlefield);
        Card source = cast(game, "Cursed Mirror", owner, 3);
        assertEquals(source.getName(), "Serra Angel");
        assertTrue(source.hasKeyword(forge.game.keyword.Keyword.HASTE));
        Game copy = snapshot(game);
        nextTurn(copy);
        Card copied = find(copy, source.getId());
        assertEquals(copied.getName(), "Cursed Mirror");
        assertTrue(copied.isArtifact());
        assertFalse(copied.isCreature());
        assertFalse(copied.hasKeyword(forge.game.keyword.Keyword.HASTE));
        assertEquals(source.getName(), "Serra Angel");
        assertTrue(source.hasKeyword(forge.game.keyword.Keyword.HASTE));
    }
}
