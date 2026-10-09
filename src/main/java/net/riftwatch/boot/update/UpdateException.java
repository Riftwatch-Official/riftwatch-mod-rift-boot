package net.riftwatch.boot.update;

final class UpdateException extends Exception {
    private static final long serialVersionUID = 1L;

    UpdateException(String message) {
        super(message);
    }

    UpdateException(String message, Throwable cause) {
        super(message, cause);
    }

    static String describe(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        String message = failure.getMessage();
        String type = failure.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }
}
