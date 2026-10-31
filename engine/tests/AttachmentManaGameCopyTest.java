package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
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

/** Attached creature types and mana-granted keywords retain independent lifetimes. */
public class AttachmentManaGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("expiry" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Keyword expiry"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 5; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        return game;
    }
    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture stack did not settle within its bound");
    }
    private Card find(Game game, int id) {
        return game.getCardsInGame().stream().filter(c -> c.getId() == id).findFirst().orElseThrow();
    }
    private void pay(Game game, SpellAbility ability, Player player, int mana) {
        ability.setActivatingPlayer(player);
        assertTrue(ability.canPlay(), "Action admission");
        assertTrue(ability.checkRestrictions(player), "Native restrictions");
        assertTrue(ComputerUtil.playStack(ability, player, game), "Native payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getStack().resolveStack();
        settle(game);
    }
    private Card cast(Game game, String name, Player player, GameEntity target, int mana) {
        Card card = addCardToZone(name, player, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(ability.canTarget(c)); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
        return find(game, card.getId());
    }
    private void activate(Game game, Card card, Player player, GameEntity target, int mana, ApiType api) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(player);
        if (target instanceof Card c) { ability.getTargets().add(c); assertTrue(ability.canTarget(c)); assertTrue(c.canBeTargetedBy(ability)); }
        else if (target instanceof Player p) { ability.getTargets().add(p); assertTrue(p.canBeTargetedBy(ability)); }
        pay(game, ability, player, mana);
    }
    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
    }
    private void attachment(String name, int castCost, int equipCost, String mode, boolean withStack) {
        Game game = pod(); Player owner = game.getPlayers().get(0);
        for (int i = 0; i < 15; i++) addCardToZone("Plains", owner, ZoneType.Battlefield);
        addCardToZone("Mountain", owner, ZoneType.Battlefield);
        addCardToZone("Island", owner, ZoneType.Battlefield);
        Card bearer = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", owner, ZoneType.Battlefield);
        Card equipment = cast(game, name, owner, null, castCost);
        assertTrue(equipment.isCreature());
        activate(game, equipment, owner, bearer, equipCost, ApiType.Attach);
        assertFalse(equipment.isCreature()); assertSame(equipment.getEntityAttachedTo(), bearer);
        GameCopier copier = new GameCopier(game).withStack(withStack);
        Game copied = copier.makeCopy(); Player acting = copied.getPlayers().get(0);
        Card mapped = find(copied, equipment.getId()); Card mappedBearer = find(copied, bearer.getId());
        assertSame(mapped.getEntityAttachedTo(), mappedBearer);
        assertFalse(mapped.isCreature());
        if (mode.equals("detach")) activate(copied, mapped, acting, null, equipCost, ApiType.Unattach);
        else if (mode.equals("bearer")) cast(copied, "Unsummon", acting, mappedBearer, 1);
        else if (mode.equals("source")) cast(copied, "Disperse", acting, mapped, 2);
        else {
            activate(copied, mapped, acting, find(copied, second.getId()), equipCost, ApiType.Attach);
            assertFalse(mapped.isCreature());
            assertSame(mapped.getEntityAttachedTo(), find(copied, second.getId()));
            activate(copied, mapped, acting, null, equipCost, ApiType.Unattach);
        }
        Card current = find(copied, equipment.getId());
        if (mode.equals("source")) {
            current = copied.getPlayers().get(0).getCardsIn(ZoneType.Hand).stream()
                    .filter(c -> c.getId() == equipment.getId()).findFirst().orElseThrow();
            assertTrue(current.isCreature(), "Returned equipment has printed creature type");
        } else assertNull(current.getEntityAttachedTo());
        assertTrue(current.isCreature(), "Copied reconfigure cleanup must restore creature type");
        assertFalse(equipment.isCreature()); assertSame(equipment.getEntityAttachedTo(), bearer);
        activate(game, equipment, owner, null, equipCost, ApiType.Unattach);
        assertTrue(equipment.isCreature()); assertNull(equipment.getEntityAttachedTo());
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    @Test public void LionSashdetachStack() { attachment("Lion Sash", 2, 2, "detach", true); }
    @Test public void LionSashdetachNoStack() { attachment("Lion Sash", 2, 2, "detach", false); }
    @Test public void LionSashbearerStack() { attachment("Lion Sash", 2, 2, "bearer", true); }
    @Test public void LionSashbearerNoStack() { attachment("Lion Sash", 2, 2, "bearer", false); }
    @Test public void LionSashsourceStack() { attachment("Lion Sash", 2, 2, "source", true); }
    @Test public void LionSashsourceNoStack() { attachment("Lion Sash", 2, 2, "source", false); }
    @Test public void LionSashreattachStack() { attachment("Lion Sash", 2, 2, "reattach", true); }
    @Test public void LionSashreattachNoStack() { attachment("Lion Sash", 2, 2, "reattach", false); }
    @Test public void LizardBladesdetachStack() { attachment("Lizard Blades", 2, 2, "detach", true); }
    @Test public void LizardBladesdetachNoStack() { attachment("Lizard Blades", 2, 2, "detach", false); }
    @Test public void LizardBladesbearerStack() { attachment("Lizard Blades", 2, 2, "bearer", true); }
    @Test public void LizardBladesbearerNoStack() { attachment("Lizard Blades", 2, 2, "bearer", false); }
    @Test public void LizardBladessourceStack() { attachment("Lizard Blades", 2, 2, "source", true); }
    @Test public void LizardBladessourceNoStack() { attachment("Lizard Blades", 2, 2, "source", false); }
    @Test public void LizardBladesreattachStack() { attachment("Lizard Blades", 2, 2, "reattach", true); }
    @Test public void LizardBladesreattachNoStack() { attachment("Lizard Blades", 2, 2, "reattach", false); }
    private void mana(String sourceName, boolean permanent, boolean withStack) {
        Game game = pod(); Player owner = game.getPlayers().get(0);
        for (int i = 0; i < 12; i++) addCardToZone("Mountain", owner, ZoneType.Battlefield);
        Card source = permanent || sourceName.equals("Arena of Glory")
                ? addCardToZone(sourceName, owner, ZoneType.Battlefield)
                : cast(game, sourceName, owner, null, sourceName.equals("Generator Servant") ? 2 : 3);
        if (sourceName.equals("Generator Servant")) for (int i = 0; i < 4; i++) nextTurn(game);
        SpellAbility manaAbility = source.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && sa.hasParam("AddsKeywords")).findFirst().orElseThrow();
        manaAbility.setActivatingPlayer(owner);
        assertTrue(manaAbility.canPlay());
        assertTrue(ComputerUtil.playNoStack(owner, manaAbility, game, false));
        if (sourceName.equals("Generator Servant")) assertTrue(source.isInZone(ZoneType.Graveyard));
        else assertTrue(source.isTapped());
        if (sourceName.equals("Arena of Glory")) assertEquals(source.getExertedThisTurn(), 1);
        if (permanent) assertEquals(owner.getLife(), 37);
        Card dragon = addCardToZone("Furnace Whelp", owner, ZoneType.Hand);
        SpellAbility spell = dragon.getFirstSpellAbility(); spell.setActivatingPlayer(owner);
        assertTrue(spell.canPlay()); assertTrue(ComputerUtil.playStack(spell, owner, game));
        SpellAbility pending = game.getStack().peekAbility();
        assertEquals(pending.getPayingMana().size(), 4);
        assertTrue(pending.getPayingMana().stream().anyMatch(m -> m.getSourceCard().getId() == source.getId()),
                "Must spend the actual keyword-granting mana");
        game.getStack().resolveStack(); settle(game); dragon = find(game, dragon.getId());
        assertTrue(dragon.hasKeyword("Haste"));
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copied = copier.makeCopy();
        Card mapped = find(copied, dragon.getId()); assertTrue(mapped.hasKeyword("Haste"));
        nextTurn(copied); assertEquals(mapped.hasKeyword("Haste"), permanent);
        assertTrue(dragon.hasKeyword("Haste")); nextTurn(game);
        assertEquals(dragon.hasKeyword("Haste"), permanent);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    @Test public void orbStack() { mana("Carnelian Orb of Dragonkind", false, true); }
    @Test public void orbNoStack() { mana("Carnelian Orb of Dragonkind", false, false); }
    @Test public void hallStack() { mana("Hall of the Bandit Lord", true, true); }
    @Test public void hallNoStack() { mana("Hall of the Bandit Lord", true, false); }
    @Test public void generatorStack() { mana("Generator Servant", false, true); }
    @Test public void generatorNoStack() { mana("Generator Servant", false, false); }
    @Test public void arenaStack() { mana("Arena of Glory", false, true); }
    @Test public void arenaNoStack() { mana("Arena of Glory", false, false); }
}
