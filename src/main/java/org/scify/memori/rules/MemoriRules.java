

/**
 * Copyright 2016 SciFY.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scify.memori.rules;

import javafx.scene.input.KeyEvent;
import org.scify.memori.*;
import org.scify.memori.card.CategorizedCard;
import org.scify.memori.helper.TimeWatch;
import org.scify.memori.interfaces.*;

import java.awt.geom.Point2D;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Integral part of the Game Engine. This class is used to evaluate every User event of the game and
 * to also prepare the {@link GameState} object to be rendered at every cycle
 */
public class MemoriRules implements Rules {
    /**
     * a {@link HighScoresHandlerImpl} instance to handle the high score as soon as the game has finished
     */
    private HighScoresHandlerImpl highScore;
    /**
     * When the user makes the first move, start the watch
     * This variable is used to identify if the watch has been already started or not
     */
    private boolean watchStarted = false;
    /**
     * a {@link TimeWatch} instance to track the elapsed time of the game
     */
    TimeWatch watch;

    public MemoriRules() {
        highScore = new HighScoresHandlerImpl();
    }

    @Override
    public GameState getInitialState() {
        return new MemoriGameState();
    }

    @Override
    public GameState getNextState(GameState gsCurrent, UserAction uaAction) {


        MemoriGameState gsCurrentState = (MemoriGameState)gsCurrent;

        //if there is a blocking game event currently being handled by the Rendering engine, return.
        if(eventQueueContainsBlockingEvent(gsCurrentState)) {
            return gsCurrentState;
        }

        handleLevelStartingGameEvents(gsCurrentState);

        //if a user action (eg Keyboard event was provided), handle the emitting Game events
        if(uaAction != null) {
            handleUserActionGameEvents(uaAction, gsCurrentState);
            //After the first user action, start the stopwatch
            if(!watchStarted) {
                watch = TimeWatch.start();
                watchStarted = true;
            }
        }

        //if last round, create appropriate READY_TO_FINISH event
        if(isLastRound(gsCurrent)) {
            //if ready to finish event already in events queue
            handleLevelFinishGameEvents(uaAction, gsCurrentState);
        }

        return gsCurrentState;
    }

