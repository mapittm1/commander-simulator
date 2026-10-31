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

/** Animation layers and their reversal commands must remain bound to the copied world. */
public class AnimationGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("animation" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Animation fixture"));
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
        return owner;
    }

    private void endTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN, () -> settle(game)));
        settle(game);
        nextTurn(game);
    }

    @org.testng.annotations.DataProvider(name = "stackCopies")
    public Object[][] stackCopies() { return new Object[][] {{false}, {true}}; }

    @Test(dataProvider = "stackCopies")
    public void factoryAnimationExpiresWithoutChangingTheOriginalLand(boolean includeStack) {
        Game game = pod();
        Player owner = prepare(game, "Island", 1);
        Card factory = addCardToZone("Mishra's Factory", owner, ZoneType.Battlefield);
        SpellAbility animate = activated(factory, ApiType.Animate, owner);
        pay(game, animate, owner, 1);
        game.getStack().resolveStack();
        settle(game);
        assertTrue(factory.isCreature());
        assertEquals(factory.getNetPower(), 2);
        Game copy = new GameCopier(game).withStack(includeStack).makeCopy();
        Card copied = find(copy, factory.getId());
        assertTrue(copied.isCreature());
        assertTrue(copied.isArtifact());
        endTurn(copy);
        assertFalse(copied.isCreature());
        assertFalse(copied.isArtifact());
        assertTrue(copied.isLand());
        assertTrue(factory.isCreature());
        assertTrue(factory.isArtifact());
    }

    @Test
    public void animateLandExpiresInOnlyTheCopiedOpponentsLand() {
        Game game = pod();
        Player owner = prepare(game, "Forest", 1);
        Card land = addCardToZone("Mountain", game.getPlayers().get(1), ZoneType.Battlefield);
        Card spell = addCardToZone("Animate Land", owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(land);
        pay(game, spell.getFirstSpellAbility(), owner, 1);
        game.getStack().resolveStack();
        settle(game);
        assertTrue(land.isCreature());
        assertEquals(land.getNetPower(), 3);
        Game copy = snapshot(game);
        Card copied = find(copy, land.getId());
        endTurn(copy);
        assertFalse(copied.isCreature());
        assertTrue(copied.isLand());
        assertEquals(copied.getController().getId(), land.getController().getId());
        assertTrue(land.isCreature());
    }

    @Test
    public void suddenSpoilingRestoresTheCopiedPowerAndFlyingAfterExpiration() {
        Game game = pod();
        Player owner = prepare(game, "Swamp", 3);
        Player opponent = game.getPlayers().get(1);
        Card angel = addCardToZone("Serra Angel", opponent, ZoneType.Battlefield);
        Card spell = addCardToZone("Sudden Spoiling", owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(opponent);
        pay(game, spell.getFirstSpellAbility(), owner, 3);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(angel.getNetPower(), 0);
        assertEquals(angel.getNetToughness(), 2);
        assertFalse(angel.hasKeyword(forge.game.keyword.Keyword.FLYING));
        Game copy = snapshot(game);
        Card copied = find(copy, angel.getId());
        assertEquals(copied.getNetPower(), 0);
        assertFalse(copied.hasKeyword(forge.game.keyword.Keyword.FLYING));
        endTurn(copy);
        assertEquals(copied.getNetPower(), 4);
        assertEquals(copied.getNetToughness(), 4);
        assertTrue(copied.hasKeyword(forge.game.keyword.Keyword.FLYING));
        assertEquals(angel.getNetPower(), 0);
        assertFalse(angel.hasKeyword(forge.game.keyword.Keyword.FLYING));
    }

    @org.testng.annotations.DataProvider(name = "licidCopies")
    public Object[][] licidCopies() {
        String[] names = {"Calming Licid", "Convulsing Licid", "Corrupting Licid", "Dominating Licid", "Enraging Licid", "Flanking Licid", "Gliding Licid", "Leeching Licid", "Nurturing Licid", "Quickening Licid", "Stinging Licid", "Tempting Licid", "Transmogrifying Licid"};
        List<Object[]> rows = new ArrayList<>();
        for (String name : names) {
            rows.add(new Object[] {name, false});
            rows.add(new Object[] {name, true});
        }
        return rows.toArray(new Object[0][]);
    }

    @Test(dataProvider = "licidCopies")
    public void endingALicidAnimationCannotExecuteTheOriginalGamesClosure(String name, boolean includeStack) {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        for (String land : new String[] {"Plains", "Island", "Swamp", "Mountain", "Forest"}) {
            for (int i = 0; i < 4; i++) addCardToZone(land, owner, ZoneType.Battlefield);
        }
        Card licid = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, licid.getFirstSpellAbility(), owner, licid.getManaCost().getCMC());
        game.getStack().resolveStack();
        settle(game);
        licid = find(game, licid.getId());
        round(game, owner.getId());
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        SpellAbility animate = activated(licid, ApiType.Animate, owner);
        animate.getSubAbility().getTargets().add(bear);
        pay(game, animate, owner, animate.getPayCosts().getTotalMana().getCMC());
        game.getStack().resolveStack();
        settle(game);
        assertTrue(licid.isAura());
        assertFalse(licid.isCreature());
        if (name.equals("Gliding Licid")) assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.FLYING));
        Game copy = new GameCopier(game).withStack(includeStack).makeCopy();
        Card copied = find(copy, licid.getId());
        Player copiedOwner = copy.getPlayer(owner.getId());
        assertTrue(copied.getSpellAbilities().stream().noneMatch(sa -> sa.getApi() == ApiType.Animate));
        SpellAbility revert = copied.getSpellAbilities().stream()
                .filter(sa -> sa instanceof forge.game.spellability.AbilityStatic
                        && sa.getDescription().contains("End Effect")).findFirst().orElseThrow();
        revert.setActivatingPlayer(copiedOwner);
        assertTrue(revert.canPlay());
        assertTrue(ComputerUtil.playNoStack(copiedOwner, revert, copy, false));
        assertEquals(revert.getPayingMana().size(), 1);
        settle(copy);
        assertTrue(licid.isAura(), "Reverting a copy must not execute the source world's closure");
        assertFalse(licid.isCreature());
        if (name.equals("Gliding Licid")) assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.FLYING));
        assertTrue(copied.isCreature());
        assertFalse(copied.isAura());
        assertFalse(find(copy, bear.getId()).hasKeyword(forge.game.keyword.Keyword.FLYING));
        assertEquals(find(copy, bear.getId()).getNetPower(), 2);
        assertEquals(find(copy, bear.getId()).getNetToughness(), 2);
        assertTrue(copied.getSpellAbilities().stream().anyMatch(sa -> sa.getApi() == ApiType.Animate));
    }
    @Test
    public void temporaryFactoryLayerDoesNotErasePermanentAwakeningOrItsName() {
        Game game = pod();
        Player owner = prepare(game, "Forest", 6);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, owner, 1);
        Card factory = addCardToZone("Mishra's Factory", owner, ZoneType.Battlefield);
        Card spell = addCardToZone("Awakening of Vitu-Ghazi", owner, ZoneType.Hand);
        spell.getFirstSpellAbility().getTargets().add(factory);
        pay(game, spell.getFirstSpellAbility(), owner, 5);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(factory.getNetPower(), 9);
        SpellAbility animate = activated(factory, ApiType.Animate, owner);
        pay(game, animate, owner, 1);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(factory.getNetPower(), 11);
        Game copy = snapshot(game);
        Card copied = find(copy, factory.getId());
        assertEquals(copied.getNetPower(), 11);
        endTurn(copy);
        assertTrue(copied.isCreature());
        assertTrue(copied.isLand());
        assertFalse(copied.isArtifact());
        assertEquals(copied.getName(), "Vitu-Ghazi");
        assertEquals(copied.getNetPower(), 9);
        assertEquals(copied.getNetToughness(), 9);
        assertTrue(copied.hasKeyword(forge.game.keyword.Keyword.HASTE));
        assertEquals(factory.getNetPower(), 11);
        assertTrue(factory.isArtifact());
    }

    @Test
    public void naturalAffinityExpiresForEveryCopiedPlayersLands() {
        Game game = pod();
        Player owner = prepare(game, "Forest", 3);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, owner, 1);
        Card opposing = addCardToZone("Mountain", game.getPlayers().get(1), ZoneType.Battlefield);
        cast(game, "Natural Affinity", owner, 3);
        assertTrue(opposing.isCreature());
        Game copy = snapshot(game);
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count(), 4L);
        endTurn(copy);
        assertTrue(copy.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isCreature));
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).size(), 4);
        assertTrue(opposing.isCreature());
        assertEquals(game.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count(), 4L);
    }

    @Test
    public void changedTraitCollectionsKeepCanonicalAbilitiesWithoutSharingMutableCollections() {
        Game game = pod();
        Player owner = game.getPlayers().get(0);
        Card host = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        SpellAbility ability = host.getFirstSpellAbility();
        ability.setActivatingPlayer(owner);
        long timestamp = game.getNextTimestamp();
        host.addChangedCardTraits(List.of(ability), List.of(), List.of(), List.of(), null, timestamp, 0);
        forge.game.card.CardTraitChanges source =
                (forge.game.card.CardTraitChanges) host.getChangedCardTraits().get(timestamp, 0L);
        Game copy = snapshot(game);
        Card copied = find(copy, host.getId());
        forge.game.card.CardTraitChanges changed =
                (forge.game.card.CardTraitChanges) copied.getChangedCardTraits().get(timestamp, 0L);
        assertNotSame(changed, source);
        assertNotSame(changed.getAbilities(), source.getAbilities());
        assertSame(changed.getAbilities().iterator().next(), copied.getFirstSpellAbility());
        assertSame(changed.getAbilities().iterator().next().getHostCard(), copied);
        assertSame(changed.getAbilities().iterator().next().getActivatingPlayer(), copy.getPlayer(owner.getId()));
        changed.getAbilities().clear();
        assertEquals(source.getAbilities().size(), 1);
        assertNotNull(copied.getFirstSpellAbility());
    }

}
