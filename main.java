/*
 * TruckaJumpa — Haul-and-leap track ledger. Sequel to Frogga; truck jump-over obstacles on a
 * single track. Deterministic from config and tick; no token, no oracle. Retro jump logic.
 */

package contracts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * TruckaJumpa: retro truck jump game engine. Truck stays at track start; obstacles move toward it.
 * Jump to clear obstacles; land on one = crash. All state and logic in one file.
 */
public final class TruckaJumpa {

    // -------------------------------------------------------------------------
    // Contract identity (unique hex; not reused from Frogget, BleuTrk, or others)
    // -------------------------------------------------------------------------
    public static final String TRUCKA_CONTRACT_ID = "0xa1b2c3d4e5f6789012345678abcdef0123456789";
    public static final String TRUCKA_VERSION_HASH = "0x9876543210fedcba9876543210fedcba98765432";
    public static final String TRUCKA_DOMAIN_SEED = "0x4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3";
    public static final int TRUCKA_CHAIN_ID = 0x8b3c1e9f;
    public static final long TRUCKA_GENESIS_TS = 0x641a5678L;

    // -------------------------------------------------------------------------
    // Game constants (distinct; truck jump sequel)
    // -------------------------------------------------------------------------
    public static final int TRACK_LENGTH = 24;
    public static final int TRUCK_POSITION = 0;
    public static final int JUMP_DURATION_TICKS = 6;
    public static final int INITIAL_LIVES = 3;
    public static final int OBSTACLE_BASE_SPEED = 1;
    public static final int MAX_LEVEL = 99;
    public static final int POINTS_PER_OBSTACLE = 80;
    public static final int POINTS_LEVEL_BONUS = 60;
    public static final int OBSTACLE_SPAWN_INTERVAL = 8;
    public static final int MIN_OBSTACLE_WIDTH = 1;
    public static final int MAX_OBSTACLE_WIDTH = 3;
    public static final int HIGH_SCORE_CAP = 10;
    public static final int TRUCK_LANE = 0;

    public static final int OBSTACLE_TYPE_BARRIER = 0;
    public static final int OBSTACLE_TYPE_PIT = 1;
    public static final int PALETTE_TRUCK = 0xe67e22;
    public static final int PALETTE_BARRIER = 0xc0392b;
    public static final int PALETTE_TRACK = 0x2c3e50;
    public static final int RETRO_FPS = 14;
    public static final int DISPLAY_CELL_W = 28;
    public static final int DISPLAY_CELL_H = 28;

    // -------------------------------------------------------------------------
    // Error / event name constants (unique naming; not Frogget)
    // -------------------------------------------------------------------------
    public static final String TRUCKA_ERR_BOUNDS = "TruckaOutOfBounds";
    public static final String TRUCKA_ERR_CRASH = "TruckaCrashed";
    public static final String TRUCKA_ERR_NO_LIVES = "TruckaNoLivesLeft";
    public static final String TRUCKA_ERR_GAME_OVER = "TruckaGameOver";
    public static final String TRUCKA_ERR_ALREADY_JUMPING = "TruckaAlreadyJumping";
    public static final String TRUCKA_EVT_JUMP = "TruckaJump";
    public static final String TRUCKA_EVT_CLEARED = "TruckaObstacleCleared";
    public static final String TRUCKA_EVT_CRASH = "TruckaCrash";
    public static final String TRUCKA_EVT_LEVEL_UP = "TruckaLevelUp";
    public static final String TRUCKA_EVT_GAME_OVER = "TruckaGameOver";
    public static final String TRUCKA_EVT_TICK = "TruckaTick";

    // -------------------------------------------------------------------------
    // Immutable config (constructor-set)
    // -------------------------------------------------------------------------
    private final TruckaJumpaConfig config;
    private final Random rng;
    private TruckaJumpaState state;
    private int lastEventCode;
    private String lastEventName;

