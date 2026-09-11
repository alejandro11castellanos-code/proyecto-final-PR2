package gt.umg.svbgua;

/**
 * Plain launcher used by the shaded fat JAR. Delegating from a non-Application
 * class avoids the "JavaFX runtime components are missing" error when the app is
 * started from an executable JAR.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
