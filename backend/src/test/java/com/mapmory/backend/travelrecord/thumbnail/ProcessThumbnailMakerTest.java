package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void 준비_확인은_스크립트가_있고_패키지를_불러올_수_있어야_통과한다() throws IOException {
        // /bin/sh로 흉내 내므로 "-c <python 코드>"가 sh 명령으로 실행된다. exit 0이면 패키지가 있다고 본다.
        assertThatCode(() -> makerRunning("exit 0").checkReady()).doesNotThrowAnyException();

        ProcessThumbnailMaker missingScript = new ProcessThumbnailMaker(
                ThumbnailWorkerPropertiesFixture.with(directory.resolve("missing"), "/bin/sh", Duration.ofSeconds(5)));
        assertThatThrownBy(missingScript::checkReady).isInstanceOf(ThumbnailMakeException.class);

        ProcessThumbnailMaker missingPython = new ProcessThumbnailMaker(
                ThumbnailWorkerPropertiesFixture.with(directory.resolve("scripts"), "/no/such/python", Duration.ofSeconds(5)));
        assertThatThrownBy(missingPython::checkReady).isInstanceOf(ThumbnailMakeException.class);
    }

    @Test
    void 상대_경로도_호출한_쪽의_작업_디렉터리_기준으로_넘긴다() throws IOException {
        ProcessThumbnailMaker maker = makerRunning("printf '%s ' \"$1\" \"$2\" > \"$2\"");
        Path relative = Path.of("").toAbsolutePath().relativize(output());

        maker.make(Path.of("").toAbsolutePath().relativize(original()), relative);

        assertThat(Files.readString(output())).isEqualTo(original() + " " + output() + " ");
    }

    // 종료 중 인터럽트되면 변환 프로세스를 남기지 않는다. 남으면 워커가 지운 작업 디렉터리에 계속 쓴다.
    @Test
    void 기다리다_인터럽트되면_프로세스를_끝낸다() throws Exception {
        Path pidFile = directory.resolve("pid");
        ProcessThumbnailMaker maker = makerRunning("echo $$ > \"" + pidFile + "\"; sleep 30");
        Thread thread = new Thread(() -> {
            try {
                maker.make(original(), output());
            } catch (ThumbnailMakeException ignored) {
            }
        });
        thread.start();
        while (!Files.exists(pidFile) || Files.readString(pidFile).isBlank()) {
            Thread.sleep(20);
        }
        long pid = Long.parseLong(Files.readString(pidFile).strip());

        thread.interrupt();
        thread.join(10_000);

        assertThat(thread.isAlive()).isFalse();
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
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
