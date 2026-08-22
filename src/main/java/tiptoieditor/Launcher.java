package tiptoieditor;

/**
 * Native-package entry point. Keeping this class separate from the JavaFX
 * Application subclass prevents the Java launcher from trying to bootstrap
 * JavaFX before the packaged class path is configured.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