    public TruckaJumpa() {
        this.rng = new Random(Objects.hash(TRUCKA_DOMAIN_SEED, System.nanoTime()));
        this.config = new TruckaJumpaConfig(
            TRACK_LENGTH,
            TRUCK_POSITION,
            JUMP_DURATION_TICKS,
            INITIAL_LIVES,
            OBSTACLE_BASE_SPEED,
            MAX_LEVEL,
            POINTS_PER_OBSTACLE,
            POINTS_LEVEL_BONUS,
            OBSTACLE_SPAWN_INTERVAL,
            MIN_OBSTACLE_WIDTH,
            MAX_OBSTACLE_WIDTH
        );
        this.state = TruckaJumpaState.initial(config);
        this.lastEventCode = 0;
        this.lastEventName = "";
    }

    public TruckaJumpa(final long seed) {
        this.rng = new Random(seed);
        this.config = new TruckaJumpaConfig(
            TRACK_LENGTH,
            TRUCK_POSITION,
            JUMP_DURATION_TICKS,
            INITIAL_LIVES,
            OBSTACLE_BASE_SPEED,
            MAX_LEVEL,
            POINTS_PER_OBSTACLE,
            POINTS_LEVEL_BONUS,
            OBSTACLE_SPAWN_INTERVAL,
            MIN_OBSTACLE_WIDTH,
            MAX_OBSTACLE_WIDTH
        );
        this.state = TruckaJumpaState.initial(config);
        this.lastEventCode = 0;
        this.lastEventName = "";
    }

    public TruckaJumpa(final TruckaJumpaConfig config, final long seed) {
        this.rng = new Random(seed);
        this.config = config;
        this.state = TruckaJumpaState.initial(config);
        this.lastEventCode = 0;
        this.lastEventName = "";
    }

    public TruckaJumpaConfig getConfig() { return config; }
    public TruckaJumpaState getState() { return state; }
    public int getLastEventCode() { return lastEventCode; }
    public String getLastEventName() { return lastEventName; }

    public void startNewGame() {
        state = TruckaJumpaState.initial(config);
        lastEventCode = 0;
        lastEventName = "";
        resetSessionStats();
    }

    /** Call when player presses jump. Starts jump if not already in air. */
    public void jump() {
        if (state.isGameOver()) {
            lastEventName = TRUCKA_ERR_GAME_OVER;
            lastEventCode = -1;
            return;
        }
        if (state.getJumpTicksLeft() > 0) {
            lastEventName = TRUCKA_ERR_ALREADY_JUMPING;
            lastEventCode = 1;
            return;
        }
        state.setJumpTicksLeft(config.getJumpDurationTicks());
        lastEventName = TRUCKA_EVT_JUMP;
        lastEventCode = 2;
    }

    public void tick() {
        if (state.isGameOver()) return;
        state.setTickCounter(state.getTickCounter() + 1);
        if (state.getJumpTicksLeft() > 0) {
            state.setJumpTicksLeft(state.getJumpTicksLeft() - 1);
            lastEventName = TRUCKA_EVT_TICK;
            lastEventCode = 3;
        }
        moveObstacles();
        checkClearedObstacles();
        if (state.getJumpTicksLeft() == 0 && checkCollision()) {
            state.setLives(state.getLives() - 1);
            lastEventName = TRUCKA_EVT_CRASH;
            lastEventCode = 4;
            if (state.getLives() <= 0) {
                state.setGameOver(true);
                lastEventName = TRUCKA_EVT_GAME_OVER;
                lastEventCode = 5;
            }
        }
        if (state.getTickCounter() % config.getObstacleSpawnInterval() == 0 && state.getTickCounter() > 0) {
            spawnObstacle();
        }
    }

    private void moveObstacles() {
        int speed = config.getObstacleBaseSpeed() + (state.getLevel() / 2);
        for (TruckaObstacle ob : state.getObstacles()) {
            ob.setPosition(ob.getPosition() - speed);
        }
        state.getObstacles().removeIf(ob -> ob.getPosition() + ob.getWidth() < config.getTruckPosition());
    }

    private void checkClearedObstacles() {
        int truckPos = config.getTruckPosition();
        for (TruckaObstacle ob : new ArrayList<>(state.getObstacles())) {
            if (ob.getPosition() + ob.getWidth() < truckPos) {
                state.addScore(config.getPointsPerObstacle() + state.getLevel() * 5);
                state.getObstacles().remove(ob);
                lastEventName = TRUCKA_EVT_CLEARED;
                lastEventCode = 6;
            }
        }
