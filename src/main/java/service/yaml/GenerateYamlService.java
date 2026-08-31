package service.yaml;

import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Generates the tttool YAML files for an album directory.
 *
 * <p>The supplied directory must contain an {@code audio} subdirectory with
 * MP3 and/or OGG files. Two files are created in the supplied directory:
 * {@code <album-name>.yaml} and {@code <album-name>.codes.yaml}.</p>
 */
public class GenerateYamlService {

    /** First OID reserved for generated chapter script codes. */
    public static final int FIRST_SCRIPT_CODE = 2055;
    /** Number of generated chapter script-code tracks. */
    public static final int SCRIPT_CODE_TRACKS = 30;

    /** Runtime settings for the generated chapter script codes. */
    public record ScriptCodeSettings(int firstScriptCode, int scriptCodeTracks) {
        public ScriptCodeSettings {
            if (firstScriptCode < 0) {
                throw new IllegalArgumentException("Chapter OIDs must start at 0 or greater.");
            }
            if (scriptCodeTracks < 1) {
                throw new IllegalArgumentException("Number of chapters must be at least 1.");
            }
            if ((long) firstScriptCode + scriptCodeTracks - 1 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("The chapter OID range exceeds " + Integer.MAX_VALUE + ".");
            }
        }
    }

    /**
     * Generates the tttool YAML and script-code YAML files.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, File albumDirectory) throws IOException {
        return generate(productId, albumDirectory.toPath());
    }

    /**
     * Generates YAML files using the supplied title instead of deriving it from
     * the album directory name.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @param title the title used for generated filenames and YAML metadata
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, File albumDirectory, String title) throws IOException {
        return generate(productId, albumDirectory.toPath(), title, defaultScriptCodeSettings());
    }

    /**
     * Generates YAML files with custom chapter script-code settings.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @param title the title used for generated filenames and YAML metadata
     * @param scriptCodeSettings chapter OID start and number of chapter codes
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, File albumDirectory, String title,
            ScriptCodeSettings scriptCodeSettings) throws IOException {
        return generate(productId, albumDirectory.toPath(), title, scriptCodeSettings);
    }

    /**
     * Generates the tttool YAML and script-code YAML files.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, Path albumDirectory) throws IOException {
        return generate(productId, albumDirectory, null, defaultScriptCodeSettings());
    }

    /**
     * Generates YAML files using the supplied title instead of deriving it from
     * the album directory name.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @param title the title used for generated filenames and YAML metadata; blank uses the directory name
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, Path albumDirectory, String title) throws IOException {
        return generate(productId, albumDirectory, title, defaultScriptCodeSettings());
    }

    /**
     * Generates YAML files using custom chapter script-code settings.
     *
     * @param productId the product ID written to the main YAML file
     * @param albumDirectory directory that contains the {@code audio} directory
     * @param title the title used for generated filenames and YAML metadata; blank uses the directory name
     * @param scriptCodeSettings chapter OID start and number of chapter codes
     * @return the paths of the generated main YAML and code YAML files
     * @throws IOException if the audio directory cannot be read or a file cannot be written
     */
    public GeneratedYamlFiles generate(int productId, Path albumDirectory, String title,
            ScriptCodeSettings scriptCodeSettings) throws IOException {
        if (scriptCodeSettings == null) {
            throw new IllegalArgumentException("Chapter script-code settings are required.");
        }
        Path normalizedDirectory = albumDirectory.toAbsolutePath().normalize();
        Path audioDirectory = normalizedDirectory.resolve("audio");

        if (!Files.isDirectory(audioDirectory)) {
            throw new IOException("Audio directory does not exist: " + audioDirectory);
        }

        List<Path> audioFiles = findAudioFiles(audioDirectory);
        int trackCount = audioFiles.size();
        int digits = String.valueOf(trackCount).length();
        String resolvedTitle = title == null || title.isBlank()
                ? albumTitle(normalizedDirectory.getFileName().toString())
                : title.trim();

        Path yamlFile = normalizedDirectory.resolve(resolvedTitle + ".yaml");
        Path codesFile = normalizedDirectory.resolve(resolvedTitle + ".codes.yaml");

        Files.write(yamlFile, generateTttoolScript(productId, resolvedTitle, trackCount, digits,
                scriptCodeSettings.scriptCodeTracks()), StandardCharsets.UTF_8);
        Files.write(codesFile, generateScriptCodes(digits, scriptCodeSettings), StandardCharsets.UTF_8);

        return new GeneratedYamlFiles(yamlFile, codesFile);
    }

