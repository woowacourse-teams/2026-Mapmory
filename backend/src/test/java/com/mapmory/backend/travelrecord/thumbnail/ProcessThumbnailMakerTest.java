package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.travelrecord.thumbnail.ThumbnailMaker.Outcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실제 Python 대신 /bin/sh로 가짜 make_thumbnail.py를 실행해 종료 코드와 시간 초과를 흉내 낸다.
 */
class ProcessThumbnailMakerTest {

    @TempDir
    Path directory;

    @Test
    void 성공하면_만든_것으로_본다() throws IOException {
        ProcessThumbnailMaker maker = makerRunning("printf webp > \"$2\"");

        assertThat(maker.make(original(), output())).isEqualTo(Outcome.CREATED);
        assertThat(Files.readString(output())).isEqualTo("webp");
    }

    @Test
    void 원본_경로_출력_경로_긴_변을_넘긴다() throws IOException {
        ProcessThumbnailMaker maker = makerRunning("printf '%s ' \"$@\" > \"$2\"");

        maker.make(original(), output());

        assertThat(Files.readString(output())).isEqualTo(original() + " " + output() + " --max-edge 1080 ");
    }

    @Test
    void 종료_코드_2는_원본_내용_때문에_만들_수_없는_것이다() throws IOException {
        assertThat(makerRunning("exit 2").make(original(), output())).isEqualTo(Outcome.UNDECODABLE);
    }

    @Test
    void 그_밖의_종료_코드는_다시_할_수_있는_실패다() throws IOException {
        assertThatThrownBy(() -> makerRunning("exit 1").make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class)
                .hasMessageContaining("exitCode=1");
        // 메모리 부족으로 죽으면 시그널 종료(137)가 된다.
        assertThatThrownBy(() -> makerRunning("kill -9 $$").make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class);
    }

    @Test
    void 성공했는데_출력이_없거나_비었으면_실패다() throws IOException {
        assertThatThrownBy(() -> makerRunning("exit 0").make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class);
        assertThatThrownBy(() -> makerRunning(": > \"$2\"").make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class);
    }

    @Test
    void 시간을_넘기면_프로세스를_끝내고_실패한다() throws IOException {
        ProcessThumbnailMaker maker = makerRunning("sleep 30", Duration.ofMillis(300));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> maker.make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class)
                .hasMessageContaining("timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void 스크립트가_없으면_실패한다() {
        ProcessThumbnailMaker maker = new ProcessThumbnailMaker(
                ThumbnailWorkerPropertiesFixture.with(directory.resolve("missing"), "/bin/sh", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> maker.make(original(), output()))
                .isInstanceOf(ThumbnailMakeException.class);
    }

    private ProcessThumbnailMaker makerRunning(String shell) throws IOException {
        return makerRunning(shell, Duration.ofSeconds(10));
    }

    private ProcessThumbnailMaker makerRunning(String shell, Duration timeout) throws IOException {
        Path scripts = Files.createDirectories(directory.resolve("scripts"));
        Files.writeString(scripts.resolve("make_thumbnail.py"), shell + "\n");
        return new ProcessThumbnailMaker(ThumbnailWorkerPropertiesFixture.with(scripts, "/bin/sh", timeout));
    }

    private Path original() {
        return directory.resolve("original.heic");
    }

    private Path output() {
        return directory.resolve("thumbnail.webp");
    }
}