    /**
     * When a level starts the rules should add the relevant game events
     * @param gsCurrentState the current game state
     */
    private void handleLevelStartingGameEvents(MemoriGameState gsCurrentState) {
        if(MainOptions.TUTORIAL_MODE) {
            if(!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_INTRO")) {
                gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_INTRO"));
                gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_INTRO_UI", null, 0, true));
            }
        } else {
            if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "STORYLINE_AUDIO")) {
                gsCurrentState.getEventQueue().add(new GameEvent("STORYLINE_AUDIO"));
                gsCurrentState.getEventQueue().add(new GameEvent("STORYLINE_AUDIO_UI", null, 0, true));
                if(!(MainOptions.gameLevel == 4)) {
                    if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "FUN_FACTOR")) {
                        gsCurrentState.getEventQueue().add(new GameEvent("FUN_FACTOR"));
                        if(MainOptions.storyLineLevel % 2 == 1) {
                            gsCurrentState.getEventQueue().add(new GameEvent("FUN_FACTOR_UI", null, 0, true));
                        }
                    }
                }
                if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "LEVEL_INTRO_AUDIO")) {
                    gsCurrentState.getEventQueue().add(new GameEvent("LEVEL_INTRO_AUDIO"));
                    gsCurrentState.getEventQueue().add(new GameEvent("LEVEL_INTRO_AUDIO_UI", null, 1000, true));
                }
            }

            if(MainOptions.gameLevel == 4) {
                if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "HELP_INSTRUCTIONS")) {
                    gsCurrentState.getEventQueue().add(new GameEvent("HELP_INSTRUCTIONS"));
                    gsCurrentState.getEventQueue().add(new GameEvent("HELP_INSTRUCTIONS_UI", null, 0, false));
                }
            }
        }
    }

    /**
     * If the events queue contains a blocking event it means that this event has not been handled by the rendering engine
     * @param gsCurrentState the current state
     * @return true if the events queue contains a blocking event
     */
    private boolean eventQueueContainsBlockingEvent(MemoriGameState gsCurrentState) {
        //Iterate through game events. If there is a blocking event, return.
        ListIterator<GameEvent> listIterator = gsCurrentState.getEventQueue().listIterator();
        while (listIterator.hasNext()) {
            GameEvent currentGameEvent = listIterator.next();
            if(currentGameEvent.blocking) {
                System.err.println("in blocking game event");
                return true;
            }
        }
        return false;
    }

    /**
     * If the level is over the rules should add the relevant game events
     * @param uaAction the user action (flip, move, help)
     * @param gsCurrentState the current game state
     */
    private void handleLevelFinishGameEvents(UserAction uaAction, MemoriGameState gsCurrentState) {
        if(eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "READY_TO_FINISH")) {
            if(uaAction != null) {
                //listen for user action indicating game over
                if(uaAction.getActionType().equals("enter")) {
                    //the game should finish and load a next level
                    gsCurrentState.replayLevel = true;
                    gsCurrentState.setGameFinished(true);
                }
                if(uaAction.getActionType().equals("flip")) {
                    //the game should finish and load a next level
                    gsCurrentState.setLoadNextLevel(true);
                    gsCurrentState.setGameFinished(true);
                }
            }
        } else {
            if(!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "READY_TO_FINISH")) {
                //add appropriate event
                gsCurrentState.getEventQueue().add(new GameEvent("READY_TO_FINISH"));
                //add UI events
                gsCurrentState.getEventQueue().add(new GameEvent("LEVEL_SUCCESS_STEP_1", null, new Date().getTime() + 5000, true));
                addTimeGameEvent(watch, gsCurrentState);

                gsCurrentState.getEventQueue().add(new GameEvent("LEVEL_SUCCESS_STEP_2", null, new Date().getTime() + 7200, true));

                if(MainOptions.gameLevel < 7) {
                    if (MainOptions.TUTORIAL_MODE) {
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_END_GAME_UI", null, new Date().getTime() + 6500, false));
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_END_GAME"));
                    } else {
                        gsCurrentState.getEventQueue().add(new GameEvent("LEVEL_END_UNIVERSAL", null, new Date().getTime() + 8600, false));
                    }
                } else {
                    gsCurrentState.getEventQueue().add(new GameEvent("GAME_END", null, new Date().getTime() + 8600, true));
                    gsCurrentState.getEventQueue().add(new GameEvent("PRESS_EXIT", null, new Date().getTime() + 8600, true));
                }
                //update high score
                highScore.updateHighScore(watch);
            }
        }
    }

    /**
     * Handles the user actions
     * @param uaAction the user action (flip, move, help)
     * @param gsCurrentState the current game state
     */
    private void handleUserActionGameEvents(UserAction uaAction, MemoriGameState gsCurrentState) {
        if(movementValid(uaAction.getKeyEvent(), gsCurrentState)) {
            gsCurrentState.updateRowIndex(uaAction.getKeyEvent());
            gsCurrentState.updateColumnIndex(uaAction.getKeyEvent());
            uaAction.setCoords(new Point2D.Double(gsCurrentState.getRowIndex(), gsCurrentState.getColumnIndex()));

            MemoriTerrain memoriTerrain = (MemoriTerrain) (gsCurrentState.getTerrain());
            // currTile is the tile that was moved on or acted upon
            Tile currTile = memoriTerrain.getTile(gsCurrentState.getRowIndex(), gsCurrentState.getColumnIndex());
            System.out.println(currTile.getTileType());
            // Rules 1-4: Valid movement
            // type: movement, params: coords
            // delayed: false (zero), blocking:yes
            if(uaAction.getActionType().equals("movement")) {
                gsCurrentState.getEventQueue().add(new GameEvent("movement", uaAction.getCoords()));
            } else if (uaAction.getActionType().equals("flip")) {
                if(MainOptions.TUTORIAL_MODE) {
                    if (eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_0"))
                        performFlip(currTile, gsCurrentState, uaAction, memoriTerrain);
                }
                else
                    performFlip(currTile, gsCurrentState, uaAction, memoriTerrain);
            } else if(uaAction.getActionType().equals("enter")) {
                if(!MainOptions.TUTORIAL_MODE /*&& eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "HELP_INSTRUCTIONS")*/)
                    createHelpGameEvent(uaAction, gsCurrentState);
            } else if(uaAction.getActionType().equals("escape")) {
                //exit current game
                gsCurrentState.setGameFinished(true);
            }
        } else {
            // if invalid movement, return only an invalid game event
            gsCurrentState.getEventQueue().add(new GameEvent("invalidMovement", new Point2D.Double(gsCurrentState.getRowIndex(), gsCurrentState.getColumnIndex()), 0 , false));
            if(MainOptions.TUTORIAL_MODE) {
                if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_INVALID_MOVEMENT")) {
                    //the invalid movement tutorial event should be emitted only if the tutorial has reached a certain point (step 2 which is go right second time)
                    if(eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_1_STEP_2")) {
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_INVALID_MOVEMENT_UI", new Point2D.Double(gsCurrentState.getRowIndex(), gsCurrentState.getColumnIndex()), new Date().getTime() + 500, true));
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_INVALID_MOVEMENT"));
                    }
                }
            }

        }
    }

    /**
     * Creates the help ui events
     * @param uaAction the user action
     * @param gsCurrentState the current game state
     */
    private void createHelpGameEvent(UserAction uaAction, MemoriGameState gsCurrentState) {
        Point2D coords = uaAction.getCoords();
        System.out.println("x: " + coords.getX() + " y: " + coords.getY());
        gsCurrentState.getEventQueue().add(new GameEvent("LETTER", (int)coords.getY() + 1, 0, true));
        gsCurrentState.getEventQueue().add(new GameEvent("NUMERIC", MainOptions.NUMBER_OF_ROWS - ((int)coords.getX()), 0, true));
        //If in help instructions mode, add appropriate explanation
        if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "HELP_INSTRUCTIONS_EXPLANATION")) {
            gsCurrentState.getEventQueue().add(new GameEvent("HELP_INSTRUCTIONS_EXPLANATION"));
            gsCurrentState.getEventQueue().add(new GameEvent("HELP_EXPLANATION_ROW", null, 0, true));
            gsCurrentState.getEventQueue().add(new GameEvent("NUMERIC", MainOptions.NUMBER_OF_ROWS - ((int)coords.getX()), 0, true));
            gsCurrentState.getEventQueue().add(new GameEvent("HELP_EXPLANATION_COLUMN", null, 0, true));
            gsCurrentState.getEventQueue().add(new GameEvent("LETTER", (int)coords.getY() + 1, 0, true));

        }
    }

    /**
     * Prepares the UI events that will play the sound clips for the high score
     * @param watch The watch object that contains the timer
     * @param gsCurrentState the current game state
     */
    private void addTimeGameEvent(TimeWatch watch, MemoriGameState gsCurrentState) {
        long passedTimeInSeconds = watch.time(TimeUnit.SECONDS);
        String timestampStr = String.valueOf(ConvertSecondToHHMMSSString((int) passedTimeInSeconds));
        String[] tokens = timestampStr.split(":");
        int minutes = Integer.parseInt(tokens[1]);
        int seconds = Integer.parseInt(tokens[2]);
        System.err.println("minutes: " + minutes);
        System.err.println("seconds: " + seconds);
        if(minutes != 0) {
            gsCurrentState.getEventQueue().add(new GameEvent("NUMERIC", minutes, new Date().getTime() + 5200, true));
            if(minutes > 1)
                gsCurrentState.getEventQueue().add(new GameEvent("MINUTES", minutes, new Date().getTime() + 5300, true));
            else
                gsCurrentState.getEventQueue().add(new GameEvent("MINUTE", minutes, new Date().getTime() + 5500, true));
        }
        if(minutes != 0 && seconds != 0)
            gsCurrentState.getEventQueue().add(new GameEvent("AND", minutes, new Date().getTime() + 5700, true));
        if(seconds != 0) {
            gsCurrentState.getEventQueue().add(new GameEvent("NUMERIC", seconds, new Date().getTime() + 6000, true));
            if(seconds > 1)
                gsCurrentState.getEventQueue().add(new GameEvent("SECONDS", minutes, new Date().getTime() + 5300, true));
            else
                gsCurrentState.getEventQueue().add(new GameEvent("SECOND", minutes, new Date().getTime() + 5500, true));
        }

    }

    /**
     * Applies the rules and creates the game events relevant to flipping a card
     * @param currTile the tile that the flip performed on
     * @param gsCurrentState the current game state
     * @param uaAction the user action object
     * @param memoriTerrain the terrain holding all the tiles
     */
    private void performFlip(Tile currTile, MemoriGameState gsCurrentState, UserAction uaAction, MemoriTerrain memoriTerrain) {
        // Rule 6: flip
        // If target card flipped
        if(isTileFlipped(currTile)) {
            //if card won
            if(isTileWon(currTile)) {
                //play empty sound
                gsCurrentState.getEventQueue().add(new GameEvent("EMPTY"));
            } else {
                //else if card not won
                // play card sound
                gsCurrentState.getEventQueue().add(new GameEvent("cardSound", uaAction.getCoords(), 0, true));
            }
        } else {
            System.out.println("tile not flipped");
            // else if not flipped
            // flip card
            flipTile(currTile);
            // push flip feedback event (delayed: false, blocking: no)
            gsCurrentState.getEventQueue().add(new GameEvent("TURN_ANIMATION", uaAction.getCoords()));
            gsCurrentState.getEventQueue().add(new GameEvent("flip", uaAction.getCoords(), new Date().getTime() + 1000, false));
            gsCurrentState.getEventQueue().add(new GameEvent("flip_second", uaAction.getCoords(), new Date().getTime() + 3000, false));
            //TODO (2): here we emmit the door open sound for the rendering engine
            gsCurrentState.getEventQueue().add(new GameEvent("DOOR_OPEN", uaAction.getCoords(), 0, true));
            gsCurrentState.getEventQueue().add(new GameEvent("cardSound", uaAction.getCoords(), new Date().getTime() + 2200, false));
            if(MainOptions.TUTORIAL_MODE){
                if(!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "FLIP_EXPLANATION")) {
                    //add FLIP_EXPLANATION event to queue
                    gsCurrentState.getEventQueue().add(new GameEvent("FLIP_EXPLANATION"));
                    // add FLIP_EXPLANATION_UI event to queue
                    gsCurrentState.getEventQueue().add(new GameEvent("FLIP_EXPLANATION_UI", null, new Date().getTime() + 4000, true));

                }
            }
            if(tileIsLastOfTuple(memoriTerrain, currTile)) {
                // If last of n-tuple flipped (i.e. if we have enough cards flipped to form a tuple)
                gsCurrentState.getEventQueue().add(new GameEvent("success", uaAction.getCoords(), new Date().getTime() + 4000, true));
                //gsCurrentState.getEventQueue().add(new GameEvent("CARD_DESCRIPTION", uaAction.getCoords(), new Date().getTime() + 4500, true));

                gsCurrentState.getEventQueue().add(new GameEvent("CARD_DESCRIPTION", cardDescriptionSoundFromOpenCards((MemoriTerrain) gsCurrentState.getTerrain(), currTile), new Date().getTime() + 6500, true));
                //if in tutorial mode, push explaining events
                if(MainOptions.TUTORIAL_MODE) {
                    if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_CORRECT_PAIR")) {
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_CORRECT_PAIR"));
                        gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_CORRECT_PAIR_UI", null, new Date().getTime() + 5000, true));
                    }
                }
                // add tile to open tiles
                memoriTerrain.addTileToOpenTiles(currTile);
                // set all open cards won
                setAllOpenTilesWon(memoriTerrain);
                //reset open tiles
                memoriTerrain.resetOpenTiles();
            } else {
                // else not last card in tuple
                if(atLeastOneOtherTileIsDifferent(memoriTerrain, currTile)) {
                    // Flip card back
                    // flipTile(currTile);
                    memoriTerrain.addTileToOpenTiles(currTile);
                    // Reset all tiles
                    List<Point2D> openTilesPoints = resetAllOpenTiles(memoriTerrain);
                    memoriTerrain.resetOpenTiles();
                    for (Iterator<Point2D> iter = openTilesPoints.iterator(); iter.hasNext(); ) {
                        Point2D position = iter.next();
                        gsCurrentState.getEventQueue().add(new GameEvent("flipBack", position, new Date().getTime() + 4500, false));
                    }
                    gsCurrentState.getEventQueue().add(new GameEvent("STOP_AUDIOS", null, new Date().getTime() + 4500, true));

//                    gsCurrentState.getEventQueue().add(new GameEvent("DOORS_CLOSED", null, new Date().getTime() + 4000, false));
//                    gsCurrentState.getEventQueue().add(new GameEvent("DOORS_CLOSED", null, new Date().getTime() + 4300, false));
                    if(MainOptions.TUTORIAL_MODE) {
                        if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_WRONG_PAIR")) {
                            gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_WRONG_PAIR"));
                            gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_WRONG_PAIR_UI", null, new Date().getTime() + 4600, true));
                        }
                    }
                    gsCurrentState.getEventQueue().add(new GameEvent("DOORS_SHUTTING", null, new Date().getTime() + 5000, true));
                    if(MainOptions.TUTORIAL_MODE) {
                        if (!eventsQueueContainsEvent(gsCurrentState.getEventQueue(), "TUTORIAL_DOORS_CLOSED")) {
                            gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_DOORS_CLOSED"));
                            gsCurrentState.getEventQueue().add(new GameEvent("TUTORIAL_DOORS_CLOSED_UI", null, new Date().getTime() + 7000, true));
                        }
                    }
                } else {
                    memoriTerrain.addTileToOpenTiles(currTile);
                }
            }
        }
    }

    protected String cardDescriptionSoundFromOpenCards(MemoriTerrain memoriTerrain, Tile currTile) {
        CategorizedCard tileToCard = (CategorizedCard)currTile;
        if(tileToCard.getCategory().equals("item")) {
            return tileToCard.getDescriptionSound();
        }
        for (Iterator<Tile> iter = memoriTerrain.getOpenTiles().iterator(); iter.hasNext(); ) {
            Tile element = iter.next();
            tileToCard = (CategorizedCard)element;
            // if the current card is not equal with the given card
            if(tileToCard.getCategory().equals("item")) {
                return tileToCard.getDescriptionSound();
            }
        }
        return null;
    }

    /**
     * Checks if a given event exists in the events list
     * @param eventQueue the events list
     * @param eventType the type of the event
     * @return true if the event exists in the events list
     */
    protected boolean eventsQueueContainsEvent(Queue<GameEvent> eventQueue, String eventType) {
        Iterator<GameEvent> iter = eventQueue.iterator();
        GameEvent currentGameEvent;
        while (iter.hasNext()) {
            currentGameEvent = iter.next();
            if(currentGameEvent.type.equals(eventType))
                return true;
        }
        return false;
    }

    /**
     * Checks if the current round is the last one (if all the tiles are won)
     * @param gsCurrent the current game state
     * @return true if the current round is the last one
     */
    private boolean isLastRound(GameState gsCurrent) {
        return ((MemoriGameState)gsCurrent).areAllTilesWon();
    }

    /**
     * Sets the tuple as won
     * @param memoriTerrain the terrain containing the open tiles tuple
     */
    private void setAllOpenTilesWon(MemoriTerrain memoriTerrain) {
        for (Tile openTile : memoriTerrain.getOpenTiles()) {
            openTile.setWon();
        }
    }

    /**
     * Checks if at least one of the currently open (but not won) tiles is different that the current tile
     * @param memoriTerrain the terrain holding all the tiles
     * @param currTile the current tile
     * @return true if one of the open tiles is different from the current tile
     */
    protected boolean atLeastOneOtherTileIsDifferent(MemoriTerrain memoriTerrain, Tile currTile) {
        CategorizedCard tileToCard = (CategorizedCard)currTile;
        boolean answer = false;
        for (Iterator<Tile> iter = memoriTerrain.getOpenTiles().iterator(); iter.hasNext(); ) {
            Tile element = iter.next();
            CategorizedCard elementToCard = (CategorizedCard)element;
            // if the current card is not equal with the given card
            if(!cardsAreEqual(elementToCard, tileToCard))
                answer = true;
        }
        return answer;
    }

    /**
     * Checks if 2 given {@link CategorizedCard}s are equal (must be of different categories but be in the same equivalence card set)
     * @param card1 the first {@link CategorizedCard}
     * @param card2 the second {@link CategorizedCard}
     * @return true if the 2 given cards are equal
     */
    protected boolean cardsAreEqual(CategorizedCard card1, CategorizedCard card2) {
        System.out.println("category1: " + card1.getCategory());
        System.out.println("category2: " + card2.getCategory());
        System.out.println("hash1: " + card1.getEquivalenceCardSetHashCode());
        System.out.println("hash2: " + card2.getEquivalenceCardSetHashCode());
        return !(card1.getCategory().equals(card2.getCategory())) && card1.getEquivalenceCardSetHashCode().equals(card2.getEquivalenceCardSetHashCode());
    }

    /**
     * Checks if the current tile is the last of the n-tuple
     * @param memoriTerrain the terrain holding all the tiles
     * @param currTile the current tile
     * @return true if the current tile is the last of the n-tuple
     */
    protected boolean tileIsLastOfTuple(MemoriTerrain memoriTerrain, Tile currTile) {
        boolean answer = false;
        // if all cards in the open cards tuple are equal and we have reached the end of the tuple (2-cards, 3-cards etc)
        if(!atLeastOneOtherTileIsDifferent(memoriTerrain, currTile) && (memoriTerrain.getOpenTiles().size() == MainOptions.NUMBER_OF_OPEN_CARDS - 1))
            answer = true;
        return answer;
    }

    private void flipTile(Tile currTile) {
        currTile.flip();
    }

    @Override
    public boolean isGameFinished(GameState gsCurrent) {
        MemoriGameState memoriGameState = (MemoriGameState)gsCurrent;
        return memoriGameState.isGameFinished();
    }

    protected boolean isTileFlipped(Tile tile) {
        return tile.getFlipped();
    }

    protected boolean isTileWon(Tile tile) {
        return tile.getWon();
    }

    /**
     * When the user opens a tile that does not belong to the currently n-tuple of tiles
     * the round is failed and all the open tiles should be flipped back
     * @param memoriTerrain the terrain holding all the tiles
     * @return a list containing the coordinates of the open tiles
     */
    public List<Point2D> resetAllOpenTiles(MemoriTerrain memoriTerrain) {
        List<Point2D> openTilesPoints = new ArrayList<>();
        memoriTerrain.getOpenTiles().forEach(Tile::flip);
        for (Iterator<Tile> iter = memoriTerrain.getOpenTiles().iterator(); iter.hasNext(); ) {
            Tile element = iter.next();
            Iterator it = memoriTerrain.getTiles().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                if(element == pair.getValue()) {
                    Point2D pos = (Point2D)pair.getKey();
                    openTilesPoints.add(new Point2D.Double(pos.getY(), pos.getX()));
                }
            }
        }

        return openTilesPoints;
    }

    /**
     * Determines whether the user move was valid
     * @param evt the kay event (action by the user)
     * @param memoriGameState the current game state
     * @return true if the user move was valid
     */
    public boolean movementValid(KeyEvent evt, MemoriGameState memoriGameState) {
        switch(evt.getCode()) {
            case LEFT:
                if(memoriGameState.getColumnIndex() == 0) {
                    return false;
                }
                break;
            case RIGHT:
                if(memoriGameState.getColumnIndex() == MainOptions.NUMBER_OF_COLUMNS - 1) {
                    return false;
                }
                break;
            case UP:
                if(memoriGameState.getRowIndex() == 0) {
                    return false;
                }
                break;
            case DOWN:
                if(memoriGameState.getRowIndex() == MainOptions.NUMBER_OF_ROWS - 1) {
                    return false;
                }
                break;
        }
        return true;
    }

    private String ConvertSecondToHHMMSSString(int nSecondTime) {
        return LocalTime.MIN.plusSeconds(nSecondTime).toString();
    }

}
