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
    }

    private boolean checkCollision() {
        int truckPos = config.getTruckPosition();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() <= truckPos && ob.getPosition() + ob.getWidth() > truckPos) {
                return true;
            }
        }
        return false;
    }

    private void spawnObstacle() {
        int width = config.getMinObstacleWidth() + rng.nextInt(config.getMaxObstacleWidth() - config.getMinObstacleWidth() + 1);
        int pos = config.getTrackLength() - 1;
        TruckaObstacle ob = new TruckaObstacle(pos, width, OBSTACLE_TYPE_BARRIER);
        state.getObstacles().add(ob);
    }

    public void advanceLevelIfComplete() {
        if (state.isLevelComplete() && !state.isGameOver()) {
            state.setLevel(state.getLevel() + 1);
            state.setLevelComplete(false);
            state.addScore(config.getPointsLevelBonus());
            state.getObstacles().clear();
            lastEventName = TRUCKA_EVT_LEVEL_UP;
            lastEventCode = 7;
        }
    }

    public void setLevelComplete(final boolean complete) {
        state.setLevelComplete(complete);
    }

    public boolean canJump() {
        return !state.isGameOver() && state.getJumpTicksLeft() == 0;
    }

    public boolean isJumping() {
        return state.getJumpTicksLeft() > 0;
    }

    // -------------------------------------------------------------------------
    // Inner: TruckaJumpaConfig (immutable)
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaConfig {
        private final int trackLength;
        private final int truckPosition;
        private final int jumpDurationTicks;
        private final int lives;
        private final int obstacleBaseSpeed;
        private final int maxLevel;
        private final int pointsPerObstacle;
        private final int pointsLevelBonus;
        private final int obstacleSpawnInterval;
        private final int minObstacleWidth;
        private final int maxObstacleWidth;

        public TruckaJumpaConfig(
            final int trackLength,
            final int truckPosition,
            final int jumpDurationTicks,
            final int lives,
            final int obstacleBaseSpeed,
            final int maxLevel,
            final int pointsPerObstacle,
            final int pointsLevelBonus,
            final int obstacleSpawnInterval,
            final int minObstacleWidth,
            final int maxObstacleWidth
        ) {
            this.trackLength = trackLength;
            this.truckPosition = truckPosition;
            this.jumpDurationTicks = jumpDurationTicks;
            this.lives = lives;
            this.obstacleBaseSpeed = obstacleBaseSpeed;
            this.maxLevel = maxLevel;
            this.pointsPerObstacle = pointsPerObstacle;
            this.pointsLevelBonus = pointsLevelBonus;
            this.obstacleSpawnInterval = obstacleSpawnInterval;
            this.minObstacleWidth = minObstacleWidth;
            this.maxObstacleWidth = maxObstacleWidth;
        }

        public int getTrackLength() { return trackLength; }
        public int getTruckPosition() { return truckPosition; }
        public int getJumpDurationTicks() { return jumpDurationTicks; }
        public int getLives() { return lives; }
        public int getObstacleBaseSpeed() { return obstacleBaseSpeed; }
        public int getMaxLevel() { return maxLevel; }
        public int getPointsPerObstacle() { return pointsPerObstacle; }
        public int getPointsLevelBonus() { return pointsLevelBonus; }
        public int getObstacleSpawnInterval() { return obstacleSpawnInterval; }
        public int getMinObstacleWidth() { return minObstacleWidth; }
        public int getMaxObstacleWidth() { return maxObstacleWidth; }
    }

    // -------------------------------------------------------------------------
    // Inner: TruckaJumpaState
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaState {
        private int lives;
        private int score;
        private int level;
        private int tickCounter;
        private int jumpTicksLeft;
        private boolean gameOver;
        private boolean levelComplete;
        private List<TruckaObstacle> obstacles;

        public static TruckaJumpaState initial(final TruckaJumpaConfig config) {
            TruckaJumpaState s = new TruckaJumpaState();
            s.lives = config.getLives();
            s.score = 0;
            s.level = 1;
            s.tickCounter = 0;
            s.jumpTicksLeft = 0;
            s.gameOver = false;
            s.levelComplete = false;
            s.obstacles = new ArrayList<>();
            return s;
        }

        public int getLives() { return lives; }
        public void setLives(final int lives) { this.lives = lives; }
        public int getScore() { return score; }
        public void addScore(final int n) { this.score += n; }
        public void setScore(final int score) { this.score = score; }
        public int getLevel() { return level; }
        public void setLevel(final int level) { this.level = level; }
        public int getTickCounter() { return tickCounter; }
        public void setTickCounter(final int tickCounter) { this.tickCounter = tickCounter; }
        public int getJumpTicksLeft() { return jumpTicksLeft; }
        public void setJumpTicksLeft(final int jumpTicksLeft) { this.jumpTicksLeft = jumpTicksLeft; }
        public boolean isGameOver() { return gameOver; }
        public void setGameOver(final boolean gameOver) { this.gameOver = gameOver; }
        public boolean isLevelComplete() { return levelComplete; }
        public void setLevelComplete(final boolean levelComplete) { this.levelComplete = levelComplete; }
        public List<TruckaObstacle> getObstacles() { return obstacles; }
        public void setObstacles(final List<TruckaObstacle> obstacles) { this.obstacles = obstacles; }

        public static TruckaJumpaState copyFrom(final TruckaJumpaState other) {
            TruckaJumpaState s = new TruckaJumpaState();
            s.lives = other.lives;
            s.score = other.score;
            s.level = other.level;
            s.tickCounter = other.tickCounter;
            s.jumpTicksLeft = other.jumpTicksLeft;
            s.gameOver = other.gameOver;
            s.levelComplete = other.levelComplete;
            s.obstacles = new ArrayList<>();
            for (TruckaObstacle ob : other.obstacles) {
                s.obstacles.add(new TruckaObstacle(ob.getPosition(), ob.getWidth(), ob.getType()));
            }
            return s;
        }
    }

    // -------------------------------------------------------------------------
    // Inner: TruckaObstacle
    // -------------------------------------------------------------------------
    public static final class TruckaObstacle {
        private int position;
        private final int width;
        private final int type;

        public TruckaObstacle(final int position, final int width, final int type) {
            this.position = position;
            this.width = width;
            this.type = type;
        }

        public int getPosition() { return position; }
        public void setPosition(final int position) { this.position = position; }
        public int getWidth() { return width; }
        public int getType() { return type; }
    }

    // -------------------------------------------------------------------------
    // Snapshot for UI
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaSnapshot {
        private final int lives;
        private final int score;
        private final int level;
        private final int jumpTicksLeft;
        private final boolean gameOver;
        private final List<ObstacleSnapshot> obstacles;

        public TruckaJumpaSnapshot(
            final int lives,
            final int score,
            final int level,
            final int jumpTicksLeft,
            final boolean gameOver,
            final List<ObstacleSnapshot> obstacles
        ) {
            this.lives = lives;
            this.score = score;
            this.level = level;
            this.jumpTicksLeft = jumpTicksLeft;
            this.gameOver = gameOver;
            this.obstacles = obstacles == null ? Collections.emptyList() : new ArrayList<>(obstacles);
        }

        public int getLives() { return lives; }
        public int getScore() { return score; }
        public int getLevel() { return level; }
        public int getJumpTicksLeft() { return jumpTicksLeft; }
        public boolean isGameOver() { return gameOver; }
        public List<ObstacleSnapshot> getObstacles() { return obstacles; }
    }

    public static final class ObstacleSnapshot {
        private final int position;
        private final int width;

        public ObstacleSnapshot(final int position, final int width) {
            this.position = position;
            this.width = width;
        }

        public int getPosition() { return position; }
        public int getWidth() { return width; }
    }

    public TruckaJumpaSnapshot getSnapshot() {
        List<ObstacleSnapshot> list = new ArrayList<>();
        for (TruckaObstacle ob : state.getObstacles()) {
            list.add(new ObstacleSnapshot(ob.getPosition(), ob.getWidth()));
        }
        return new TruckaJumpaSnapshot(
            state.getLives(),
            state.getScore(),
            state.getLevel(),
            state.getJumpTicksLeft(),
            state.isGameOver(),
            list
        );
    }

    // -------------------------------------------------------------------------
    // High scores
    // -------------------------------------------------------------------------
    public static final class TruckaHighScoreEntry {
        private final int score;
        private final int level;
        private final long timestamp;

        public TruckaHighScoreEntry(final int score, final int level, final long timestamp) {
            this.score = score;
            this.level = level;
            this.timestamp = timestamp;
        }

        public int getScore() { return score; }
        public int getLevel() { return level; }
        public long getTimestamp() { return timestamp; }
    }

    private final List<TruckaHighScoreEntry> highScores = new ArrayList<>();

    public List<TruckaHighScoreEntry> getHighScores() {
        return Collections.unmodifiableList(highScores);
    }

    public void submitScoreIfHigh() {
        if (state.isGameOver() && state.getScore() > 0) {
            if (highScores.size() < HIGH_SCORE_CAP || state.getScore() > highScores.get(highScores.size() - 1).getScore()) {
                highScores.add(new TruckaHighScoreEntry(state.getScore(), state.getLevel(), System.currentTimeMillis()));
                highScores.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
                if (highScores.size() > HIGH_SCORE_CAP) {
                    highScores.remove(highScores.size() - 1);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------
    public String encodeState() {
        StringBuilder sb = new StringBuilder();
        sb.append(state.getLives()).append(',').append(state.getScore()).append(',').append(state.getLevel()).append(',')
          .append(state.getTickCounter()).append(',').append(state.getJumpTicksLeft()).append(',')
          .append(state.isGameOver()).append(',').append(state.isLevelComplete()).append('|');
        for (TruckaObstacle ob : state.getObstacles()) {
            sb.append(ob.getPosition()).append(',').append(ob.getWidth()).append(';');
        }
        return sb.toString();
    }

    public static int getScoreFromEncoded(final String encoded) {
        if (encoded == null || !encoded.contains("|")) return 0;
        String[] parts = encoded.split("\\|")[0].split(",");
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    }
