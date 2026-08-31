package service.tttool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TttoolServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void findsGmeFilesRecursivelyAndIgnoresOtherFiles() throws IOException {
        Path rootGme = Files.createFile(temporaryDirectory.resolve("root.gme"));
        Path nestedDirectory = Files.createDirectories(temporaryDirectory.resolve("nested"));
        Path nestedGme = Files.createFile(nestedDirectory.resolve("album.GME"));
        Files.createFile(nestedDirectory.resolve("notes.txt"));

        assertEquals(List.of(nestedGme, rootGme), TttoolService.findGmeFiles(temporaryDirectory));
    }

    @Test
    void createsOidTableWithTheRequiredPdfArguments() {
        Path yamlFile = Path.of("Disney", "_Frozen", "tttoolAlbum.yaml");

        assertEquals(List.of(
                "--image-format", "PDF",
                "--dpi", "1200",
                "--pixel-size", "4",
                "--code-dim", "10",
                "oid-table",
                yamlFile.toAbsolutePath().toString()),
                TttoolService.oidTableArguments(yamlFile));
    }

    @Test
    void createsOidRangeTableWithTheRequestedRangeAndOutputFile() {
        Path outputPdf = Path.of("Disney", "start-oid-table.pdf");

        assertEquals(List.of(
                "oid-table",
                "900-950",
                outputPdf.toAbsolutePath().toString()),
                TttoolService.oidRangeTableArguments(900, 950, outputPdf));
    }

    @Test
    void parsesProductIdFromTttoolInfoOutput() throws IOException {
        String output = """
                Product ID: 805
                Raw XOR value: 0x00000039
                Comment: Bobo Siebenschlaefer
                """;

        assertEquals(805, TttoolService.parseProductId(output, Path.of("name.gme")));
    }

    @Test
    void rejectsInfoOutputWithoutAProductId() {
        assertThrows(IOException.class,
                () -> TttoolService.parseProductId("Comment: Missing ID", Path.of("name.gme")));
    }

    @Test
    void productIdResultRepresentsASuccessfulLookup() {
        TttoolService.ProductIdResult result = TttoolService.ProductIdResult.success(Path.of("album.gme"), 805);

        assertTrue(result.isSuccess());
        assertEquals(805, result.productId());
        assertEquals(null, result.error());
    }

    @Test
    void productIdResultRepresentsAFailedLookup() {
        TttoolService.ProductIdResult result = TttoolService.ProductIdResult.failure(Path.of("broken.gme"),
                "Invalid GME");

        assertFalse(result.isSuccess());
        assertEquals(null, result.productId());
        assertEquals("Invalid GME", result.error());
    }
}
