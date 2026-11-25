package forge.ai.simulation;

import com.google.common.collect.*;
import forge.LobbyPlayer;
import forge.ai.AIOption;
import forge.ai.LobbyPlayerAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.CardStateName;
import forge.game.*;
import forge.game.ability.effects.DetachedCardEffect;
import forge.game.card.*;
import forge.game.card.sticker.AppliedSticker;
import forge.game.card.sticker.Sticker;
import forge.game.card.token.TokenInfo;
import forge.game.combat.Combat;
import forge.game.mana.Mana;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;

import forge.game.staticability.StaticAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.PlayerZoneBattlefield;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.item.IPaperCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameCopier {
    private static final ZoneType[] ZONES = new ZoneType[] {
        ZoneType.Battlefield,
        ZoneType.Hand,
        ZoneType.Graveyard,
        ZoneType.Library,
        ZoneType.Exile,
        ZoneType.Stack,
        ZoneType.Command,
        ZoneType.Sideboard,
        ZoneType.PlanarDeck,
        ZoneType.SchemeDeck,
        ZoneType.AttractionDeck,
        ZoneType.ContraptionDeck,
        ZoneType.Junkyard,
        ZoneType.StickerSheets,
    };

    private final Map<forge.game.combat.AttackingBand, forge.game.combat.AttackingBand> combatBands = new java.util.IdentityHashMap<>();
    private forge.game.spellability.SpellAbilitySnapshot abilitySnapshot;
    private Game origGame;
    private BiMap<Player, Player> playerMap = HashBiMap.create();
    private BiMap<Card, Card> cardMap = HashBiMap.create();
    private CopiedGameObjectMap gameObjectMap;
    private GameSnapshot snapshot = null;
    /** Copy the spells on the stack too (see {@link GameSimulator#COPY_STACK}); decided per copier. */
    private boolean copyStack = GameSimulator.COPY_STACK;
    private boolean checkStateBasedActions = true;
    private final Map<SpellAbility, SpellAbility> stackMap = new HashMap<>();

    public GameCopier(Game origGame) {
        this.origGame = origGame;
        if (origGame.EXPERIMENTAL_RESTORE_SNAPSHOT) {
            this.snapshot = new GameSnapshot(origGame);
        }
    }

    /** Makes {@link #makeCopy} copy the spells on the stack, so that a decision with a spell on the stack can be simulated. */
    public GameCopier withStack(final boolean copy) {
        this.copyStack = copy;
        return this;
    }

    /**
     * Preserve an in-progress native state for cost-only calculations. Such a copy cannot
     * advance a phase; its Java resolution continuation is still owned by the original game.
     */
    public GameCopier withStateBasedActions(final boolean check) {
        if (!check && (!origGame.getStack().isFrozen() && !origGame.getStack().isResolving())) {
            throw new IllegalStateException("Deferred state checks require an in-progress native window");
        }
        if (!check && origGame.EXPERIMENTAL_RESTORE_SNAPSHOT) {
            throw new IllegalStateException("Deferred state checks are unavailable for experimental restore snapshots");
        }
        this.checkStateBasedActions = check;
        return this;
    }

    public Game getOriginalGame() {
        return origGame;
    }

    /**
     * Cuts every link to the game this copy was made from (and the maps between the two). Only the copied game stays
     * alive. For search nodes that are kept for a long time; after this {@link #find} and {@link #reverseFind} find
     * nothing, and {@link #makeCopy} must not be called again.
     */
    public void release() {
        if (gameObjectMap != null) {
            releasedGame = gameObjectMap.getGame();
        }
        origGame = null;
        playerMap = HashBiMap.create();
        cardMap = HashBiMap.create();
        stackMap.clear();
        gameObjectMap = null;
        snapshot = null;
    }

    private Game releasedGame;

    public Game getCopiedGame() {
        return gameObjectMap != null ? gameObjectMap.getGame() : releasedGame;
    }

    public Game makeCopy() {
        return makeCopy(null, null);
    }
    public Game makeCopy(PhaseType advanceToPhase, Player aiPlayer) {
        if (!checkStateBasedActions && !origGame.getStack().isFrozen() && !origGame.getStack().isResolving()) {
            throw new IllegalStateException("The in-progress snapshot window has already ended");
        }
        if (!checkStateBasedActions && advanceToPhase != null) {
            throw new IllegalArgumentException("Cannot advance an in-progress state-only snapshot");
        }
        if (origGame.EXPERIMENTAL_RESTORE_SNAPSHOT) {
            // How do we advance to phase when using restores?
            return snapshot.makeCopy();
        }

        combatBands.clear();
        cardMap.clear();
        playerMap.clear();
        stackMap.clear();
        abilitySnapshot = null;
        List<RegisteredPlayer> origPlayers = origGame.getMatch().getPlayers();
        List<RegisteredPlayer> newPlayers = new ArrayList<>();
        for (RegisteredPlayer p : origPlayers) {
            newPlayers.add(clonePlayer(p));
        }

        GameRules currentRules = origGame.getRules();
        Match newMatch = new Match(currentRules, newPlayers, origGame.getView().getTitle());
        Game newGame = new Game(newPlayers, currentRules, newMatch);
        newGame.copyTurnOrderFrom(origGame);
        newGame.markAsSimulationCopy();
        newGame.setNoGUIUser();
        newGame.dangerouslySetTimestamp(origGame.getTimestamp());

        for (Player origPlayer : origGame.getRegisteredPlayers()) {
            Player newPlayer = newGame.getPlayer(origPlayer.getId());
            newPlayer.setTeam(origPlayer.getTeam());
            // Restore the rules baseline before current life; these values can differ.
            newPlayer.setStartingLife(origPlayer.getStartingLife());
            newPlayer.setLife(origPlayer.getLife(), null);
            newPlayer.setLifeLostLastTurn(origPlayer.getLifeLostLastTurn());
            newPlayer.setLifeLostThisTurn(origPlayer.getLifeLostThisTurn());
            newPlayer.setLifeGainedThisTurn(origPlayer.getLifeGainedThisTurn());
            newPlayer.setCommitedCrimeThisTurn(origPlayer.getCommittedCrimeThisTurn());
            newPlayer.setDilemmasFacedThisTurn(origPlayer.getDilemmasFacedThisTurn());
            newPlayer.copyDiceHistoryFrom(origPlayer);
            newPlayer.copyDrawHistoryFrom(origPlayer);
            newPlayer.copySnapshotElementalBendFrom(origPlayer);
            newPlayer.setLifeStartedThisTurnWith(origPlayer.getLifeStartedThisTurnWith());
            newPlayer.setDamageReceivedThisTurn(origPlayer.getDamageReceivedThisTurn());
            newPlayer.setLandsPlayedThisTurn(origPlayer.getLandsPlayedThisTurn());
            newPlayer.setCounters(HashMultiset.create(origPlayer.getCounters()));
            newPlayer.setSpeed(origPlayer.getSpeed());
            // Blessing state travels with the copied command-zone effect card
            // (wired via copyEffectCardsToSnapshot below); calling setBlessing
            // here would create a second effect card the zone copy duplicates.
            newPlayer.setDescended(origPlayer.getDescended());
            newPlayer.setLibrarySearched(origPlayer.getLibrarySearched());
            newPlayer.setSpellsCastLastTurn(origPlayer.getSpellsCastLastTurn());
            for (int j = 0; j < origPlayer.getSpellsCastThisTurn(); j++) {
                newPlayer.addSpellCastThisTurn();
            }
            newPlayer.setMaxHandSize(origPlayer.getMaxHandSize());
            newPlayer.setUnlimitedHandSize(origPlayer.isUnlimitedHandSize());
            newPlayer.setCrankCounter(origPlayer.getCrankCounter());
            // TODO creatureAttackedThisTurn
            for (Mana m : origPlayer.getManaPool()) {
                newPlayer.getManaPool().addManaNoEvent(m);
            }
            // Restore life before the outcome: lost players cannot gain or lose life.
            // PlayerOutcome is immutable; the statistics object remains per game.
            newPlayer.getStats().setOutcome(origPlayer.getOutcome());
            newPlayer.setHasLost(origPlayer.hasLost());
            playerMap.put(origPlayer, newPlayer);
        }

        // Removing participants here is state restoration, not a new loss event.
        newGame.copyParticipationFrom(origGame, playerMap::get);

        PhaseHandler origPhaseHandler = origGame.getPhaseHandler();
        Player newPlayerTurn = playerMap.get(origPhaseHandler.getPlayerTurn());
        newGame.getPhaseHandler().devModeSet(origPhaseHandler.getPhase(), newPlayerTurn, origPhaseHandler.getTurn());
        newGame.getTriggerHandler().suppressMode(TriggerType.ChangesZone);
        for (Player p : newGame.getPlayers()) {
            ((PlayerZoneBattlefield) p.getZone(ZoneType.Battlefield)).setTriggers(false);
        }

        copyGameState(newGame, aiPlayer);

        // Commander damage can outlive its source's owner. Share the same
        // historical graph with abilities instead of requiring every old source
        // to remain in a live zone of the copied game.
        forge.game.spellability.SpellAbilitySnapshot context = createAbilitySnapshot(newGame, gameObjectMap);
        context.bindCostStack(origGame.costPaymentStack, newGame.costPaymentStack);
        abilitySnapshot = context;
        for (Player origPlayer : origGame.getPlayers()) {
            Player newPlayer = playerMap.get(origPlayer);
            origPlayer.copyCommandersToSnapshot(newPlayer, context::map);
            origPlayer.copyEffectCardsToSnapshot(newPlayer, context::map);
            ((PlayerZoneBattlefield) newPlayer.getZone(ZoneType.Battlefield)).setTriggers(true);
        }
        newGame.getTriggerHandler().clearSuppression(TriggerType.ChangesZone);

        copyAbilityState(origGame, newGame, context);

        for (Card c : newGame.getCardsInGame()) {
            Card origCard = (Card) reverseFind(c);
            if (origCard.hasRemembered()) {
                for (Object o : origCard.getRemembered()) {
                    if (o instanceof GameObject) {
                        // Sometimes, a spell can "remember" a token card that's not in any zone
                        // (and thus wouldn't have been copied) - for example Swords to Plowshares
                        // remembering its target for LKI. Skip these to not crash in find().
                        if (o instanceof Card && ((Card)o).getZone() == null) {
                           continue;
                        }
                        // Paid ability identities were mapped before remembered objects.

                        c.addRemembered(find((GameObject) o));
                    } else {
                        System.err.println(c + " Remembered: " + o + "/" + o.getClass());
                        c.addRemembered(o);
                    }
                }
            }
            for (SpellAbility sa : c.getSpellAbilities()) {
                Player activatingPlayer = sa.getActivatingPlayer();
                if (activatingPlayer != null && activatingPlayer.getGame() != newGame) {
                    sa.setActivatingPlayer(gameObjectMap.map(activatingPlayer));
                }
            }
        }

        // Prepared effects point at an exiled spell; bind after remembered objects.
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            if (entry.getKey().isPrepared()) {
                entry.getValue().copySnapshotPreparedFrom(entry.getKey(), context);
            }
        }

        // Undo effects first before calculating them below, to avoid them applying twice.
        // The effects hold the card they came from as it was when the state was last checked, and GameAction checks
        // with the copies a zone change made ("TODO filter out old copies from zone change"): such a source is a
        // different object than the card in the zone, with the same id. Find it by id too, and skip an effect whose
        // card is no longer in the game at all, there is nothing to undo for it in the copy.
        final IEntityMap lenient = lenientMap(newGame);
        for (StaticEffect effect : origGame.getStaticEffects().getEffects()) {
            if (lenient.map(effect.getSource()) != null) {
                effect.removeMapped(lenient);
            }
        }

        if (origPhaseHandler.getCombat() != null) {
            newGame.getPhaseHandler().setCombat(new Combat(origPhaseHandler.getCombat(), gameObjectMap));
        }

        if (checkStateBasedActions) {
            newGame.getAction().checkStateEffects(true);
        } else {
            newGame.getAction().checkStaticAbilities(false);
        }
        newGame.getTriggerHandler().resetActiveTriggers();




        if (advanceToPhase != null) {
            newGame.getPhaseHandler().devAdvanceToPhase(advanceToPhase, () -> GameSimulator.resolveStack(newGame, aiPlayer.getWeakestOpponent()));
        }

        return newGame;
    }


    private forge.game.spellability.SpellAbilitySnapshot createAbilitySnapshot(Game newGame, IEntityMap map) {
        IEntityMap objects = new IEntityMap() {
            @Override public Game getGame() { return newGame; }
            @Override public forge.game.combat.AttackingBand mapCombatBand(forge.game.combat.AttackingBand band) {
                return map.mapCombatBand(band);
            }
            @Override public GameObject map(GameObject value) {
                if (value instanceof Card card) return cardMap.get(card);
                return map.map(value);
            }
        };
        return new forge.game.spellability.SpellAbilitySnapshot(objects, combatBands);
    }

    private void copyAbilityState(Game origGame, Game newGame,
            forge.game.spellability.SpellAbilitySnapshot context) {
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().bindSnapshotStatesFrom(entry.getKey(), context);
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().copySnapshotTraitsFrom(entry.getKey(), context);
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().copySnapshotCastFormReferencesFrom(entry.getKey(), context);
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().copySnapshotGrantCachesFrom(entry.getKey(), context);
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            context.copyCastState(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().copySnapshotUsageFrom(entry.getKey(), context);
        }
        abilitySnapshot = context;
        for (Map.Entry<Player, Player> entry : playerMap.entrySet()) {
            entry.getValue().copySnapshotControllersFrom(entry.getKey(), context);
            entry.getValue().copySnapshotRingFrom(entry.getKey(), context);
            entry.getValue().copySnapshotDrawnFrom(entry.getKey(), context);
            entry.getValue().copySnapshotKeywordsFrom(entry.getKey(), context);
            entry.getValue().getManaPool().copySnapshotFrom(entry.getKey().getManaPool(), context);
        }
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            // Snapshot construction inserts cards into zones; restore their actual entry turn afterward.
            entry.getValue().setTurnInZone(entry.getKey().getTurnInZone());
            entry.getValue().copySnapshotChoicesFrom(entry.getKey(), context);
            entry.getValue().copySnapshotProtectorFrom(entry.getKey(), context);
            entry.getValue().copySnapshotPhasingFrom(entry.getKey(), context);
            entry.getValue().copySnapshotExertionFrom(entry.getKey(), context);
            entry.getValue().copySnapshotImprintedFrom(entry.getKey(), context);
            entry.getValue().copySnapshotControlFrom(entry.getKey(), context);
            entry.getValue().copySnapshotBlockingFrom(entry.getKey(), context);
            entry.getValue().copySnapshotRegenerationFrom(entry.getKey());
            entry.getValue().copySnapshotDetainFrom(entry.getKey(), context);
            entry.getValue().copySnapshotCommandsFrom(entry.getKey(), context);
        }
        newGame.copySnapshotCounterHistoryFrom(origGame, context);
        newGame.getUntap().copySnapshotCommandsFrom(origGame.getUntap(), context);
        newGame.getUpkeep().copySnapshotCommandsFrom(origGame.getUpkeep(), context);
        newGame.getBeginOfCombat().copySnapshotCommandsFrom(origGame.getBeginOfCombat(), context);
        newGame.getEndOfCombat().copySnapshotCommandsFrom(origGame.getEndOfCombat(), context);
        newGame.getEndOfTurn().copySnapshotCommandsFrom(origGame.getEndOfTurn(), context);
        newGame.getCleanup().copySnapshotCommandsFrom(origGame.getCleanup(), context);
        newGame.getStack().restoreSnapshotFrom(origGame.getStack(), context, copyStack);
        newGame.costPaymentStack.copySnapshotStateFrom(origGame.costPaymentStack, context);
        newGame.getTriggerHandler().copySnapshotStateFrom(origGame.getTriggerHandler(), context);
        newGame.getPhaseHandler().copySnapshotStateFrom(origGame.getPhaseHandler(), context);
        context.copyCommands(origGame.sbaCheckedCommandList, newGame.sbaCheckedCommandList, "game.sba");
        stackMap.putAll(context.getAbilityCopies());
    }

    private RegisteredPlayer clonePlayer(RegisteredPlayer p) {
        RegisteredPlayer clone = new RegisteredPlayer(p.getDeck());
        clone.setStartingLife(p.getStartingLife());
        LobbyPlayer lp = p.getPlayer();
        if (!(lp instanceof LobbyPlayerAi)) {
            // TODO should probably also override them if they're normal AI
            lp = new LobbyPlayerAi(p.getPlayer().getName(), Sets.newHashSet(AIOption.USE_FULL_SIMULATION));
        }
        clone.setPlayer(lp);
        return clone;
    }

    private void copyGameState(Game newGame, Player aiPlayer) {
        // Copied cards keep their original ids (id order is AI-visible via
        // Card.compareTo and id-keyed collections; renumbering makes forked
        // games deterministically diverge from the mainline). Sync the fresh-id
        // counters first so ids created during or after the copy cannot collide.
        newGame.dangerouslySyncCardIdCounters(origGame);
        newGame.EXPERIMENTAL_RESTORE_SNAPSHOT = origGame.EXPERIMENTAL_RESTORE_SNAPSHOT;
        newGame.AI_TIMEOUT = origGame.AI_TIMEOUT;
        newGame.setAge(origGame.getAge());

        // Turn-counter history is restored with the complete object graph.

        if (origGame.getStartingPlayer() != null) {
            newGame.setStartingPlayer(playerMap.get(origGame.getStartingPlayer()));
        }
        if (origGame.getMonarch() != null) {
            newGame.setMonarch(playerMap.get(origGame.getMonarch()));
        }
        if (origGame.getMonarchBeginTurn() != null) {
            newGame.setMonarchBeginTurn(playerMap.get(origGame.getMonarchBeginTurn()));
        }
        if (origGame.getHasInitiative() != null) {
            newGame.setHasInitiative(playerMap.get(origGame.getHasInitiative()));
        }
        if (origGame.getDayTime() != null) {
            newGame.setDayTime(origGame.getDayTime());
        }

        for (ZoneType zone : ZONES) {
            for (Card card : origGame.getCardsIncludePhasingIn(zone)) {
                addCard(newGame, zone, card, aiPlayer);
            }
            // TODO CardsAddedThisTurn is now messed up
        }
        gameObjectMap = new CopiedGameObjectMap(newGame);

        for (Card card : origGame.getCardsIncludePhasingIn(ZoneType.Battlefield)) {
            Card otherCard = cardMap.get(card);
            otherCard.setGameTimestamp(card.getGameTimestamp());
            otherCard.setLayerTimestamp(card.getLayerTimestamp());
            otherCard.setSickness(card.isFirstTurnControlled());
            // Goad requirements belong to the copied world, including phased-out
            // permanents. Rebind every source player and preserve timestamps.
            for (final Map.Entry<Long, Player> entry : card.getGoadMap().entrySet()) {
                otherCard.addGoad(entry.getKey(), gameObjectMap.map(entry.getValue()));
            }
            otherCard.setState(card.getCurrentStateName(), false);
            if (card.isAttachedToEntity()) {
                GameEntity ge = gameObjectMap.map(card.getEntityAttachedTo());
                otherCard.setEntityAttachedTo(ge);
                ge.addAttachedCard(otherCard);
            }
            if (card.getCloneOrigin() != null) {
                otherCard.setCloneOrigin(cardMap.get(card.getCloneOrigin()));
            }
            if (card.getHaunting() != null) {
                otherCard.setHaunting(cardMap.get(card.getHaunting()));
            }
            if (card.isPaired()) {
                otherCard.setPairedWith(cardMap.get(card.getPairedWith()));
            }
            // Cipher links must use copied objects before their static abilities are recalculated.
            for (Card encoded : card.getEncodedCards()) {
                Card copiedEncoded = gameObjectMap.map(encoded);
                otherCard.addEncodedCard(copiedEncoded);
                copiedEncoded.setEncodingCard(otherCard);
            }
            if (card.getCopiedPermanent() != null) {
                // TODO would it be safe to simply reuse the prototype?
                otherCard.setCopiedPermanent(new CardCopyService(card.getCopiedPermanent()).copyCard(false));
            }
            // TODO: Verify that the above relationships are preserved bi-directionally or not.
        }

        for (Card card : origGame.getCardsIn(ZoneType.Command)) {
            Card otherCard = cardMap.get(card);
            if (card.getEffectSource() != null) {
                // this matters for correct MayPlay matching
                otherCard.setEffectSource(cardMap.get(card.getEffectSource()));
            }
        }

        copyStickerState();
        for (Player original : origGame.getRegisteredPlayers()) {
            Player copied = playerMap.get(original);
            for (ZoneType zone : ZONES) {
                if (zone != ZoneType.Stack) copied.getZone(zone).copyTurnHistoryFrom(original.getZone(zone), gameObjectMap);
            }
        }
        newGame.getStackZone().copyTurnHistoryFrom(origGame.getStackZone(), gameObjectMap);
        copyLeftThisTurn(newGame);
        copyDamageState(newGame);
    }

    private void copyStickerState() {
        // All zones and current faces must exist before any sticker's sheet is mapped.
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            for (AppliedSticker original : entry.getKey().getStickers()) {
                Card copiedSheet = cardMap.get(original.getSticker().getSheet());
                if (copiedSheet == null) {
                    throw new IllegalStateException("Sticker source sheet is absent from snapshot");
                }
                Sticker sticker = original.getSticker().copyForSheet(copiedSheet);
                entry.getValue().addSticker(new AppliedSticker(sticker, original.getTimestamp(), original.getNamePosition()));
            }
        }
    }

    private void copyDamageState(Game newGame) {
        IEntityMap damageMap = new IEntityMap() {
            @Override
            public Game getGame() { return newGame; }

            @Override
            public GameObject map(GameObject object) {
                if (object instanceof Card original) {
                    Card copied = cardMap.get(original);
                    if (!original.isLKI() && copied != null && copied.equalsWithGameTimestamp(original)) {
                        return copied;
                    }
                    return CardCopyService.getLKICopyForGame(original, gameObjectMap);
                }
                return gameObjectMap.map(object);
            }
        };
        Map<forge.game.card.CardDamageHistory, forge.game.card.CardDamageHistory> histories = new java.util.IdentityHashMap<>();
        java.util.function.Function<forge.game.card.CardDamageHistory, forge.game.card.CardDamageHistory> historyCopy =
                original -> histories.computeIfAbsent(original, history -> history.copy(damageMap));
        for (Map.Entry<Card, Card> entry : cardMap.entrySet()) {
            entry.getValue().setDamageHistory(historyCopy.apply(entry.getKey().getDamageHistory()));
            entry.getValue().copyDamageReceivedFrom(entry.getKey());
            // Block records are separate from damage history and retain last-known card states.
            Card original = entry.getKey();
            Card copied = entry.getValue();
            copied.clearBlockedThisTurn();
            for (Card blocked : original.getBlockedThisTurn()) copied.addBlockedThisTurn((Card) damageMap.map(blocked));
            copied.clearBlockedByThisTurn();
            for (Card blocker : original.getBlockedByThisTurn()) copied.addBlockedByThisTurn((Card) damageMap.map(blocker));
        }
        for (Map.Entry<Player, Player> entry : playerMap.entrySet()) {
            entry.getValue().copyDamageReceivedFrom(entry.getKey());
        }
        newGame.copyDamageHistoryFrom(origGame, damageMap, historyCopy);
    }

    /**
     * What left the battlefield and the graveyard this turn: revolt (Fatal Push, Narnam Renegade), morbid and the like
     * read it. Without it a play that depends on it is judged in a game where nothing has happened this turn.
     */
    private void copyLeftThisTurn(Game newGame) {
        for (Card historical : origGame.getLeftBattlefieldThisTurn()) {
            newGame.addLeftBattlefieldThisTurn(CardCopyService.getLKICopyForGame(historical, gameObjectMap));
        }
        for (Card historical : origGame.getLeftGraveyardThisTurn()) {
            newGame.addLeftGraveyardThisTurn(CardCopyService.getLKICopyForGame(historical, gameObjectMap));
        }
    }

    private static PaperCard hidden_info_card = new PaperCard(CardRules.fromScript(Lists.newArrayList("Name:hidden", "Types:Artifact", "Oracle:")), "", CardRarity.Common);
    private static final boolean PRUNE_HIDDEN_INFO = false;
    private static final boolean USE_FROM_PAPER_CARD = true;
    private Card createCardCopy(Game newGame, Player newOwner, Card c, Player aiPlayer) {
        if (c.isToken() && !c.isImmutable()) {
            // Transforming tokens need their printed rules as well as the two face states.
            Card result = c.isTransformable()
                    ? new CardCopyService(c, newGame).copyCard(false, newOwner)
                    : new TokenInfo(c).makeOneToken(newOwner, c.getId());
            new CardCopyService(c).copyCopiableCharacteristics(result, null, null);
            // a token copy of a transforming (or otherwise multi-faced) permanent has its other faces too, and may be
            // showing one of them: without them it cannot be put back in the state it is in
            for (final CardStateName state : c.getStates()) {
                if (!result.hasState(state)) {
                    result.addAlternateState(state, false);
                    result.getState(state).copyFrom(c.getState(state), false);
                }
            }
            result.setBackSide(c.isBackSide());
            return result;
        }
        if (USE_FROM_PAPER_CARD && !c.isImmutable() && c.getPaperCard() != null) {
            Card newCard;
            if (PRUNE_HIDDEN_INFO && aiPlayer != null && !c.getView().canBeShownTo(aiPlayer.getView())) {
                // TODO also check REVEALED_CARDS memory
                newCard = new Card(c.getId(), hidden_info_card, newGame);
                newCard.setOwner(newOwner);
            } else {
                final IPaperCard paper = c.getPaperCard();
                if (canCreateSnapshotSkeleton(c, paper)) {
                    newCard = createSnapshotSkeleton(newGame, newOwner, c, paper);
                } else {
                    newCard = CardFactory.getCard(paper, newOwner, c.getId(), newGame);
                }
            }
            newCard.setCommander(c.isCommander());
            return newCard;
        }

        // TODO: The above is very expensive and accounts for the vast majority of GameCopier execution time.
        // The issue is that it requires parsing the original card from scratch from the paper card. We should
        // improve the copier to accurately copy the card from its actual state, so that the paper card shouldn't
        // be needed. Once the below code accurately copies the card, remove the USE_FROM_PAPER_CARD code path.
        Card newCard;
        if (c instanceof DetachedCardEffect)
            newCard = new DetachedCardEffect((DetachedCardEffect) c, newGame, false);
        else
            newCard = new Card(c.getId(), c.getPaperCard(), newGame);
        newCard.setGamePieceType(c.getGamePieceType());
        newCard.setOwner(newOwner);
        newCard.setName(c.getName());
        newCard.setCommander(c.isCommander());
        newCard.setType(new CardType(c.getType()));
        for (StaticAbility stAb : c.getStaticAbilities()) {
            newCard.addStaticAbility(stAb.copy(newCard, true));
        }
        for (SpellAbility sa : c.getSpellAbilities()) {
            SpellAbility saCopy = sa.copy(newCard, true);
            if (saCopy != null) {
                newCard.addSpellAbility(saCopy);
            } else {
                System.err.println(sa.toString());
            }
        }

        return newCard;
    }

    /** Unknown runtime types keep their normal construction callbacks. */
    private boolean canCreateSnapshotSkeleton(final Card source, final IPaperCard paper) {
        if (source.getClass() != Card.class || paper == null || paper.getClass() != PaperCard.class
                || paper.getRules().getClass() != CardRules.class) return false;
        for (final CardStateName name : source.getStates()) {
            if (source.getOriginalState(name).getClass() != CardState.class) return false;
        }
        return true;
    }

    /** Traits are installed by copyAbilityState after every live object has a mapping. */
    private Card createSnapshotSkeleton(final Game game, final Player owner,
            final Card source, final IPaperCard paper) {
        final Card target = new Card(source.getId(), paper, game);
        target.setOwner(owner);
        target.setGamePieceType(source.getGamePieceType());
        for (final CardStateName name : source.getStates()) {
            if (!target.getStates().contains(name)) target.addAlternateState(name, false);
            target.getOriginalState(name).copySnapshotPropertiesFrom(source.getOriginalState(name));
        }
        return target;
    }

    private void addCard(Game newGame, ZoneType zone, Card c, Player aiPlayer) {
        final Player owner = playerMap.get(c.getOwner());
        final Card newCard = createCardCopy(newGame, owner, c, aiPlayer);
        newCard.setChosenCommanderColor(c.getChosenCommanderColor());
        // Simulation worlds retain existing noncopiable designations and Class level.
        newCard.setHarnessed(c.isHarnessed());
        newCard.copySnapshotWorldFrom(c);
        if (newCard.getClassLevel() != c.getClassLevel()) {
            newCard.setClassLevel(c.getClassLevel());
        }
        newCard.setGameTimestamp(c.getGameTimestamp());
        newCard.setLayerTimestamp(c.getLayerTimestamp());
        newCard.setTransformedTimestamp(c.getTransformedTimestamp());
        cardMap.put(c, newCard);


        Player zoneOwner = owner;
        // everything the CreatureEvaluator checks must be set here
        if (zone == ZoneType.Battlefield) {
            // TODO: Controllers' list with timestamps should be copied.
            zoneOwner = playerMap.get(c.getController());
            newCard.setController(zoneOwner, 0);


            newCard.setCameUnderControlSinceLastUpkeep(c.cameUnderControlSinceLastUpkeep());

            newCard.setPTTable(c.getSetPTTable());
            newCard.setPTCharacterDefiningTable(c.getSetPTCharacterDefiningTable());

            newCard.setPTBoost(c.getPTBoostTable());
            // TODO copy by map
            newCard.setDamage(c.getDamage());
            newCard.setDamageReceivedThisTurn(c.getDamageReceivedThisTurn());

            newCard.copyFrom(c);
            newCard.copySnapshotTextFrom(c);
            newCard.setRingBearer(c.isRingBearer());
            newCard.updateKeywordsCache();

            if (c.isTapped()) {
                newCard.setTapped(true);
            }
            if (c.isFaceDown()) {
                newCard.turnFaceDown(true);
                if (c.isManifested()) {
                    newCard.setManifested(c.getManifestedSA());
                }
                if (c.isCloaked()) {
                    newCard.setCloaked(c.getCloakedSA());
                }
            }
            if (c.isMonstrous()) {
                newCard.setMonstrous(true);
            }
            if (c.isRenowned()) {
                newCard.setRenowned(true);
            }
            if (c.isSolved()) {
                newCard.setSolved(true);
            }
            if (c.isSuspected()) {
                newCard.setSuspected(true);
            }


            newCard.setFlipped(c.isFlipped());
            for (Map.Entry<Long, CardCloneStates> e : c.getCloneStates().entrySet()) {
                newCard.addCloneState(e.getValue().copy(newCard, true), e.getKey());
            }

            newCard.setSprocket(c.getSprocket());

            newCard.setSVars(c.getSVars());
            newCard.copyChangedSVarsFrom(c);
        }

        // Counters belong to the snapshot even outside the battlefield.
        // Counter-derived statics are remapped with the other snapshot references below.
        newCard.copySnapshotCountersFrom(c);
        newCard.copySnapshotCastFormFrom(c);

        if (zone == ZoneType.Exile && c.getCurrentStateName() == CardStateName.PreparedSpell) {
            newCard.setState(CardStateName.PreparedSpell, false);
        }


        if (zone == ZoneType.Exile && c.isFaceDown()) {
            // Face-down exile state (foretell, "exile face down" effects) must
            // survive the copy: cards rebuilt from their paper card default to
            // face up, leaking hidden information into the copied game.
            newCard.turnFaceDownNoUpdate();
            if (c.isForetold()) {
                newCard.setForetold(true);
                if (c.isForetoldCostByEffect()) {
                    newCard.setForetoldCostByEffect(true);
                }
            }
        }

        if (zone == ZoneType.Stack) {
            newGame.getStackZone().add(newCard);
        } else {
            zoneOwner.getZone(zone).add(newCard);
        }
    }

    private static SpellAbility findSAInCard(SpellAbility sa, Card c) {
        String saDesc = sa.getDescription();
        for (SpellAbility cardSa : c.getAllSpellAbilities()) {
            if (saDesc.equals(cardSa.getDescription())) {
                return cardSa;
            }
        }
        return null;
    }

    private class CopiedGameObjectMap implements IEntityMap {
        private final Game copiedGame;

        public CopiedGameObjectMap(Game copiedGame) {
            this.copiedGame = copiedGame;
        }

        @Override
        public Game getGame() {
            return copiedGame;
        }

        @Override
        public GameObject map(GameObject o) {
            return find(o);
        }

        @Override
        public forge.game.combat.AttackingBand mapCombatBand(forge.game.combat.AttackingBand original) {
            return combatBands.computeIfAbsent(original, band -> {
                forge.game.combat.AttackingBand copy = new forge.game.combat.AttackingBand(mapList(new ArrayList<>(band.getAttackers())));
                if (band.isBlocked() != null) copy.setBlocked(band.isBlocked());
                return copy;
            });
        }
    }

    /**
     * Maps what a static effect refers to, as far as the copy has it: a card is looked up by identity, then by id (a
     * card that changed zones is a new object with the same id), and what is not there is left out instead of throwing.
     * Only for undoing effects; {@link #find} stays strict for everything that must exist in the copy.
     */
    private IEntityMap lenientMap(final Game newGame) {
        final Map<Integer, Card> byId = new HashMap<>();
        for (final Card c : newGame.getCardsInGame()) {
            byId.put(c.getId(), c);
        }
        return new IEntityMap() {
            @Override
            public Game getGame() {
                return newGame;
            }

            @Override
            public GameObject map(final GameObject o) {
                if (o instanceof Card) {
                    final Card found = cardMap.get(o);
                    return found != null ? found : byId.get(((Card) o).getId());
                }
                if (o instanceof Player) {
                    return playerMap.get(o);
                }
                return null;
            }

            @Override
            public CardCollection mapCollection(final CardCollectionView cards) {
                final CardCollection out = new CardCollection();
                for (final Card c : cards) {
                    final Card m = map(c);
                    if (m != null) {
                        out.add(m);
                    }
                }
                return out;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends GameObject> List<T> mapList(final List<T> objects) {
                final List<T> out = new ArrayList<>();
                for (final T o : objects) {
                    final GameObject m = map(o);
                    if (m != null) {
                        out.add((T) m);
                    }
                }
                return out;
            }
        };
    }

    /** Map an independently supplied cost through the same snapshot identity graph as its ability. */
    public forge.game.cost.Cost findCost(forge.game.cost.Cost cost) {
        if (abilitySnapshot == null) throw new IllegalStateException("Cost mapping requires a complete game copy");
        return abilitySnapshot.cost(cost);
    }

    public <T extends GameObject> T find(T o) {
        if (origGame != null && origGame.EXPERIMENTAL_RESTORE_SNAPSHOT) {
            return (T) snapshot.find(o);
        }

        T result = null;
        if (o instanceof Card) {
            result = (T) cardMap.get(o);
            if (result != null) {
                return result;
            } else {
                System.out.println("Couldn't map " + o + "/" + System.identityHashCode(o));
            }
        } else if (o instanceof Player) {
            result = (T) playerMap.get(o);
            if (result != null)
                return result;
        } else if (o instanceof SpellAbility) {
            // a spell on the stack (the target of a counterspell), only known when the stack was copied
            result = (T) (abilitySnapshot == null ? stackMap.get(o) : abilitySnapshot.copy((SpellAbility)o));
            if (result != null)
                return result;
        }
        if (o != null)
            throw new RuntimeException("Couldn't map " + o + "/" + System.identityHashCode(o));
        return result;
    }
    public GameObject reverseFind(GameObject o) {
        if (origGame != null && origGame.EXPERIMENTAL_RESTORE_SNAPSHOT) {
            return snapshot.reverseFind(o);
        }

        GameObject result = cardMap.inverse().get(o);
        if (result != null)
            return result;
        // TODO: Have only one GameObject map?
        return playerMap.inverse().get(o);
    }

    /** Callback kinds still needing explicit implementations; never treated as proof of coverage. */
    public List<String> getUnmappedSnapshotCommands() {
        return abilitySnapshot == null ? List.of() : abilitySnapshot.getUnmappedCommands();
    }
}
