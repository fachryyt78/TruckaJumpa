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

    // -------------------------------------------------------------------------
    // Contract identity
    // -------------------------------------------------------------------------
    public static String getContractId() { return TRUCKA_CONTRACT_ID; }
    public static String getVersionHash() { return TRUCKA_VERSION_HASH; }
    public static String getDomainSeed() { return TRUCKA_DOMAIN_SEED; }
    public static int getChainId() { return TRUCKA_CHAIN_ID; }
    public static long getGenesisTs() { return TRUCKA_GENESIS_TS; }

    public static String getContractFingerprint() {
        return TRUCKA_CONTRACT_ID + "-" + TRUCKA_VERSION_HASH + "-" + TRUCKA_CHAIN_ID;
    }

    public static String formatContractIdShort() {
        if (TRUCKA_CONTRACT_ID == null || TRUCKA_CONTRACT_ID.length() < 12) return TRUCKA_CONTRACT_ID;
        return TRUCKA_CONTRACT_ID.substring(0, 10) + "..." + TRUCKA_CONTRACT_ID.substring(TRUCKA_CONTRACT_ID.length() - 8);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaBuilder {
        private int trackLength = TRACK_LENGTH;
        private int truckPosition = TRUCK_POSITION;
        private int jumpDurationTicks = JUMP_DURATION_TICKS;
        private int lives = INITIAL_LIVES;
        private int obstacleBaseSpeed = OBSTACLE_BASE_SPEED;
        private int maxLevel = MAX_LEVEL;
        private int pointsPerObstacle = POINTS_PER_OBSTACLE;
        private int pointsLevelBonus = POINTS_LEVEL_BONUS;
        private int obstacleSpawnInterval = OBSTACLE_SPAWN_INTERVAL;
        private int minObstacleWidth = MIN_OBSTACLE_WIDTH;
        private int maxObstacleWidth = MAX_OBSTACLE_WIDTH;
        private Long seed = null;

        public TruckaJumpaBuilder trackLength(final int n) { this.trackLength = n; return this; }
        public TruckaJumpaBuilder truckPosition(final int n) { this.truckPosition = n; return this; }
        public TruckaJumpaBuilder jumpDurationTicks(final int n) { this.jumpDurationTicks = n; return this; }
        public TruckaJumpaBuilder lives(final int n) { this.lives = n; return this; }
        public TruckaJumpaBuilder obstacleBaseSpeed(final int n) { this.obstacleBaseSpeed = n; return this; }
        public TruckaJumpaBuilder maxLevel(final int n) { this.maxLevel = n; return this; }
        public TruckaJumpaBuilder pointsPerObstacle(final int n) { this.pointsPerObstacle = n; return this; }
        public TruckaJumpaBuilder pointsLevelBonus(final int n) { this.pointsLevelBonus = n; return this; }
        public TruckaJumpaBuilder obstacleSpawnInterval(final int n) { this.obstacleSpawnInterval = n; return this; }
        public TruckaJumpaBuilder minObstacleWidth(final int n) { this.minObstacleWidth = n; return this; }
        public TruckaJumpaBuilder maxObstacleWidth(final int n) { this.maxObstacleWidth = n; return this; }
        public TruckaJumpaBuilder seed(final long s) { this.seed = s; return this; }

        public TruckaJumpa build() {
            TruckaJumpaConfig cfg = new TruckaJumpaConfig(
                trackLength, truckPosition, jumpDurationTicks, lives, obstacleBaseSpeed,
                maxLevel, pointsPerObstacle, pointsLevelBonus, obstacleSpawnInterval,
                minObstacleWidth, maxObstacleWidth
            );
            long s = seed != null ? seed : System.nanoTime();
            return new TruckaJumpa(cfg, s);
        }
    }

    public static TruckaJumpaBuilder builder() { return new TruckaJumpaBuilder(); }

    // -------------------------------------------------------------------------
    // Presets
    // -------------------------------------------------------------------------
    public static TruckaJumpaConfig presetEasy() {
        return new TruckaJumpaConfig(20, 0, 8, 5, 1, 50, 100, 80, 10, 1, 2);
    }

    public static TruckaJumpaConfig presetNormal() {
        return new TruckaJumpaConfig(
            TRACK_LENGTH, TRUCK_POSITION, JUMP_DURATION_TICKS, INITIAL_LIVES,
            OBSTACLE_BASE_SPEED, MAX_LEVEL, POINTS_PER_OBSTACLE, POINTS_LEVEL_BONUS,
            OBSTACLE_SPAWN_INTERVAL, MIN_OBSTACLE_WIDTH, MAX_OBSTACLE_WIDTH
        );
    }

    public static TruckaJumpaConfig presetHard() {
        return new TruckaJumpaConfig(28, 0, 4, 2, 2, 99, 120, 100, 5, 2, 4);
    }

    // -------------------------------------------------------------------------
    // Event codes
    // -------------------------------------------------------------------------
    public static final int EVT_NONE = 0;
    public static final int EVT_ALREADY_JUMPING = 1;
    public static final int EVT_JUMP = 2;
    public static final int EVT_TICK = 3;
    public static final int EVT_CRASH = 4;
    public static final int EVT_GAME_OVER = 5;
    public static final int EVT_CLEARED = 6;
    public static final int EVT_LEVEL_UP = 7;

    public String getLastEventDescription() {
        switch (lastEventCode) {
            case -1: return TRUCKA_ERR_GAME_OVER;
            case 1: return TRUCKA_ERR_ALREADY_JUMPING;
            case 2: return TRUCKA_EVT_JUMP;
            case 3: return TRUCKA_EVT_TICK;
            case 4: return TRUCKA_EVT_CRASH;
            case 5: return TRUCKA_EVT_GAME_OVER;
            case 6: return TRUCKA_EVT_CLEARED;
            case 7: return TRUCKA_EVT_LEVEL_UP;
            default: return "";
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------
    public static boolean isValidConfig(final TruckaJumpaConfig config) {
        if (config == null) return false;
        if (config.getTrackLength() < 5 || config.getTrackLength() > 50) return false;
        if (config.getTruckPosition() < 0 || config.getTruckPosition() >= config.getTrackLength()) return false;
        if (config.getJumpDurationTicks() < 1 || config.getJumpDurationTicks() > 20) return false;
        if (config.getLives() < 1 || config.getLives() > 10) return false;
        return true;
    }

    public int getObstacleCount() { return state.getObstacles().size(); }

    public TruckaJumpaState copyState() {
        return TruckaJumpaState.copyFrom(state);
    }

    public void restoreState(final TruckaJumpaState s) {
        if (s != null) state = TruckaJumpaState.copyFrom(s);
    }

    // -------------------------------------------------------------------------
    // Replay input
    // -------------------------------------------------------------------------
    public static final class TruckaJumpInput {
        private final int tick;
        private final boolean jump;

        public TruckaJumpInput(final int tick, final boolean jump) {
            this.tick = tick;
            this.jump = jump;
        }

        public int getTick() { return tick; }
        public boolean isJump() { return jump; }
    }

    public static TruckaJumpaState runReplay(final TruckaJumpaConfig config, final long seed, final List<TruckaJumpInput> inputs, final int maxTicks) {
        TruckaJumpa game = new TruckaJumpa(config, seed);
        int tick = 0;
        int inputIdx = 0;
        while (tick < maxTicks && !game.getState().isGameOver()) {
            while (inputIdx < inputs.size() && inputs.get(inputIdx).getTick() <= tick) {
                if (inputs.get(inputIdx).isJump()) game.jump();
                inputIdx++;
            }
            game.tick();
            tick++;
        }
        return game.getState();
    }

    // -------------------------------------------------------------------------
    // Runtime exception
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaRuntimeException extends RuntimeException {
        private final String errorCode;

        public TruckaJumpaRuntimeException(final String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }

        public String getErrorCode() { return errorCode; }
    }

    // -------------------------------------------------------------------------
    // Display / palette
    // -------------------------------------------------------------------------
    public static String getPaletteTruckHex() { return String.format("#%06x", PALETTE_TRUCK & 0xFFFFFF); }
    public static String getPaletteBarrierHex() { return String.format("#%06x", PALETTE_BARRIER & 0xFFFFFF); }
    public static String getPaletteTrackHex() { return String.format("#%06x", PALETTE_TRACK & 0xFFFFFF); }

    public int getDisplayWidthPx() {
        return config.getTrackLength() * DISPLAY_CELL_W;
    }

    public int getDisplayHeightPx() {
        return DISPLAY_CELL_H * 2;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    public boolean isActive() { return !state.isGameOver(); }
    public boolean canMove() { return !state.isGameOver(); }
    public int getRemainingLives() { return Math.max(0, state.getLives()); }

    // -------------------------------------------------------------------------
    // Sequel reference (Frogget / Frogga)
    // -------------------------------------------------------------------------
    public static final String FROGGA_SEQUEL_ID = "Frogget";
    public static final String TRUCKA_SEQUEL_LABEL = "TruckaJumpa";

    public static String getSequelInfo() {
        return TRUCKA_SEQUEL_LABEL + " follows " + FROGGA_SEQUEL_ID + ". Same universe, truck jump mechanics.";
    }

    // -------------------------------------------------------------------------
    // Session stats
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaStats {
        private final int totalJumps;
        private final int totalCleared;
        private final int totalCrashes;
        private final int peakLevel;
        private final int peakScore;

        public TruckaJumpaStats(final int totalJumps, final int totalCleared, final int totalCrashes, final int peakLevel, final int peakScore) {
            this.totalJumps = totalJumps;
            this.totalCleared = totalCleared;
            this.totalCrashes = totalCrashes;
            this.peakLevel = peakLevel;
            this.peakScore = peakScore;
        }

        public int getTotalJumps() { return totalJumps; }
        public int getTotalCleared() { return totalCleared; }
        public int getTotalCrashes() { return totalCrashes; }
        public int getPeakLevel() { return peakLevel; }
        public int getPeakScore() { return peakScore; }
    }

    private int sessionJumps;
    private int sessionCleared;
    private int sessionCrashes;

    public void resetSessionStats() {
        sessionJumps = 0;
        sessionCleared = 0;
        sessionCrashes = 0;
    }

    public TruckaJumpaStats getSessionStats() {
        return new TruckaJumpaStats(sessionJumps, sessionCleared, sessionCrashes, state.getLevel(), state.getScore());
    }

    public void jumpWithStats() {
        jump();
        if (lastEventCode == 2) sessionJumps++;
    }

    public void tickWithStats() {
        int livesBefore = state.getLives();
        tick();
        if (state.getLives() < livesBefore) sessionCrashes++;
        if (lastEventCode == EVT_CLEARED) sessionCleared++;
    }

    // -------------------------------------------------------------------------
    // Event log
    // -------------------------------------------------------------------------
    public static final class TruckaJumpaEvent {
        private final int code;
        private final String name;
        private final int tick;
        private final int jumpTicksLeft;

        public TruckaJumpaEvent(final int code, final String name, final int tick, final int jumpTicksLeft) {
            this.code = code;
            this.name = name;
            this.tick = tick;
            this.jumpTicksLeft = jumpTicksLeft;
        }

        public int getCode() { return code; }
        public String getName() { return name; }
        public int getTick() { return tick; }
        public int getJumpTicksLeft() { return jumpTicksLeft; }
    }

    private final List<TruckaJumpaEvent> eventLog = new ArrayList<>();

    public List<TruckaJumpaEvent> getEventLog() { return Collections.unmodifiableList(eventLog); }
    public void clearEventLog() { eventLog.clear(); }

    public void jumpWithEventLog() {
        jump();
        eventLog.add(new TruckaJumpaEvent(lastEventCode, lastEventName, state.getTickCounter(), state.getJumpTicksLeft()));
    }

    public void tickWithEventLog() {
        tick();
        eventLog.add(new TruckaJumpaEvent(lastEventCode, lastEventName, state.getTickCounter(), state.getJumpTicksLeft()));
    }

    // -------------------------------------------------------------------------
    // Level description
    // -------------------------------------------------------------------------
    public static String getLevelDescription(final int level) {
        if (level <= 0) return "Invalid level";
        if (level <= 2) return "Easy. Slow obstacles.";
        if (level <= 5) return "Medium. More speed.";
        if (level <= 8) return "Hard. Tight timing.";
        return "Very hard. Expert jumps.";
    }

    public int getObstacleSpeedForLevel() {
        return config.getObstacleBaseSpeed() + (state.getLevel() / 2);
    }

    public int getPointsPerClear() {
        return config.getPointsPerObstacle() + state.getLevel() * 5;
    }

    public String getStateDigest() {
        return "L" + state.getLevel() + " S" + state.getScore() + " Lives" + state.getLives()
            + " Tick" + state.getTickCounter() + " Jump" + state.getJumpTicksLeft()
            + (state.isGameOver() ? " GO" : "") + " Obs" + state.getObstacles().size();
    }

    public boolean wouldCollideAtTruckPosition() {
        if (state.getJumpTicksLeft() > 0) return false;
        int truckPos = config.getTruckPosition();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() <= truckPos && ob.getPosition() + ob.getWidth() > truckPos) return true;
        }
        return false;
    }

    public void tick(final int count) {
        for (int i = 0; i < count; i++) {
            tick();
            if (state.isGameOver()) break;
        }
    }

    public static final long TICK_MS = 1000 / RETRO_FPS;

    public static int ticksFromElapsedMs(final long elapsedMs) {
        return (int) (elapsedMs / TICK_MS);
    }

    public static String getContractInfo() {
        return "TruckaJumpa v1 " + TRUCKA_CONTRACT_ID + " " + TRUCKA_VERSION_HASH;
    }

    public static boolean isContractId(final String id) { return TRUCKA_CONTRACT_ID.equals(id); }
    public static boolean isVersionHash(final String hash) { return TRUCKA_VERSION_HASH.equals(hash); }

    public int getTrackLength() { return config.getTrackLength(); }
    public int getTruckPosition() { return config.getTruckPosition(); }
    public int getJumpDurationTicks() { return config.getJumpDurationTicks(); }
    public static int getTrackLengthConstant() { return TRACK_LENGTH; }
    public static int getJumpDurationConstant() { return JUMP_DURATION_TICKS; }
    public static int getPointsPerObstacleConstant() { return POINTS_PER_OBSTACLE; }
    public static int getPointsLevelBonusConstant() { return POINTS_LEVEL_BONUS; }

    public int getNextSpawnTick() {
        int interval = config.getObstacleSpawnInterval();
        int t = state.getTickCounter();
        if (t == 0) return interval;
        return interval - (t % interval);
    }

    public boolean isDefaultConfig() {
        return config.getTrackLength() == TRACK_LENGTH
            && config.getJumpDurationTicks() == JUMP_DURATION_TICKS
            && config.getLives() == INITIAL_LIVES
            && config.getObstacleBaseSpeed() == OBSTACLE_BASE_SPEED;
    }

    public static boolean isDefaultConfig(final TruckaJumpaConfig config) {
        if (config == null) return false;
        return config.getTrackLength() == TRACK_LENGTH
            && config.getJumpDurationTicks() == JUMP_DURATION_TICKS
            && config.getLives() == INITIAL_LIVES;
    }

    public int getObstacleCountInRange(final int fromPos, final int toPos) {
        int n = 0;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() <= toPos && ob.getPosition() + ob.getWidth() >= fromPos) n++;
        }
        return n;
    }

    public TruckaJumpaSnapshot getSnapshotAt(final int tickLimit) {
        TruckaJumpaState copy = TruckaJumpaState.copyFrom(state);
        TruckaJumpa g = new TruckaJumpa(config, rng.nextLong());
        g.state = copy;
        for (int i = 0; i < tickLimit && !g.state.isGameOver(); i++) g.tick();
        return g.getSnapshot();
    }

    // -------------------------------------------------------------------------
    // Cell type for rendering (track position -> cell content)
    // -------------------------------------------------------------------------
    public enum TruckaCellType { EMPTY, TRUCK, OBSTACLE }

    public TruckaCellType getCellType(final int position) {
        if (position == config.getTruckPosition()) return TruckaCellType.TRUCK;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (position >= ob.getPosition() && position < ob.getPosition() + ob.getWidth()) return TruckaCellType.OBSTACLE;
        }
        return TruckaCellType.EMPTY;
    }

    public boolean isTruckAt(final int position) {
        return position == config.getTruckPosition();
    }

    public boolean hasObstacleAt(final int position) {
        for (TruckaObstacle ob : state.getObstacles()) {
            if (position >= ob.getPosition() && position < ob.getPosition() + ob.getWidth()) return true;
        }
        return false;
    }

    public String toRetroGridString() {
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < config.getTrackLength(); p++) {
            TruckaCellType t = getCellType(p);
            if (t == TruckaCellType.TRUCK) sb.append('T');
            else if (t == TruckaCellType.OBSTACLE) sb.append('#');
            else sb.append('.');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Key binding (for web UI)
    // -------------------------------------------------------------------------
    public static final int KEY_JUMP = 1;
    public static final int KEY_NONE = 0;

    public static int actionFromKey(final String key) {
        if (key == null) return KEY_NONE;
        switch (key.toLowerCase()) {
            case " ":
            case "space":
            case "arrowup":
            case "keyw":
            case "up":
            case "w": return KEY_JUMP;
            default: return KEY_NONE;
        }
    }

    public void performKeyAction(final String key) {
        if (actionFromKey(key) == KEY_JUMP) jump();
    }

    // -------------------------------------------------------------------------
    // Clamp helpers
    // -------------------------------------------------------------------------
    public static int clampLevel(final int level, final TruckaJumpaConfig config) {
        if (config == null) return Math.max(1, Math.min(level, MAX_LEVEL));
        return Math.max(1, Math.min(level, config.getMaxLevel()));
    }

    public static int clampScore(final int score) {
        return Math.max(0, score);
    }

    public int getMaxPossibleScoreApprox() {
        return config.getMaxLevel() * (config.getPointsPerObstacle() * 20 + config.getPointsLevelBonus());
    }

    // -------------------------------------------------------------------------
    // Copy state and restore
    // -------------------------------------------------------------------------
    public void copyStateTo(final TruckaJumpaState target) {
        if (target == null) return;
        target.setLives(state.getLives());
        target.setScore(state.getScore());
        target.setLevel(state.getLevel());
        target.setTickCounter(state.getTickCounter());
        target.setJumpTicksLeft(state.getJumpTicksLeft());
        target.setGameOver(state.isGameOver());
        target.setLevelComplete(state.isLevelComplete());
        target.setObstacles(new ArrayList<>());
        for (TruckaObstacle ob : state.getObstacles()) {
            target.getObstacles().add(new TruckaObstacle(ob.getPosition(), ob.getWidth(), ob.getType()));
        }
    }

    // -------------------------------------------------------------------------
    // Start new game with reset session
    // -------------------------------------------------------------------------
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TruckaJumpa that = (TruckaJumpa) o;
        return Objects.equals(config, that.config) && state.getScore() == that.getState().getScore()
            && state.getLevel() == that.getState().getLevel() && state.getLives() == that.getState().getLives();
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, state.getScore(), state.getLevel(), state.getTickCounter());
    }

    public void startNewGameWithResetStats() {
        startNewGame();
        resetSessionStats();
    }

    // -------------------------------------------------------------------------
    // Constants for display (retro)
    // -------------------------------------------------------------------------
    public static final int ANIM_TRUCK_GROUND = 0;
    public static final int ANIM_TRUCK_AIR = 1;
    public static final int SOUND_JUMP = 0;
    public static final int SOUND_CRASH = 1;
    public static final int SOUND_CLEAR = 2;
    public static final int SOUND_LEVEL = 3;

    public int getTruckAnimationFrame() {
        return state.getJumpTicksLeft() > 0 ? ANIM_TRUCK_AIR : ANIM_TRUCK_GROUND;
    }

    // -------------------------------------------------------------------------
    // Obstacle iterator
    // -------------------------------------------------------------------------
    public List<ObstacleSnapshot> getObstaclesSnapshot() {
        List<ObstacleSnapshot> list = new ArrayList<>();
        for (TruckaObstacle ob : state.getObstacles()) {
            list.add(new ObstacleSnapshot(ob.getPosition(), ob.getWidth()));
        }
        return list;
    }

    public int getClosestObstaclePosition() {
        int truckPos = config.getTruckPosition();
        int closest = config.getTrackLength();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() >= truckPos && ob.getPosition() < closest) closest = ob.getPosition();
        }
        return closest == config.getTrackLength() ? -1 : closest;
    }

    public int getTicksUntilNextObstacleAtTruck() {
        int truckPos = config.getTruckPosition();
        int speed = getObstacleSpeedForLevel();
        int minTicks = Integer.MAX_VALUE;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() > truckPos) {
                int ticks = (ob.getPosition() - truckPos) / speed;
                if (ticks < minTicks) minTicks = ticks;
            }
        }
        return minTicks == Integer.MAX_VALUE ? -1 : minTicks;
    }

    // -------------------------------------------------------------------------
    // Level completion (optional: complete after N cleared)
    // -------------------------------------------------------------------------
    public static final int OBSTACLES_PER_LEVEL = 15;

    public boolean shouldLevelComplete() {
        return state.getScore() > 0 && (state.getScore() - (state.getLevel() - 1) * config.getPointsLevelBonus())
            / getPointsPerClear() >= OBSTACLES_PER_LEVEL * state.getLevel();
    }

    public void checkAndSetLevelComplete() {
        if (shouldLevelComplete()) state.setLevelComplete(true);
    }

    // -------------------------------------------------------------------------
    // More getters for config
    // -------------------------------------------------------------------------
    public int getConfigTrackLength() { return config.getTrackLength(); }
    public int getConfigLives() { return config.getLives(); }
    public int getConfigObstacleBaseSpeed() { return config.getObstacleBaseSpeed(); }
    public int getConfigMaxLevel() { return config.getMaxLevel(); }
    public int getConfigPointsPerObstacle() { return config.getPointsPerObstacle(); }
    public int getConfigPointsLevelBonus() { return config.getPointsLevelBonus(); }
    public int getConfigObstacleSpawnInterval() { return config.getObstacleSpawnInterval(); }
    public int getConfigMinObstacleWidth() { return config.getMinObstacleWidth(); }
    public int getConfigMaxObstacleWidth() { return config.getMaxObstacleWidth(); }

    public static int getInitialLivesConstant() { return INITIAL_LIVES; }
    public static int getObstacleSpawnIntervalConstant() { return OBSTACLE_SPAWN_INTERVAL; }
    public static int getMinObstacleWidthConstant() { return MIN_OBSTACLE_WIDTH; }
    public static int getMaxObstacleWidthConstant() { return MAX_OBSTACLE_WIDTH; }
    public static int getHighScoreCapConstant() { return HIGH_SCORE_CAP; }
    public static int getRetroFpsConstant() { return RETRO_FPS; }

    public String getErrCrash() { return TRUCKA_ERR_CRASH; }
    public String getEvtJump() { return TRUCKA_EVT_JUMP; }
    public String getEvtCleared() { return TRUCKA_EVT_CLEARED; }
    public String getEvtGameOver() { return TRUCKA_EVT_GAME_OVER; }

    // -------------------------------------------------------------------------
    // Level descriptions for UI
    // -------------------------------------------------------------------------
    private static final String[] LEVEL_DESCRIPTIONS = {
        "Level 1: Get used to the jump timing.",
        "Level 2: Obstacles move a bit faster.",
        "Level 3: More obstacles, stay sharp.",
        "Level 4: Speed increases. Time your jumps.",
        "Level 5: Hard. Quick reactions needed.",
        "Level 6: Very hard. Expert only.",
        "Level 7: Near maximum difficulty.",
        "Level 8: Maximum speed and density.",
        "Level 9+: Ultimate challenge."
    };

    public static String getLevelDescriptionForUI(final int level) {
        if (level <= 0) return "Invalid level";
        int idx = Math.min(level - 1, LEVEL_DESCRIPTIONS.length - 1);
        return LEVEL_DESCRIPTIONS[idx >= 0 ? idx : 0];
    }

    public String getCurrentLevelDescription() {
        return getLevelDescriptionForUI(state.getLevel());
    }

    // -------------------------------------------------------------------------
    // Frogget / Frogga sequel compatibility (same universe)
    // -------------------------------------------------------------------------
    public static final int FROGGET_TRACK_COLS = 11;
    public static final int TRUCKA_TRACK_LENGTH_DEFAULT = 24;

    public static boolean isSequelToFrogget() {
        return true;
    }

    public static String getUniverseName() {
        return "FroggaUniverse";
    }

    // -------------------------------------------------------------------------
    // Checksum / integrity (for save validation)
    // -------------------------------------------------------------------------
    public long getStateChecksum() {
        long h = state.getLives() * 31L + state.getScore() * 37L + state.getLevel() * 41L + state.getTickCounter() * 43L;
        for (TruckaObstacle ob : state.getObstacles()) {
            h = h * 59 + ob.getPosition() * 61 + ob.getWidth() * 67;
        }
        return h;
    }

    public static boolean validateEncodedState(final String encoded) {
        if (encoded == null || !encoded.contains("|")) return false;
        String[] parts = encoded.split("\\|")[0].split(",");
        return parts.length >= 7;
    }

    // -------------------------------------------------------------------------
    // More presets
    // -------------------------------------------------------------------------
    public static TruckaJumpaConfig presetLongTrack() {
        return new TruckaJumpaConfig(40, 0, 8, 3, 1, 99, 80, 60, 12, 1, 3);
    }

    public static TruckaJumpaConfig presetShortJump() {
        return new TruckaJumpaConfig(16, 0, 3, 2, 2, 50, 100, 70, 4, 2, 4);
    }

    // -------------------------------------------------------------------------
    // Window / viewport (for UI)
    // -------------------------------------------------------------------------
    public static final int VIEWPORT_TRACK_CELLS = 12;
    public static final int VIEWPORT_OFFSET_MAX = 12;

    public int getViewportStart(final int offset) {
        int start = config.getTruckPosition() + offset - VIEWPORT_TRACK_CELLS / 2;
        return Math.max(0, Math.min(start, config.getTrackLength() - VIEWPORT_TRACK_CELLS));
    }

    public int getVisibleObstacleCount(final int viewportStart) {
        int end = viewportStart + VIEWPORT_TRACK_CELLS;
        int n = 0;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() + ob.getWidth() > viewportStart && ob.getPosition() < end) n++;
        }
        return n;
    }

    // -------------------------------------------------------------------------
    // Debug / test
    // -------------------------------------------------------------------------
    public boolean stateEquals(final TruckaJumpa other) {
        if (other == null) return false;
        if (state.getLives() != other.getState().getLives()) return false;
        if (state.getScore() != other.getState().getScore()) return false;
        if (state.getLevel() != other.getState().getLevel()) return false;
        if (state.getObstacles().size() != other.getState().getObstacles().size()) return false;
        return true;
    }

    public String toCompactState() {
        return "L" + state.getLevel() + " S" + state.getScore() + " " + state.getLives() + " lives J" + state.getJumpTicksLeft();
    }

    // -------------------------------------------------------------------------
    // Spawn logic (exposed for tests)
    // -------------------------------------------------------------------------
    public void spawnObstacleAt(final int position, final int width) {
        state.getObstacles().add(new TruckaObstacle(position, width, OBSTACLE_TYPE_BARRIER));
    }

    public int getSpawnIntervalForLevel() {
        return Math.max(2, config.getObstacleSpawnInterval() - state.getLevel() / 3);
    }

    // -------------------------------------------------------------------------
    // Tick with optional auto level-complete check
    // -------------------------------------------------------------------------
    public void tickAndCheckLevelComplete() {
        tick();
        checkAndSetLevelComplete();
    }

    // -------------------------------------------------------------------------
    // Additional constant accessors (for external tools / UI)
    // -------------------------------------------------------------------------
    public static int getObstacleTypeBarrier() { return OBSTACLE_TYPE_BARRIER; }
    public static int getObstacleTypePit() { return OBSTACLE_TYPE_PIT; }
    public static int getPaletteTruckRaw() { return PALETTE_TRUCK; }
    public static int getPaletteBarrierRaw() { return PALETTE_BARRIER; }
    public static int getPaletteTrackRaw() { return PALETTE_TRACK; }
    public static int getDisplayCellW() { return DISPLAY_CELL_W; }
    public static int getDisplayCellH() { return DISPLAY_CELL_H; }
    public static int getViewportTrackCells() { return VIEWPORT_TRACK_CELLS; }
    public static int getObstaclesPerLevel() { return OBSTACLES_PER_LEVEL; }
    public static String getErrBounds() { return TRUCKA_ERR_BOUNDS; }
    public static String getErrCrash() { return TRUCKA_ERR_CRASH; }
    public static String getErrNoLives() { return TRUCKA_ERR_NO_LIVES; }
    public static String getErrGameOver() { return TRUCKA_ERR_GAME_OVER; }
    public static String getErrAlreadyJumping() { return TRUCKA_ERR_ALREADY_JUMPING; }
    public static String getEvtJump() { return TRUCKA_EVT_JUMP; }
    public static String getEvtCleared() { return TRUCKA_EVT_CLEARED; }
    public static String getEvtCrash() { return TRUCKA_EVT_CRASH; }
    public static String getEvtLevelUp() { return TRUCKA_EVT_LEVEL_UP; }
    public static String getEvtGameOver() { return TRUCKA_EVT_GAME_OVER; }
    public static String getEvtTick() { return TRUCKA_EVT_TICK; }

    public int getStateLives() { return state.getLives(); }
    public int getStateScore() { return state.getScore(); }
    public int getStateLevel() { return state.getLevel(); }
    public int getStateTickCounter() { return state.getTickCounter(); }
    public int getStateJumpTicksLeft() { return state.getJumpTicksLeft(); }
    public boolean getStateGameOver() { return state.isGameOver(); }
    public boolean getStateLevelComplete() { return state.isLevelComplete(); }
    public int getStateObstacleCount() { return state.getObstacles().size(); }

    public boolean isGameOver() { return state.isGameOver(); }
    public boolean isLevelComplete() { return state.isLevelComplete(); }
    public boolean isTruckGrounded() { return state.getJumpTicksLeft() == 0; }
    public boolean isTruckInAir() { return state.getJumpTicksLeft() > 0; }
    public int getJumpTicksRemaining() { return state.getJumpTicksLeft(); }
    public int getScore() { return state.getScore(); }
    public int getLevel() { return state.getLevel(); }
    public int getLives() { return state.getLives(); }
    public int getTickCounter() { return state.getTickCounter(); }

    public static int getMaxLevelConstant() { return MAX_LEVEL; }
    public static long getTickMs() { return TICK_MS; }
    public static long getGenesisTsConstant() { return TRUCKA_GENESIS_TS; }
    public static int getChainIdConstant() { return TRUCKA_CHAIN_ID; }

    /** Returns the number of ticks the truck remains in the air (0 if grounded). */
    public int getAirTimeRemaining() { return state.getJumpTicksLeft(); }

    /** Returns true if a jump can be triggered (grounded and game not over). */
    public boolean isJumpAllowed() { return canJump(); }

    public int getTotalObstacleWidth() {
        int w = 0;
        for (TruckaObstacle ob : state.getObstacles()) w += ob.getWidth();
        return w;
    }

    public int getLeftmostObstaclePosition() {
        int min = config.getTrackLength();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() < min) min = ob.getPosition();
        }
        return min == config.getTrackLength() ? -1 : min;
    }

    public int getRightmostObstacleEndPosition() {
        int max = -1;
        for (TruckaObstacle ob : state.getObstacles()) {
            int end = ob.getPosition() + ob.getWidth();
            if (end > max) max = end;
        }
        return max;
    }

    // -------------------------------------------------------------------------
    // Documentation: TruckaJumpa is the sequel to Frogget (Frogga). Same universe.
    // Truck stays at position 0; obstacles move left. Jump duration in ticks.
    // Collision when obstacle overlaps truck position and truck is grounded.
    // Cleared when obstacle moves fully past truck; score added. Spawn every N ticks.
    // Level increases difficulty (speed, spawn rate). No token, no claim.
    // Contract identity: TRUCKA_CONTRACT_ID, TRUCKA_VERSION_HASH, TRUCKA_DOMAIN_SEED.
    // All config is constructor-set; no readonly; use final for constants.
    // -------------------------------------------------------------------------

    public int getObstacleAtPosition(final int pos) {
        int i = 0;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (pos >= ob.getPosition() && pos < ob.getPosition() + ob.getWidth()) return i;
            i++;
        }
        return -1;
    }

    public int getObstaclePosition(final int index) {
        if (index < 0 || index >= state.getObstacles().size()) return -1;
        return state.getObstacles().get(index).getPosition();
    }

    public int getObstacleWidth(final int index) {
        if (index < 0 || index >= state.getObstacles().size()) return 0;
        return state.getObstacles().get(index).getWidth();
    }

    public boolean hasObstacleInRange(final int from, final int to) {
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() + ob.getWidth() > from && ob.getPosition() < to) return true;
        }
        return false;
    }

    public int countObstaclesInRange(final int from, final int to) {
        int n = 0;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() + ob.getWidth() > from && ob.getPosition() < to) n++;
        }
        return n;
    }

    public int getSafeJumpTicksBeforeCollision() {
        if (state.getJumpTicksLeft() > 0) return state.getJumpTicksLeft();
        int truckPos = config.getTruckPosition();
        int speed = getObstacleSpeedForLevel();
        int minTicks = Integer.MAX_VALUE;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() > truckPos) {
                int ticks = (ob.getPosition() - truckPos) / speed;
                if (ticks < minTicks) minTicks = ticks;
            }
        }
        return minTicks == Integer.MAX_VALUE ? config.getJumpDurationTicks() : Math.min(minTicks, config.getJumpDurationTicks());
    }

    public boolean wouldJumpClearNextObstacle() {
        int truckPos = config.getTruckPosition();
        int speed = getObstacleSpeedForLevel();
        int jumpTicks = config.getJumpDurationTicks();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() > truckPos) {
                int ticksToReach = (ob.getPosition() - truckPos) / speed;
                int ticksToClear = (ob.getPosition() + ob.getWidth() - truckPos) / speed;
                if (ticksToReach <= jumpTicks && ticksToClear > jumpTicks) return false;
            }
        }
        return true;
    }

    public static int getDefaultTrackLength() { return TRACK_LENGTH; }
    public static int getDefaultTruckPosition() { return TRUCK_POSITION; }
    public static int getDefaultJumpDurationTicks() { return JUMP_DURATION_TICKS; }
    public static int getDefaultInitialLives() { return INITIAL_LIVES; }
    public static int getDefaultObstacleBaseSpeed() { return OBSTACLE_BASE_SPEED; }
    public static int getDefaultMaxLevel() { return MAX_LEVEL; }
    public static int getDefaultPointsPerObstacle() { return POINTS_PER_OBSTACLE; }
    public static int getDefaultPointsLevelBonus() { return POINTS_LEVEL_BONUS; }
    public static int getDefaultObstacleSpawnInterval() { return OBSTACLE_SPAWN_INTERVAL; }
    public static int getDefaultMinObstacleWidth() { return MIN_OBSTACLE_WIDTH; }
    public static int getDefaultMaxObstacleWidth() { return MAX_OBSTACLE_WIDTH; }
    public static int getDefaultHighScoreCap() { return HIGH_SCORE_CAP; }
    public static int getKeyJump() { return KEY_JUMP; }
    public static int getKeyNone() { return KEY_NONE; }
    public static int getEvtNone() { return EVT_NONE; }
    public static int getEvtAlreadyJumping() { return EVT_ALREADY_JUMPING; }
    public static int getEvtJumpCode() { return EVT_JUMP; }
    public static int getEvtTickCode() { return EVT_TICK; }
    public static int getEvtCrashCode() { return EVT_CRASH; }
    public static int getEvtGameOverCode() { return EVT_GAME_OVER; }
    public static int getEvtClearedCode() { return EVT_CLEARED; }
    public static int getEvtLevelUpCode() { return EVT_LEVEL_UP; }
    public static int getAnimTruckGround() { return ANIM_TRUCK_GROUND; }
    public static int getAnimTruckAir() { return ANIM_TRUCK_AIR; }
    public static int getSoundJump() { return SOUND_JUMP; }
    public static int getSoundCrash() { return SOUND_CRASH; }
    public static int getSoundClear() { return SOUND_CLEAR; }
    public static int getSoundLevel() { return SOUND_LEVEL; }
    public static String getFroggaSequelId() { return FROGGA_SEQUEL_ID; }
    public static String getTruckaSequelLabel() { return TRUCKA_SEQUEL_LABEL; }
    public static int getFroggetTrackCols() { return FROGGET_TRACK_COLS; }
    public static int getTruckaTrackLengthDefault() { return TRUCKA_TRACK_LENGTH_DEFAULT; }
    public static int getViewportOffsetMax() { return VIEWPORT_OFFSET_MAX; }

    public float getJumpProgress() {
        if (config.getJumpDurationTicks() <= 0) return 1.0f;
        int left = state.getJumpTicksLeft();
        if (left <= 0) return 1.0f;
        return 1.0f - (float) left / config.getJumpDurationTicks();
    }

    public int getScoreForNextLevel() {
        return state.getLevel() * config.getPointsLevelBonus();
    }

    public boolean isHighScoreEligible(final int minScore) {
        return state.isGameOver() && state.getScore() >= minScore;
    }

    public static TruckaJumpa createWithSeed(final long seed) {
        return new TruckaJumpa(seed);
    }

    public static TruckaJumpa createWithConfigAndSeed(final TruckaJumpaConfig config, final long seed) {
        return new TruckaJumpa(config, seed);
    }

    public TruckaJumpaConfig getConfigCopy() {
        return new TruckaJumpaConfig(
            config.getTrackLength(),
            config.getTruckPosition(),
            config.getJumpDurationTicks(),
            config.getLives(),
            config.getObstacleBaseSpeed(),
            config.getMaxLevel(),
            config.getPointsPerObstacle(),
            config.getPointsLevelBonus(),
            config.getObstacleSpawnInterval(),
            config.getMinObstacleWidth(),
            config.getMaxObstacleWidth()
        );
    }

    public int getConfigTrackLengthInternal() { return config.getTrackLength(); }
    public int getConfigTruckPositionInternal() { return config.getTruckPosition(); }
    public int getConfigJumpDurationTicksInternal() { return config.getJumpDurationTicks(); }
    public int getConfigLivesInternal() { return config.getLives(); }
    public int getConfigObstacleBaseSpeedInternal() { return config.getObstacleBaseSpeed(); }
    public int getConfigMaxLevelInternal() { return config.getMaxLevel(); }
    public int getConfigPointsPerObstacleInternal() { return config.getPointsPerObstacle(); }
    public int getConfigPointsLevelBonusInternal() { return config.getPointsLevelBonus(); }
    public int getConfigObstacleSpawnIntervalInternal() { return config.getObstacleSpawnInterval(); }
    public int getConfigMinObstacleWidthInternal() { return config.getMinObstacleWidth(); }
    public int getConfigMaxObstacleWidthInternal() { return config.getMaxObstacleWidth(); }

    public static String getContractIdStatic() { return TRUCKA_CONTRACT_ID; }
    public static String getVersionHashStatic() { return TRUCKA_VERSION_HASH; }
    public static String getDomainSeedStatic() { return TRUCKA_DOMAIN_SEED; }
    public String getContractIdInstance() { return TRUCKA_CONTRACT_ID; }
    public String getVersionHashInstance() { return TRUCKA_VERSION_HASH; }
    public String getDomainSeedInstance() { return TRUCKA_DOMAIN_SEED; }

    public int getLastEventCodeValue() { return lastEventCode; }
    public String getLastEventNameValue() { return lastEventName; }
    public void clearLastEvent() { lastEventCode = 0; lastEventName = ""; }

    public Random getRng() { return rng; }

    public boolean isObstacleListEmpty() { return state.getObstacles().isEmpty(); }
    public int getObstacleListSize() { return state.getObstacles().size(); }
    public TruckaObstacle getObstacleByIndex(final int index) {
        if (index < 0 || index >= state.getObstacles().size()) return null;
        return state.getObstacles().get(index);
    }

    public int getFirstObstaclePosition() {
        if (state.getObstacles().isEmpty()) return -1;
        int min = config.getTrackLength();
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() < min) min = ob.getPosition();
        }
        return min;
    }

    public int getLastObstacleEndPosition() {
        if (state.getObstacles().isEmpty()) return -1;
        int max = -1;
        for (TruckaObstacle ob : state.getObstacles()) {
            int end = ob.getPosition() + ob.getWidth();
            if (end > max) max = end;
        }
        return max;
    }

    public static int clampTrackPosition(final int pos, final TruckaJumpaConfig config) {
        if (config == null) return Math.max(0, Math.min(pos, TRACK_LENGTH - 1));
        return Math.max(0, Math.min(pos, config.getTrackLength() - 1));
    }

    public static int clampObstacleWidth(final int width, final TruckaJumpaConfig config) {
        if (config == null) return Math.max(MIN_OBSTACLE_WIDTH, Math.min(width, MAX_OBSTACLE_WIDTH));
        return Math.max(config.getMinObstacleWidth(), Math.min(width, config.getMaxObstacleWidth()));
    }

    public int getRecommendedJumpTick() {
        int truckPos = config.getTruckPosition();
        int speed = getObstacleSpeedForLevel();
        int bestTick = -1;
        int bestDist = Integer.MAX_VALUE;
        for (TruckaObstacle ob : state.getObstacles()) {
            if (ob.getPosition() > truckPos) {
                int ticksToReach = (ob.getPosition() - truckPos) / speed;
                int dist = Math.abs(ticksToReach - config.getJumpDurationTicks() / 2);
                if (dist < bestDist) { bestDist = dist; bestTick = ticksToReach; }
            }
        }
        return bestTick;
    }

    public String getStateSummary() {
        return "TruckaJumpa L" + state.getLevel() + " S" + state.getScore() + " Lives" + state.getLives()
            + " Jump" + state.getJumpTicksLeft() + " Obs" + state.getObstacles().size()
            + (state.isGameOver() ? " GAMEOVER" : "");
    }

    public static final int TRUCKA_FLAG_JUMP_READY = 0x01;
    public static final int TRUCKA_FLAG_GAME_OVER = 0x02;
    public static final int TRUCKA_FLAG_LEVEL_COMPLETE = 0x04;
    public static final int TRUCKA_FLAG_TRUCK_IN_AIR = 0x08;

    public int getStateFlags() {
        int f = 0;
        if (canJump()) f |= TRUCKA_FLAG_JUMP_READY;
        if (state.isGameOver()) f |= TRUCKA_FLAG_GAME_OVER;
        if (state.isLevelComplete()) f |= TRUCKA_FLAG_LEVEL_COMPLETE;
        if (state.getJumpTicksLeft() > 0) f |= TRUCKA_FLAG_TRUCK_IN_AIR;
        return f;
    }

    public boolean hasFlag(final int flag) { return (getStateFlags() & flag) != 0; }
    public boolean hasJumpReadyFlag() { return hasFlag(TRUCKA_FLAG_JUMP_READY); }
    public boolean hasGameOverFlag() { return hasFlag(TRUCKA_FLAG_GAME_OVER); }
    public boolean hasLevelCompleteFlag() { return hasFlag(TRUCKA_FLAG_LEVEL_COMPLETE); }
    public boolean hasTruckInAirFlag() { return hasFlag(TRUCKA_FLAG_TRUCK_IN_AIR); }

    public static int getTruckaFlagJumpReady() { return TRUCKA_FLAG_JUMP_READY; }
    public static int getTruckaFlagGameOver() { return TRUCKA_FLAG_GAME_OVER; }
    public static int getTruckaFlagLevelComplete() { return TRUCKA_FLAG_LEVEL_COMPLETE; }
    public static int getTruckaFlagTruckInAir() { return TRUCKA_FLAG_TRUCK_IN_AIR; }

    public int getClearedCountEstimate() {
        return state.getScore() / getPointsPerClear();
    }

    public int getScoreToNextBonus() {
        return config.getPointsLevelBonus();
    }

    public boolean isEligibleForLevelBonus() {
        return state.isLevelComplete();
    }

    public int getTrackLengthForDisplay() { return config.getTrackLength(); }
    public int getTruckPositionForDisplay() { return config.getTruckPosition(); }
    public int getJumpDurationForDisplay() { return config.getJumpDurationTicks(); }
    public int getLivesForDisplay() { return state.getLives(); }
    public int getScoreForDisplay() { return state.getScore(); }
    public int getLevelForDisplay() { return state.getLevel(); }
    public boolean getGameOverForDisplay() { return state.isGameOver(); }
    public boolean getLevelCompleteForDisplay() { return state.isLevelComplete(); }
    public int getJumpTicksLeftForDisplay() { return state.getJumpTicksLeft(); }
    public int getObstacleCountForDisplay() { return state.getObstacles().size(); }
    public int getObstacleSpeedForDisplay() { return getObstacleSpeedForLevel(); }
    public int getPointsPerClearForDisplay() { return getPointsPerClear(); }
    public int getNextSpawnTickForDisplay() { return getNextSpawnTick(); }
    public String getLastEventForDisplay() { return getLastEventDescription(); }
    public int getLastEventCodeForDisplay() { return lastEventCode; }
    public float getJumpProgressForDisplay() { return getJumpProgress(); }
    public int getStateFlagsForDisplay() { return getStateFlags(); }
    public String getStateSummaryForDisplay() { return getStateSummary(); }
    public String getCurrentLevelDescriptionForDisplay() { return getCurrentLevelDescription(); }
    public String getContractIdForDisplay() { return formatContractIdShort(); }
    public String getSequelInfoForDisplay() { return getSequelInfo(); }
    public boolean getCanJumpForDisplay() { return canJump(); }
    public boolean getIsJumpingForDisplay() { return isJumping(); }
    public boolean getIsActiveForDisplay() { return isActive(); }
    public boolean getWouldCollideForDisplay() { return wouldCollideAtTruckPosition(); }
    public int getClosestObstacleForDisplay() { return getClosestObstaclePosition(); }
    public int getTicksUntilObstacleForDisplay() { return getTicksUntilNextObstacleAtTruck(); }
    public int getLeftmostObstacleForDisplay() { return getLeftmostObstaclePosition(); }
    public int getRightmostObstacleEndForDisplay() { return getRightmostObstacleEndPosition(); }
    public int getTotalObstacleWidthForDisplay() { return getTotalObstacleWidth(); }
    public int getDisplayWidthPxForDisplay() { return getDisplayWidthPx(); }
    public int getDisplayHeightPxForDisplay() { return getDisplayHeightPx(); }
    public String getPaletteTruckHexForDisplay() { return getPaletteTruckHex(); }
    public String getPaletteBarrierHexForDisplay() { return getPaletteBarrierHex(); }
    public String getPaletteTrackHexForDisplay() { return getPaletteTrackHex(); }
    public int getTruckAnimationFrameForDisplay() { return getTruckAnimationFrame(); }
    public TruckaJumpaSnapshot getSnapshotForDisplay() { return getSnapshot(); }
    public TruckaJumpaStats getSessionStatsForDisplay() { return getSessionStats(); }
    public List<TruckaHighScoreEntry> getHighScoresForDisplay() { return getHighScores(); }
    public List<ObstacleSnapshot> getObstaclesSnapshotForDisplay() { return getObstaclesSnapshot(); }
    public String toRetroGridStringForDisplay() { return toRetroGridString(); }
    public String toCompactStateForDisplay() { return toCompactState(); }
    public String getStateDigestForDisplay() { return getStateDigest(); }
    public String encodeStateForDisplay() { return encodeState(); }
    public long getStateChecksumForDisplay() { return getStateChecksum(); }
    public int getMaxPossibleScoreApproxForDisplay() { return getMaxPossibleScoreApprox(); }
    public int getRecommendedJumpTickForDisplay() { return getRecommendedJumpTick(); }
    public int getSafeJumpTicksBeforeCollisionForDisplay() { return getSafeJumpTicksBeforeCollision(); }
    public boolean getWouldJumpClearNextObstacleForDisplay() { return wouldJumpClearNextObstacle(); }
    public int getViewportStartForDisplay(final int offset) { return getViewportStart(offset); }
    public int getVisibleObstacleCountForDisplay(final int viewportStart) { return getVisibleObstacleCount(viewportStart); }
    public int getSpawnIntervalForLevelForDisplay() { return getSpawnIntervalForLevel(); }
    public boolean getShouldLevelCompleteForDisplay() { return shouldLevelComplete(); }
    public boolean getIsDefaultConfigForDisplay() { return isDefaultConfig(); }
    public boolean getIsContractIdForDisplay(final String id) { return isContractId(id); }
    public boolean getIsVersionHashForDisplay(final String hash) { return isVersionHash(hash); }
    public boolean getIsSequelToFroggetForDisplay() { return isSequelToFrogget(); }
    public String getUniverseNameForDisplay() { return getUniverseName(); }
    public boolean getValidateEncodedStateForDisplay(final String encoded) { return validateEncodedState(encoded); }
    public int getScoreFromEncodedForDisplay(final String encoded) { return getScoreFromEncoded(encoded); }
    public String getLevelDescriptionForDisplay(final int level) { return getLevelDescription(level); }
    public String getLevelDescriptionForUIForDisplay(final int level) { return getLevelDescriptionForUI(level); }
    public int getActionFromKeyForDisplay(final String key) { return actionFromKey(key); }
    public int getClampLevelForDisplay(final int level) { return clampLevel(level, config); }
    public int getClampScoreForDisplay(final int score) { return clampScore(score); }
    public int getClampTrackPositionForDisplay(final int pos) { return clampTrackPosition(pos, config); }
    public int getClampObstacleWidthForDisplay(final int width) { return clampObstacleWidth(width, config); }
    public boolean getIsValidConfigForDisplay() { return isValidConfig(config); }
    public boolean getStateEqualsForDisplay(final TruckaJumpa other) { return stateEquals(other); }
    public int getObstacleCountInRangeForDisplay(final int from, final int to) { return getObstacleCountInRange(from, to); }
    public boolean getHasObstacleInRangeForDisplay(final int from, final int to) { return hasObstacleInRange(from, to); }
    public int getObstacleAtPositionForDisplay(final int pos) { return getObstacleAtPosition(pos); }
    public int getObstaclePositionForDisplay(final int index) { return getObstaclePosition(index); }
    public int getObstacleWidthForDisplay(final int index) { return getObstacleWidth(index); }
    public TruckaCellType getCellTypeForDisplay(final int position) { return getCellType(position); }
    public boolean getIsTruckAtForDisplay(final int position) { return isTruckAt(position); }
    public boolean getHasObstacleAtForDisplay(final int position) { return hasObstacleAt(position); }
    public int getClearedCountEstimateForDisplay() { return getClearedCountEstimate(); }
    public int getScoreToNextBonusForDisplay() { return getScoreToNextBonus(); }
    public boolean getIsHighScoreEligibleForDisplay(final int minScore) { return isHighScoreEligible(minScore); }
    public float getJumpProgressForDisplayFloat() { return getJumpProgress(); }
    public int getStateFlagsValue() { return getStateFlags(); }
    public String getContractFingerprintForDisplay() { return getContractFingerprint(); }
    public String getContractInfoForDisplay() { return getContractInfo(); }

    public static final int TRUCKA_VERSION_MAJOR = 1;
    public static final int TRUCKA_VERSION_MINOR = 0;
    public static final String TRUCKA_VERSION_STRING = "1.0";
    public static String getVersionString() { return TRUCKA_VERSION_STRING; }
    public static int getVersionMajor() { return TRUCKA_VERSION_MAJOR; }
    public static int getVersionMinor() { return TRUCKA_VERSION_MINOR; }

    public int getObstacleSpeedAtLevel(final int level) {
        return config.getObstacleBaseSpeed() + (level / 2);
    }

    public int getPointsPerClearAtLevel(final int level) {
        return config.getPointsPerObstacle() + level * 5;
    }

    public static int getTrackLengthFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getTrackLength() : TRACK_LENGTH;
    }

    public static int getTruckPositionFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getTruckPosition() : TRUCK_POSITION;
    }

    public static int getJumpDurationFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getJumpDurationTicks() : JUMP_DURATION_TICKS;
    }

    public static int getLivesFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getLives() : INITIAL_LIVES;
    }

    public static int getObstacleBaseSpeedFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getObstacleBaseSpeed() : OBSTACLE_BASE_SPEED;
    }

    public static int getMaxLevelFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getMaxLevel() : MAX_LEVEL;
    }

    public static int getPointsPerObstacleFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getPointsPerObstacle() : POINTS_PER_OBSTACLE;
    }

    public static int getPointsLevelBonusFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getPointsLevelBonus() : POINTS_LEVEL_BONUS;
    }

    public static int getObstacleSpawnIntervalFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getObstacleSpawnInterval() : OBSTACLE_SPAWN_INTERVAL;
    }

    public static int getMinObstacleWidthFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getMinObstacleWidth() : MIN_OBSTACLE_WIDTH;
    }

    public static int getMaxObstacleWidthFromConfig(final TruckaJumpaConfig cfg) {
        return cfg != null ? cfg.getMaxObstacleWidth() : MAX_OBSTACLE_WIDTH;
    }

    public TruckaJumpaState getStateRef() { return state; }
    public TruckaJumpaConfig getConfigRef() { return config; }
    public void setStateRef(final TruckaJumpaState s) { if (s != null) state = s; }

    public static boolean isObstacleTypeBarrier(final int type) { return type == OBSTACLE_TYPE_BARRIER; }
    public static boolean isObstacleTypePit(final int type) { return type == OBSTACLE_TYPE_PIT; }
    public int getObstacleTypeAt(final int index) {
        TruckaObstacle ob = getObstacleByIndex(index);
        return ob != null ? ob.getType() : -1;
    }
    public int getObstaclePositionByIndex(final int index) { return getObstaclePosition(index); }
    public int getObstacleWidthByIndex(final int index) { return getObstacleWidth(index); }
    public boolean isEmpty() { return isObstacleListEmpty(); }
    public int size() { return getObstacleListSize(); }
    public TruckaJumpaSnapshot snapshot() { return getSnapshot(); }
    public void jumpWithStatsAndEvent() { jumpWithStats(); if (lastEventCode == EVT_JUMP) { eventLog.add(new TruckaJumpaEvent(lastEventCode, lastEventName, state.getTickCounter(), state.getJumpTicksLeft())); } }
    public void tickWithStatsAndEvent() { tickWithStats(); eventLog.add(new TruckaJumpaEvent(lastEventCode, lastEventName, state.getTickCounter(), state.getJumpTicksLeft())); }
    public static TruckaJumpa create() { return new TruckaJumpa(); }
    public static TruckaJumpa createWithSeedOnly(final long seed) { return new TruckaJumpa(seed); }
    public static TruckaJumpa createWithConfig(final TruckaJumpaConfig config) { return new TruckaJumpa(config, System.nanoTime()); }
    public static TruckaJumpa createWithConfigAndSeed(final TruckaJumpaConfig config, final long seed) { return new TruckaJumpa(config, seed); }
    public static TruckaJumpa createEasy() { return new TruckaJumpa(presetEasy(), 0L); }
    public static TruckaJumpa createNormal() { return new TruckaJumpa(presetNormal(), 0L); }
    public static TruckaJumpa createHard() { return new TruckaJumpa(presetHard(), 0L); }
    public static TruckaJumpa createLongTrack() { return new TruckaJumpa(presetLongTrack(), 0L); }
    public static TruckaJumpa createShortJump() { return new TruckaJumpa(presetShortJump(), 0L); }
    public void performKeyActionWithStats(final String key) { if (actionFromKey(key) == KEY_JUMP) jumpWithStats(); }
    public void performKeyActionWithEvent(final String key) { if (actionFromKey(key) == KEY_JUMP) jumpWithEventLog(); }
    public void performKeyActionWithStatsAndEvent(final String key) { if (actionFromKey(key) == KEY_JUMP) jumpWithStatsAndEvent(); }
    public int getEventLogSize() { return eventLog.size(); }
    public TruckaJumpaEvent getEventAt(final int index) { if (index < 0 || index >= eventLog.size()) return null; return eventLog.get(index); }
    public int getHighScoresSize() { return highScores.size(); }
    public TruckaHighScoreEntry getHighScoreAt(final int index) { if (index < 0 || index >= highScores.size()) return null; return highScores.get(index); }
    public int getSessionJumps() { return sessionJumps; }
    public int getSessionCleared() { return sessionCleared; }
    public int getSessionCrashes() { return sessionCrashes; }
    public void incrementSessionJumps() { sessionJumps++; }
    public void incrementSessionCleared() { sessionCleared++; }
    public void incrementSessionCrashes() { sessionCrashes++; }
    public static String getErrBoundsStatic() { return TRUCKA_ERR_BOUNDS; }
    public static String getErrCrashStatic() { return TRUCKA_ERR_CRASH; }
    public static String getErrNoLivesStatic() { return TRUCKA_ERR_NO_LIVES; }
    public static String getErrGameOverStatic() { return TRUCKA_ERR_GAME_OVER; }
    public static String getErrAlreadyJumpingStatic() { return TRUCKA_ERR_ALREADY_JUMPING; }
    public static String getEvtJumpStatic() { return TRUCKA_EVT_JUMP; }
    public static String getEvtClearedStatic() { return TRUCKA_EVT_CLEARED; }
    public static String getEvtCrashStatic() { return TRUCKA_EVT_CRASH; }
    public static String getEvtLevelUpStatic() { return TRUCKA_EVT_LEVEL_UP; }
    public static String getEvtGameOverStatic() { return TRUCKA_EVT_GAME_OVER; }
    public static String getEvtTickStatic() { return TRUCKA_EVT_TICK; }
    public String getContractIdShort() { return formatContractIdShort(); }
    public String getFingerprint() { return getContractFingerprint(); }
    public String getInfo() { return getContractInfo(); }
    public String getSequel() { return getSequelInfo(); }
    public String getUniverse() { return getUniverseName(); }
    public boolean getIsSequel() { return isSequelToFrogget(); }
    public int getFlags() { return getStateFlags(); }
    public boolean getHasJumpReady() { return hasJumpReadyFlag(); }
    public boolean getHasGameOver() { return hasGameOverFlag(); }
    public boolean getHasLevelComplete() { return hasLevelCompleteFlag(); }
    public boolean getHasTruckInAir() { return hasTruckInAirFlag(); }
    public float getJumpProgressPct() { return getJumpProgress() * 100f; }
    public int getAirTimeRemainingTicks() { return getAirTimeRemaining(); }
    public boolean getIsJumpAllowedNow() { return isJumpAllowed(); }
    public int getTotalWidth() { return getTotalObstacleWidth(); }
    public int getLeftmostPos() { return getLeftmostObstaclePosition(); }
    public int getRightmostEnd() { return getRightmostObstacleEndPosition(); }
    public int getFirstObstaclePos() { return getFirstObstaclePosition(); }
    public int getLastObstacleEnd() { return getLastObstacleEndPosition(); }
    public int getClosestObstacle() { return getClosestObstaclePosition(); }
    public int getTicksUntilObstacle() { return getTicksUntilNextObstacleAtTruck(); }
    public int getSafeJumpTicks() { return getSafeJumpTicksBeforeCollision(); }
    public boolean getWouldClear() { return wouldJumpClearNextObstacle(); }
    public int getRecommendedTick() { return getRecommendedJumpTick(); }
    public int getClearedEstimate() { return getClearedCountEstimate(); }
    public int getScoreNextBonus() { return getScoreToNextBonus(); }
    public boolean getEligibleBonus() { return isEligibleForLevelBonus(); }
    public boolean getIsEmpty() { return isObstacleListEmpty(); }
    public int getObsCount() { return getObstacleListSize(); }
    public TruckaObstacle getObs(final int index) { return getObstacleByIndex(index); }
    public int getObsPos(final int index) { return getObstaclePosition(index); }
    public int getObsWidth(final int index) { return getObstacleWidth(index); }
    public TruckaCellType getCell(final int pos) { return getCellType(pos); }
    public boolean isTruck(final int pos) { return isTruckAt(pos); }
    public boolean isObstacle(final int pos) { return hasObstacleAt(pos); }
    public String getGrid() { return toRetroGridString(); }
    public String getCompact() { return toCompactState(); }
