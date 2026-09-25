package service.audio;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioMergeServiceTest {

    @Test
    void groupsFilesUntilEachGroupReachesTheMinimumDuration() {
        List<AudioMergeService.AudioFileDuration> files = List.of(
                new AudioMergeService.AudioFileDuration(new File("01.ogg"), 120),
                new AudioMergeService.AudioFileDuration(new File("02.ogg"), 180),
                new AudioMergeService.AudioFileDuration(new File("03.ogg"), 180),
                new AudioMergeService.AudioFileDuration(new File("04.ogg"), 120));

        List<List<AudioMergeService.AudioFileDuration>> groups = AudioMergeService.groupByMinimumDuration(files, 300);

        assertEquals(List.of("01.ogg", "02.ogg"), groups.get(0).stream().map(item -> item.file().getName()).toList());
        assertEquals(List.of("03.ogg", "04.ogg"), groups.get(1).stream().map(item -> item.file().getName()).toList());
    }

    @Test
    void retainsTheFinalShortGroup() {
        List<AudioMergeService.AudioFileDuration> files = List.of(
                new AudioMergeService.AudioFileDuration(new File("01.ogg"), 300),
                new AudioMergeService.AudioFileDuration(new File("02.ogg"), 10));

        List<List<AudioMergeService.AudioFileDuration>> groups = AudioMergeService.groupByMinimumDuration(files, 300);

        assertEquals(2, groups.size());
        assertEquals("02.ogg", groups.get(1).getFirst().file().getName());
    }

    @Test
    void namesTracksWithTheAlbumNameWhenOneIsProvided() {
        assertEquals("Album Name - 001.ogg",
                AudioMergeService.outputFileName("Album Name", 1, AudioMergeService.OutputFormat.OGG));
        assertEquals("track-002.mp3",
                AudioMergeService.outputFileName("", 2, AudioMergeService.OutputFormat.MP3));
    }
}
