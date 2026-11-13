package br.com.droidboaoferta;

/** Classifies the actual network failure, including exceptions returned by worker futures. */
final class PropertyQueryFailure {
    static final class Verification extends Exception {
        final int attempts;
        Verification(Exception error, int attempts) { super(error); this.attempts = attempts; }
    }
    private PropertyQueryFailure() { }

    static Throwable cause(Throwable error) {
        Throwable current = error;
        for (int i = 0; i < 12 && current != null && current.getCause() != null
                && current.getCause() != current; i++) current = current.getCause();
        return current;
    }

    static boolean retryable(Throwable error) {
        Throwable root = cause(error);
        if (root instanceof java.io.IOException) return true;
        String message = root == null ? "" : String.valueOf(root.getMessage());
        return message.matches("HTTP (408|429|5[0-9]{2})");
    }

    static String detail(Throwable error) {
        Throwable root = cause(error);
        if (root == null) return "Unknown";
        String message = root.getMessage();
        String text = root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.substring(0, Math.min(text.length(), 400));
    }
}
