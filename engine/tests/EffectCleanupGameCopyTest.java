package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.GamePieceType;
import forge.StaticData;
import forge.game.ability.AbilityFactory;
import forge.game.ability.effects.AddTurnEffect;
import forge.game.card.CardCopyService;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntity;
import forge.game.combat.CombatUtil;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Temporary prevention effects must not survive their native cleanup in copies. */
public class EffectCleanupGameCopyTest extends AITest {
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
        ability.setActivatingPlayer(player);
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
    private void combat(Game game, int attackerId, int turn) {
        Player attacker = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, turn);
        game.getPhaseHandler().setCombat(new Combat(attacker));
        Card creature = find(game, attackerId);
        game.getCombat().addAttacker(creature, game.getPlayers().get(0));
        CombatUtil.checkDeclaredAttacker(game, creature, game.getCombat(), true); settle(game);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game))); settle(game);
    }
    private void fog(boolean withStack) {
        Game game = pod(); Player owner = game.getPlayers().get(0);
        for (Player p : game.getPlayers()) for (int i = 0; i < 4; i++) addCardToZone("Forest", p, ZoneType.Battlefield);
        nextTurn(game);
        Card creature = cast(game, "Grizzly Bears", game.getPlayers().get(1), null, 2);
        for (int i = 0; i < 3; i++) nextTurn(game);
        assertSame(game.getPhaseHandler().getPlayerTurn(), owner);
        cast(game, "Fog", owner, null, 1);
        assertEquals(owner.getCardsIn(ZoneType.Command).size(), 1);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copied = copier.makeCopy();
        nextTurn(game); combat(game, creature.getId(), game.getPhaseHandler().getTurn());
        assertEquals(owner.getCardsIn(ZoneType.Command).size(), 0, "Original Fog effect expires");
        assertEquals(owner.getLife(), 38, "Original combat damage after Fog expires");
        assertEquals(copied.getPlayers().get(0).getLife(), 40, "Original combat does not damage the copy");
        nextTurn(copied); combat(copied, creature.getId(), copied.getPhaseHandler().getTurn());
        assertEquals(copied.getPlayers().get(0).getLife(), 38, "Copied combat damage after Fog expires");
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(), copier.getUnmappedSnapshotCommands().toString());
    }
    @Test public void fogStack() { fog(true); }
    @Test public void fogNoStack() { fog(false); }
    private Player prepare(Game game) {
        for (Player p : game.getPlayers()) for (String name : List.of("Forest", "Swamp", "Island", "Mountain", "Plains"))
            for (int i = 0; i < 8; i++) addCardToZone(name, p, ZoneType.Battlefield);
        return game.getPlayers().get(0);
    }
    private long effects(Game game) {
        return game.getCardsIn(ZoneType.Command).stream().filter(c -> c.getGamePieceType() == GamePieceType.EFFECT).count();
    }
    private void regenExpiry(boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        cast(game, "Regenerate", owner, bear, 2); assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        nextTurn(game); nextTurn(copy);
        cast(game, "Murder", owner, find(game, bear.getId()), 3);
        assertNotNull(game.getCardsIn(ZoneType.Graveyard).stream().filter(c -> c.getId() == bear.getId()).findFirst().orElse(null));
        cast(copy, "Murder", copy.getPlayers().get(0), find(copy, bear.getId()), 3);
        assertEquals(find(copy, bear.getId()).getZone().getZoneType(), ZoneType.Graveyard, "Expired regeneration cannot save copied creature");
        assertEquals(effects(copy), 0L);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void regenState(boolean partial, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card first = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", owner, ZoneType.Battlefield);
        if (partial) { cast(game, "Wrap in Vigor", owner, null, 2); cast(game, "Murder", owner, first, 3); }
        else cast(game, "Regenerate", owner, first, 2);
        assertEquals(first.getShieldCount(), partial ? 0 : 1);
        assertEquals(first.getRegeneratedThisTurn(), partial ? 1 : 0);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        assertEquals(find(copy, first.getId()).getShieldCount(), first.getShieldCount(), "Copied regeneration shield count");
        assertEquals(find(copy, first.getId()).getRegeneratedThisTurn(), first.getRegeneratedThisTurn(), "Copied regeneration history");
        assertEquals(find(copy, second.getId()).getShieldCount(), second.getShieldCount());
        nextTurn(copy); assertEquals(find(copy, first.getId()).getRegeneratedThisTurn(), 0);
        assertEquals(first.getRegeneratedThisTurn(), partial ? 1 : 0);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void regenUse(boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
        cast(game, "Regenerate", owner, bear, 2);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        cast(copy, "Murder", copy.getPlayers().get(0), find(copy, bear.getId()), 3);
        Card saved = find(copy, bear.getId()); assertTrue(saved.isInPlay()); assertTrue(saved.isTapped());
        assertEquals(saved.getShieldCount(), 0, "Consumption never produces a negative shield quota");
        assertEquals(saved.getRegeneratedThisTurn(), 1); assertEquals(bear.getRegeneratedThisTurn(), 0);
        assertEquals(bear.getShieldCount(), 1); nextTurn(copy); assertEquals(effects(copy), 0L);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void dying(boolean expired, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card wall = addCardToZone("Wall of Stone", owner, ZoneType.Battlefield);
        cast(game, "Incendiary Flow", owner, wall, 2); assertEquals(wall.getDamage(), 3); assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        if (expired) { nextTurn(game); nextTurn(copy); }
        cast(game, "Murder", owner, find(game, wall.getId()), 3);
        assertEquals(find(game, wall.getId()).getZone().getZoneType(), expired ? ZoneType.Graveyard : ZoneType.Exile);
        cast(copy, "Murder", copy.getPlayers().get(0), find(copy, wall.getId()), 3);
        assertEquals(find(copy, wall.getId()).getZone().getZoneType(), expired ? ZoneType.Graveyard : ZoneType.Exile);
        nextTurn(copy); assertEquals(effects(copy), 0L);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void unearth(boolean bounce, boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card specter = addCardToZone("Sedraxis Specter", owner, ZoneType.Graveyard);
        SpellAbility ability = specter.getSpellAbilities().stream().filter(a -> a.isActivatedAbility() && a.isKeyword(Keyword.UNEARTH)).findFirst().orElseThrow();
        pay(game, ability, owner, 2); specter = find(game, specter.getId());
        assertTrue(specter.isInPlay()); assertTrue(specter.hasKeyword("Haste")); assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        if (bounce) cast(copy, "Unsummon", copy.getPlayers().get(0), find(copy, specter.getId()), 1);
        else nextTurn(copy);
        assertEquals(find(copy, specter.getId()).getZone().getZoneType(), ZoneType.Exile);
        assertEquals(effects(copy), 0L, "Unearth effect must leave command when its creature leaves");
        assertTrue(specter.isInPlay()); assertEquals(effects(game), 1L);
        if (bounce) cast(game, "Unsummon", owner, specter, 1); else nextTurn(game);
        assertEquals(effects(game), 0L);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void amass(boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        cast(game, "Lazotep Reaver", owner, null, 2); cast(game, "Dunland Crebain", owner, null, 3);
        Card army = game.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getType().hasSubtype("Army")).findFirst().orElseThrow();
        assertTrue(army.getType().hasSubtype("Zombie")); assertTrue(army.getType().hasSubtype("Orc"));
        assertEquals(army.getCounters(CounterEnumType.P1P1), 3); assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        assertTrue(find(copy, army.getId()).getType().hasSubtype("Orc"));
        cast(copy, "Unsummon", copy.getPlayers().get(0), find(copy, army.getId()), 1);
        assertEquals(effects(copy), 0L); assertTrue(army.isInPlay()); assertEquals(effects(game), 1L);
        cast(game, "Unsummon", owner, army, 1); assertEquals(effects(game), 0L);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void replay(boolean counter) {
        Game game = pod(); Player owner = prepare(game);
        Card opt = addCardToZone("Opt", owner, ZoneType.Graveyard);
        Card hulk = addCardToZone("Torrential Gearhulk", owner, ZoneType.Hand);
        SpellAbility spell = hulk.getFirstSpellAbility(); spell.setActivatingPlayer(owner);
        assertTrue(spell.canPlay()); assertTrue(ComputerUtil.playStack(spell, owner, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6); game.getStack().resolveStack();
        game.getAction().checkStateEffects(true); game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().peekAbility().getApi(), ApiType.Play); game.getStack().resolveStack();
        assertFalse(game.getStack().isEmpty()); assertEquals(game.getStack().peekAbility().getHostCard().getId(), opt.getId());
        assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(true); Game copy = copier.makeCopy();
        if (counter) {
            Card counterCard = addCardToZone("Counterspell", copy.getPlayers().get(0), ZoneType.Hand);
            SpellAbility answer = counterCard.getFirstSpellAbility(); answer.setActivatingPlayer(copy.getPlayers().get(0));
            assertTrue(answer.canTarget(copy.getStack().peekAbility()));
            answer.getTargets().add(copy.getStack().peekAbility());
            pay(copy, answer, copy.getPlayers().get(0), 2);
        }
        else { copy.getStack().resolveStack(); settle(copy); }
        assertEquals(find(copy, opt.getId()).getZone().getZoneType(), ZoneType.Exile);
        assertEquals(effects(copy), 0L); assertEquals(effects(game), 1L);
        nextTurn(copy); assertEquals(effects(copy), 0L); assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void schemeHelper(boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card scheme = Card.fromPaperCard(StaticData.instance().getVariantCards().getCard("All in Good Time"), owner);
        scheme.setGameTimestamp(game.getNextTimestamp()); owner.getZone(ZoneType.Command).add(scheme);
        SpellAbility ability = AbilityFactory.getAbility(scheme.getSVar("GoodTimes"), scheme); ability.setActivatingPlayer(owner);
        AddTurnEffect.createCantSetSchemesInMotionEffect(ability); assertEquals(effects(game), 1L);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        nextTurn(copy); assertEquals(effects(copy), 0L); assertEquals(effects(game), 1L);
        nextTurn(game); assertEquals(effects(game), 0L); assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    private void historical(boolean consumed) {
        Game game = pod(); Player owner = prepare(game);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield); cast(game, "Regenerate", owner, bear, 2);
        if (consumed) cast(game, "Murder", owner, bear, 3);
        Card lki = CardCopyService.getLKICopy(bear);
        assertEquals(lki.getShieldCount(), bear.getShieldCount(), "Original LKI regeneration shields");
        assertEquals(lki.getRegeneratedThisTurn(), bear.getRegeneratedThisTurn(), "Original LKI regeneration count");
    }
    @Test public void regenExpiryStack() { regenExpiry(true); }
    @Test public void regenExpiryNoStack() { regenExpiry(false); }
    @Test public void regenUseStack() { regenUse(true); }
    @Test public void regenUseNoStack() { regenUse(false); }
    @Test public void amassStack() { amass(true); }
    @Test public void amassNoStack() { amass(false); }
    @Test public void schemeHelperStack() { schemeHelper(true); }
    @Test public void schemeHelperNoStack() { schemeHelper(false); }
    @Test public void regenStateTrueStack() { regenState(true, true); }
    @Test public void regenStateTrueNoStack() { regenState(true, false); }
    @Test public void regenStateFalseStack() { regenState(false, true); }
    @Test public void regenStateFalseNoStack() { regenState(false, false); }
    @Test public void dyingTrueStack() { dying(true, true); }
    @Test public void dyingTrueNoStack() { dying(true, false); }
    @Test public void dyingFalseStack() { dying(false, true); }
    @Test public void dyingFalseNoStack() { dying(false, false); }
    @Test public void unearthTrueStack() { unearth(true, true); }
    @Test public void unearthTrueNoStack() { unearth(true, false); }
    @Test public void unearthFalseStack() { unearth(false, true); }
    @Test public void unearthFalseNoStack() { unearth(false, false); }
    @Test public void replayResolve() { replay(false); }
    @Test public void replayCounter() { replay(true); }
    @Test public void historicalShield() { historical(false); }
    @Test public void historicalCount() { historical(true); }
    private void starfish(boolean withStack) {
        Game game = pod(); Player owner = prepare(game);
        Card fish = cast(game, "Spiny Starfish", owner, null, 3);
        activate(game, fish, owner, null, 1, ApiType.Regenerate); cast(game, "Murder", owner, fish, 3);
        assertTrue(fish.isInPlay()); assertEquals(fish.getRegeneratedThisTurn(), 1);
        GameCopier copier = new GameCopier(game).withStack(withStack); Game copy = copier.makeCopy();
        nextTurn(game);
        assertEquals(game.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 1L, "Original Starfish trigger");
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 0L);
        nextTurn(copy);
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 1L, "Copied Starfish trigger needs regeneration history");
        assertEquals(find(copy, fish.getId()).getRegeneratedThisTurn(), 0);
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
    }
    @Test public void starfishStack() { starfish(true); }
    @Test public void starfishNoStack() { starfish(false); }
}
