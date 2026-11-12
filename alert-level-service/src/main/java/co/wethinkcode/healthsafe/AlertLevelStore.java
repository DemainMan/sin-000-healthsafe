package co.wethinkcode.healthsafe;

import java.util.concurrent.atomic.AtomicInteger;

public final class AlertLevelStore {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private final AtomicInteger level = new AtomicInteger(0);

    public int current() {
        return level.get();
    }

    public void set(int value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("alert level must be between 0 and 8");
        }
        level.set(value);
    }

    public static boolean isValid(int value) {
        return value >= MIN_LEVEL && value <= MAX_LEVEL;
    }
}
