package service.audio;

import tiptoieditor.ui.WorkflowTaskManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Combines ordered audio files into tracks with a requested minimum duration.
 */
public class AudioMergeService {

    private final Consumer<String> logger;
    private final WorkflowTaskManager taskManager;

    public AudioMergeService(Consumer<String> logger, WorkflowTaskManager taskManager) {
        this.logger = logger;
        this.taskManager = taskManager;
    }

    /** Uses MP3 as the default output format. */
    public MergeResult mergeAudioFiles(File sourceFolder, double minimumMinutes) throws IOException {
        return mergeAudioFiles(sourceFolder, minimumMinutes, OutputFormat.MP3, null);
    }

    /**
     * Writes the combined files to {@code longer tracks} in {@code sourceFolder}.
     * The final group is retained even when it is shorter than the requested
     * length.
     */
    public MergeResult mergeAudioFiles(File sourceFolder, double minimumMinutes, OutputFormat outputFormat)
            throws IOException {
        return mergeAudioFiles(sourceFolder, minimumMinutes, outputFormat, null);
    }

    /**
     * Uses the given album name as the combined-track filename prefix when
     * provided.
     */
    public MergeResult mergeAudioFiles(File sourceFolder, double minimumMinutes, OutputFormat outputFormat,
            String albumName) throws IOException {
        if (!sourceFolder.isDirectory()) {
            throw new IllegalArgumentException("The selected audio folder does not exist: " + sourceFolder);
        }
        if (!Double.isFinite(minimumMinutes) || minimumMinutes <= 0) {
            throw new IllegalArgumentException("Minimum length must be greater than zero.");
        }
        if (outputFormat == null) {
            throw new IllegalArgumentException("An output format must be selected.");
        }

        List<File> audioFiles = findAudioFiles(sourceFolder);
        if (audioFiles.isEmpty()) {
            throw new IllegalArgumentException("The selected folder contains no supported audio files.");
        }

        List<AudioFileDuration> filesWithDurations = new ArrayList<>();
        for (File audioFile : audioFiles) {
            checkInterrupted();
            double duration = readDurationSeconds(audioFile);
            logger.accept("Found " + audioFile.getName() + " (" + formatMinutes(duration) + ").");
            filesWithDurations.add(new AudioFileDuration(audioFile, duration));
        }

        List<List<AudioFileDuration>> groups = groupByMinimumDuration(filesWithDurations,
                minimumMinutes * 60);
        File targetFolder = new File(sourceFolder, "longer tracks");
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            throw new IOException("Could not create output folder: " + targetFolder);
        }

        for (int index = 0; index < groups.size(); index++) {
            checkInterrupted();
            List<AudioFileDuration> group = groups.get(index);
            File outputFile = new File(targetFolder, outputFileName(albumName, index + 1, outputFormat));
            logger.accept("Merging " + group.size() + " file(s) into " + outputFile.getName() + ".");
            mergeGroup(group, outputFile, outputFormat);
        }

        return new MergeResult(audioFiles.size(), groups.size(), targetFolder);
    }

    static List<List<AudioFileDuration>> groupByMinimumDuration(List<AudioFileDuration> files,
            double minimumSeconds) {
        List<List<AudioFileDuration>> groups = new ArrayList<>();
        List<AudioFileDuration> currentGroup = new ArrayList<>();
        double currentDuration = 0;

        for (AudioFileDuration file : files) {
            currentGroup.add(file);
            currentDuration += file.durationSeconds();
            if (currentDuration >= minimumSeconds) {
                groups.add(List.copyOf(currentGroup));
                currentGroup.clear();
                currentDuration = 0;
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(List.copyOf(currentGroup));
        }
        return List.copyOf(groups);
    }

    static String outputFileName(String albumName, int trackNumber, OutputFormat outputFormat) {
        if (albumName == null || albumName.isBlank()) {
            return String.format("track-%03d%s", trackNumber, outputFormat.extension());
        }
        return String.format("%s - %03d%s", albumName.trim(), trackNumber, outputFormat.extension());
    }

    private List<File> findAudioFiles(File sourceFolder) throws IOException {
        try (Stream<Path> paths = Files.list(sourceFolder.toPath())) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(this::isSupportedAudioFile)
                    .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    private boolean isSupportedAudioFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".m4a")
                || name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".opus")
                || name.endsWith(".wav");
    }

    private double readDurationSeconds(File file) throws IOException {
        Process process = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", file.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        register(process);
        try {
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }
            int exitCode = waitFor(process);
            if (exitCode != 0 || output == null) {
                throw new IOException("Could not determine duration of " + file.getName());
            }
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Could not read duration of " + file.getName(), e);
        } finally {
            unregister(process);
        }
    }

    private void mergeGroup(List<AudioFileDuration> group, File outputFile, OutputFormat outputFormat)
            throws IOException {
        List<String> command = new ArrayList<>(List.of("ffmpeg", "-y"));
        for (AudioFileDuration file : group) {
            command.add("-i");
            command.add(file.file().getAbsolutePath());
        }

        if (group.size() == 1) {
            command.add("-map");
            command.add("0:a:0");
        } else {
            String filterInputs = "[0:a]".repeat(group.size());
            String filter = filterInputs + "concat=n=" + group.size() + ":v=0:a=1[audio]";
            command.add("-filter_complex");
            command.add(filter);
            command.add("-map");
            command.add("[audio]");
        }
        command.addAll(List.of("-vn", "-ac", "1", "-ar", "22050", "-c:a", outputFormat.codec(),
                outputFile.getAbsolutePath()));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        register(process);
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    checkInterrupted();
                }
            }
            if (waitFor(process) != 0) {
                throw new IOException("Could not merge audio into " + outputFile.getName());
            }
        } finally {
            unregister(process);
        }
    }

    private int waitFor(Process process) throws IOException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Audio merging cancelled.");
        }
    }

    private void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Audio merging cancelled.");
        }
    }

    private static String formatMinutes(double seconds) {
        return String.format(Locale.ROOT, "%.2f min", seconds / 60);
    }

    private void register(Process process) {
        taskManager.register(process);
    }

    private void unregister(Process process) {
        taskManager.unregister(process);
    }

    record AudioFileDuration(File file, double durationSeconds) {
    }

    /** Output encodings offered by the audio-combining UI. */
    public enum OutputFormat {
        MP3(".mp3", "libmp3lame"),
        OGG(".ogg", "libvorbis"),
        WAV(".wav", "pcm_s16le");

        private final String extension;
        private final String codec;

        OutputFormat(String extension, String codec) {
            this.extension = extension;
            this.codec = codec;
        }

        String extension() {
            return extension;
        }

        String codec() {
            return codec;
        }
    }

    /** Summary of a completed merge operation. */
    public record MergeResult(int originalTrackCount, int createdTrackCount, File outputFolder) {
    }
}
