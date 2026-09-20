package dev.roam;

/** Turns a length of time into short text such as "1h 5m" or "30s". */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String format(long millis) {
        long total = Math.max(0, (millis + 999) / 1000);
        long days = total / 86400;
        long hours = (total % 86400) / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append("d ");
        if (hours > 0) text.append(hours).append("h ");
        if (minutes > 0) text.append(minutes).append("m ");
        if (seconds > 0 || text.isEmpty()) text.append(seconds).append("s");
        return text.toString().trim();
    }
}