    private List<Path> findAudioFiles(Path audioDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(audioDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedAudioFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private boolean isSupportedAudioFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".ogg");
    }

    private String albumTitle(String directoryName) {
        return directoryName.endsWith("_album")
                ? directoryName.substring(0, directoryName.length() - "_album".length())
                : directoryName;
    }

    private static ScriptCodeSettings defaultScriptCodeSettings() {
        return new ScriptCodeSettings(FIRST_SCRIPT_CODE, SCRIPT_CODE_TRACKS);
    }

    private List<String> generateTttoolScript(int productId, String title, int trackCount, int digits,
            int scriptCodeTracks) {
        List<String> lines = new ArrayList<>();
        lines.add("product-id: " + productId);
        lines.add("comment: " + title);
        lines.add("gme-lang: GERMAN");
        lines.add("media-path: audio/%s");
        lines.add("init: $current:=" + formatTrack(1, digits));

        List<String> welcomeTracks = new ArrayList<>();
        for (int track = 1; track <= trackCount; track++) {
            welcomeTracks.add(formatTrack(track, digits));
        }
        lines.add("welcome: " + String.join(", ", welcomeTracks));

        lines.add("scripts:");
        addPlayScript(lines, trackCount, digits);
        addNextScript(lines, trackCount, digits);
        addPreviousScript(lines, trackCount, digits);
        lines.add("  stop:");
        lines.add("  - C C");
        addTrackBlocks(lines, trackCount, digits, scriptCodeTracks);
        return lines;
    }

    private void addPlayScript(List<String> lines, int trackCount, int digits) {
        lines.add("  play:");
        for (int track = 1; track <= trackCount; track++) {
            String current = formatTrack(track, digits);
            if (track < trackCount) {
                String next = formatTrack(track + 1, digits);
                lines.add("  - $current==" + current + "? P(" + current + ") J(t" + next + ")");
            } else {
                lines.add("  - $current==" + current + "? P(" + current + ") C");
            }
        }
    }

    private void addNextScript(List<String> lines, int trackCount, int digits) {
        lines.add("  next:");
        for (int track = 1; track <= trackCount; track++) {
            String current = formatTrack(track, digits);
            if (track < trackCount) {
                String next = formatTrack(track + 1, digits);
                lines.add("  - $current==" + current + "? $current:=" + next + " P(" + next + ") J(t" + next + ")");
            } else {
                lines.add("  - $current==" + current + "? $current:=" + current + " P(" + current + ") C");
            }
        }
    }

    private void addPreviousScript(List<String> lines, int trackCount, int digits) {
        lines.add("  prev:");
        for (int track = 2; track <= trackCount; track++) {
            String current = formatTrack(track, digits);
            String previous = formatTrack(track - 1, digits);
            lines.add("  - $current==" + current + "? $current:=" + previous + " P(" + previous + ") J(t" + current + ")");
        }
    }

    private void addTrackBlocks(List<String> lines, int trackCount, int digits, int scriptCodeTracks) {
        int maximumTrack = Math.max(scriptCodeTracks, trackCount);
        for (int track = 1; track <= maximumTrack; track++) {
            String current = formatTrack(track, digits);
            lines.add("  t" + current + ":");
            if (track < trackCount) {
                String next = formatTrack(track + 1, digits);
                lines.add("  - $current:=" + current + " P(" + current + ") J(t" + next + ")");
            } else if (track > trackCount) {
                String lastTrack = formatTrack(trackCount, digits);
                lines.add("  - $current:=" + lastTrack + " P(" + lastTrack + ") C");
            } else {
                lines.add("  - $current:=" + current + " P(" + current + ") C");
            }
        }
    }

    private List<String> generateScriptCodes(int digits, ScriptCodeSettings scriptCodeSettings) {
        List<String> lines = new ArrayList<>();
        lines.add("scriptcodes:");
        lines.add("  play: 2051");
        lines.add("  next: 2052");
        lines.add("  prev: 2053");
        lines.add("  stop: 2054");
        for (int track = 1; track <= scriptCodeSettings.scriptCodeTracks(); track++) {
            lines.add("  t" + formatTrack(track, digits) + ": "
                    + (scriptCodeSettings.firstScriptCode() + track - 1));
        }
        return lines;
    }

    private String formatTrack(int track, int digits) {
        return String.format("%0" + digits + "d", track);
    }

    public record GeneratedYamlFiles(Path yamlFile, Path codesFile) {
    }
}
