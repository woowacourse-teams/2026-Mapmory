package com.mapmory.backend.sharevideo.render;

import static java.util.Comparator.reverseOrder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.mapmory.backend.sharevideo.ShareVideoStyle;
import com.mapmory.backend.sharevideo.worker.ShareVideoRenderTask;
import com.mapmory.backend.sharevideo.worker.ShareVideoWorkerProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessShareVideoRenderer implements ShareVideoRenderer {

    private static final Logger log = LoggerFactory.getLogger(ProcessShareVideoRenderer.class);

    private final ShareVideoWorkerProperties properties;

    public ProcessShareVideoRenderer(final ShareVideoWorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public RenderedShareVideo render(final ShareVideoRenderTask task) {
        Path workDirectory = null;
        boolean succeeded = false;

        try {
            workDirectory = createWorkDirectory(task.jobId());

            Path inputPath = workDirectory.resolve("snapshot.json");
            Path outputPath = workDirectory.resolve("result.mp4");

            Files.writeString(inputPath, task.snapshotJson(), StandardCharsets.UTF_8);

            Process process = startProcess(inputPath, outputPath, task.styleId());

            waitForCompletion(process, task.jobId());
            validateOutput(outputPath);

            succeeded = true;

            return new RenderedShareVideo(outputPath, workDirectory);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ShareVideoRenderException("영상 렌더링 대기가 중단됐습니다.", exception);
        } catch (IOException exception) {
            throw new ShareVideoRenderException(
                    "영상 렌더링 프로세스를 실행하지 못했습니다.",
                    exception
            );
        } finally {
            if (!succeeded && workDirectory != null) {
                deleteWorkDirectory(workDirectory);
            }
        }
    }

    @Override
    public void cleanup(final RenderedShareVideo renderedVideo) {
        deleteWorkDirectory(renderedVideo.workDirectory());
    }

    private Path createWorkDirectory(final Long jobId) throws IOException {
        Path root = properties.workDirectory()
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(root);

        return Files.createTempDirectory(
                root,
                "job-" + jobId + "-"
        );
    }

    private Process startProcess(
            final Path inputPath,
            final Path outputPath,
            final ShareVideoStyle styleId
    ) throws IOException {
        Path rendererDirectory = properties.rendererDirectory()
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(rendererDirectory)) {
            throw new ShareVideoRenderException(
                    "Video Renderer 경로를 찾을 수 없습니다: " + rendererDirectory
            );
        }

        return new ProcessBuilder(
                properties.npmCommand(),
                "run",
                "render:worker",
                "--",
                inputPath.toString(),
                outputPath.toString(),
                styleId.name()
        )
                .directory(rendererDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    private void waitForCompletion(
            final Process process,
            final Long jobId
    ) throws InterruptedException {
        boolean finished = process.waitFor(
                properties.renderTimeout().toMillis(),
                MILLISECONDS
        );

        if (!finished) {
            terminate(process);
            throw new ShareVideoRenderException(
                    "영상 렌더링 시간이 초과됐습니다. jobId=" + jobId
            );
        }

        if (process.exitValue() != 0) {
            throw new ShareVideoRenderException(
                    "영상 렌더링 프로세스가 실패했습니다. jobId=" + jobId
                            + ", exitCode=" + process.exitValue()
            );
        }
    }

    private void terminate(final Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();

        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private void validateOutput(final Path outputPath) throws IOException {
        if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0L) {
            throw new ShareVideoRenderException(
                    "렌더링 결과 파일이 생성되지 않았습니다: " + outputPath
            );
        }
    }

    private void deleteWorkDirectory(final Path workDirectory) {
        try (Stream<Path> paths = Files.walk(workDirectory)) {
            paths.sorted(reverseOrder())
                    .forEach(this::deleteFile);
        } catch (IOException exception) {
            log.warn(
                    "영상 임시 디렉터리를 정리하지 못했습니다. path={}",
                    workDirectory,
                    exception
            );
        }
    }

    private void deleteFile(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("영상 임시 파일을 삭제하지 못했습니다. path={}", path, exception);
        }
    }
}
