package service.tttool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tiptoieditor.ui.WorkflowTaskManager;

public class TttoolService {

    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("(?m)^Product ID:\\s*(\\d+)\\s*$");
    private final WorkflowTaskManager taskManager;

    public TttoolService() {
        this(null);
    }

    public TttoolService(WorkflowTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public String assemble(Path yamlFile) throws IOException, InterruptedException {
        return runTttool("assemble", yamlFile);
    }

    /**
     * Creates the printable OID table PDF for an album YAML file.
     */
    public String createOidTable(Path yamlFile) throws IOException, InterruptedException {
        return createOidTable(yamlFile, OidTableSettings.DEFAULT);
    }

    /** Creates a printable OID table PDF using the supplied PDF rendering settings. */
    public String createOidTable(Path yamlFile, OidTableSettings settings) throws IOException, InterruptedException {
        return runTttool(oidTableArguments(yamlFile, settings));
    }

    /** Creates a printable PDF containing start codes for an inclusive OID range. */
    public String createOidRangeTable(int startOid, int endOid, Path outputPdf)
            throws IOException, InterruptedException {
        return createOidRangeTable(startOid, endOid, outputPdf, OidTableSettings.DEFAULT);
    }

    /** Creates a start-code range PDF using the supplied PDF rendering settings. */
    public String createOidRangeTable(int startOid, int endOid, Path outputPdf, OidTableSettings settings)
            throws IOException, InterruptedException {
        return runTttool(oidRangeTableArguments(startOid, endOid, outputPdf, settings));
    }

    static List<String> oidTableArguments(Path yamlFile) {
        return oidTableArguments(yamlFile, OidTableSettings.DEFAULT);
    }

    static List<String> oidTableArguments(Path yamlFile, OidTableSettings settings) {
        return List.of(
                "--image-format", "PDF",
                "--dpi", Integer.toString(settings.dpi()),
                "--pixel-size", Integer.toString(settings.pixelSize()),
                "--code-dim", Integer.toString(settings.codeDim()),
                "oid-table",
                yamlFile.toAbsolutePath().toString());
    }

    static List<String> oidRangeTableArguments(int startOid, int endOid, Path outputPdf) {
        return oidRangeTableArguments(startOid, endOid, outputPdf, OidTableSettings.DEFAULT);
    }

    static List<String> oidRangeTableArguments(int startOid, int endOid, Path outputPdf, OidTableSettings settings) {
        return List.of(
                "--image-format", "PDF",
                "--dpi", Integer.toString(settings.dpi()),
                "--pixel-size", Integer.toString(settings.pixelSize()),
                "--code-dim", Integer.toString(settings.codeDim()),
                "oid-table",
                startOid + "-" + endOid,
                outputPdf.toAbsolutePath().toString());
    }

    /**
     * Reads the product ID reported by {@code tttool info} for one GME file.
     */
    public int getProductId(Path gmeFile) throws IOException, InterruptedException {
        String output = runTttool("info", gmeFile);
        return parseProductId(output, gmeFile);
    }

    static int parseProductId(String output, Path gmeFile) throws IOException {
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new IOException("tttool did not report a Product ID for " + gmeFile);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Finds GME files below {@code directory}, including subdirectories, and reads their product IDs.
     * A problem with one file is returned with that file instead of stopping the entire scan.
     */
    public List<ProductIdResult> listProductIds(Path directory) throws IOException, InterruptedException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Directory does not exist: " + directory);
        }

        List<Path> gmeFiles = findGmeFiles(directory);

        List<ProductIdResult> results = new ArrayList<>();
        for (Path gmeFile : gmeFiles) {
            try {
                results.add(ProductIdResult.success(gmeFile, getProductId(gmeFile)));
            } catch (IOException | RuntimeException e) {
                results.add(ProductIdResult.failure(gmeFile, e.getMessage()));
            }
        }
        return results;
    }

    static List<Path> findGmeFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(TttoolService::isGmeFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
        }
    }

    private static boolean isGmeFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(".gme");
    }

    private String runTttool(String command, Path inputFile) throws IOException, InterruptedException {
        return runTttool(List.of(command, inputFile.toAbsolutePath().toString()));
    }

    private String runTttool(List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolveTttoolPath().toString());
        command.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        // Combine stdout and stderr
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        if (taskManager != null) {
            taskManager.register(process);
        }

        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } finally {
            if (taskManager != null) {
                taskManager.unregister(process);
            }
        }

        if (exitCode != 0) {
            throw new RuntimeException(
                    "TTTOOL exited with code " + exitCode +
                            System.lineSeparator() +
                            output);
        }

        return output.toString();
    }

    /** Resolves tttool from the packaged application, with a development-folder fallback. */
    static Path resolveTttoolPath() throws IOException {
        String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "tttool.exe"
                : "tttool";

        Path bundledTool = bundledApplicationDirectory()
                .map(directory -> directory.resolve("tools").resolve(executableName))
                .orElse(null);
        if (bundledTool != null && Files.isRegularFile(bundledTool)) {
            return bundledTool.toAbsolutePath();
        }

        Path developmentTool = Path.of("tools", executableName).toAbsolutePath();
        if (Files.isRegularFile(developmentTool)) {
            return developmentTool;
        }

        throw new IOException("Bundled tttool executable was not found. Expected: "
                + (bundledTool == null ? developmentTool : bundledTool));
    }

    private static java.util.Optional<Path> bundledApplicationDirectory() {
        try {
            Path codeLocation = Path.of(TttoolService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isRegularFile(codeLocation)
                    ? java.util.Optional.ofNullable(codeLocation.getParent())
                    : java.util.Optional.empty();
        } catch (URISyntaxException | SecurityException e) {
            return java.util.Optional.empty();
        }
    }

    public record ProductIdResult(Path gmeFile, Integer productId, String error) {

        public static ProductIdResult success(Path gmeFile, int productId) {
            return new ProductIdResult(gmeFile, productId, null);
        }

        public static ProductIdResult failure(Path gmeFile, String error) {
            return new ProductIdResult(gmeFile, null, error == null ? "Unknown error" : error);
        }

        public boolean isSuccess() {
            return productId != null;
        }
    }

    /** PDF rendering options for {@code tttool oid-table}. */
    public record OidTableSettings(int dpi, int pixelSize, int codeDim) {
        public static final OidTableSettings DEFAULT = new OidTableSettings(1200, 4, 10);

        public OidTableSettings {
            if (dpi <= 0 || pixelSize <= 0 || codeDim <= 0) {
                throw new IllegalArgumentException("OID table PDF settings must be greater than zero.");
            }
        }
    }
}
