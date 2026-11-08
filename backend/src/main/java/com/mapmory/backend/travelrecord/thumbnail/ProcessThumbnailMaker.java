package com.mapmory.backend.travelrecord.thumbnail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * thumbnail/make_thumbnail.py를 외부 프로세스로 실행한다. 공유 영상의 ProcessShareVideoRenderer와 같은 방식이다.
 *
 * <p>종료 코드 0은 성공, 2는 원본 내용 때문에 만들 수 없음(다시 해도 같음)이다. 그 밖의 코드, 시그널 종료(메모리 부족),
 * 시간 초과는 다시 할 수 있는 실패로 본다. 스크립트의 출력은 렌더러 로그(journald)로 흘려보낸다.
 */
public class ProcessThumbnailMaker implements ThumbnailMaker {

    private static final String SCRIPT = "make_thumbnail.py";
    private static final int EXIT_UNDECODABLE = 2;

    private final ThumbnailWorkerProperties properties;

    public ProcessThumbnailMaker(ThumbnailWorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Outcome make(Path original, Path thumbnail) {
        Path scriptDirectory = properties.scriptDirectory().toAbsolutePath().normalize();
        Path script = scriptDirectory.resolve(SCRIPT);
        if (!Files.isRegularFile(script)) {
            throw new ThumbnailMakeException("thumbnail script not found: " + script);
        }
        try {
            Process process = new ProcessBuilder(
                    properties.pythonCommand(),
                    script.toString(),
                    original.toString(),
                    thumbnail.toString(),
                    "--max-edge",
                    String.valueOf(properties.maxEdge()))
                    .directory(scriptDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            int exitCode = waitFor(process);
            if (exitCode == EXIT_UNDECODABLE) {
                return Outcome.UNDECODABLE;
            }
            if (exitCode != 0) {
                throw new ThumbnailMakeException("thumbnail process failed. exitCode=" + exitCode);
            }
            if (!Files.isRegularFile(thumbnail) || Files.size(thumbnail) == 0L) {
                throw new ThumbnailMakeException("thumbnail process finished without output: " + thumbnail);
            }
            return Outcome.CREATED;
        } catch (IOException e) {
            throw new ThumbnailMakeException("cannot run thumbnail process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ThumbnailMakeException("interrupted while waiting for thumbnail process", e);
        }
    }

    private int waitFor(Process process) throws InterruptedException {
        if (!process.waitFor(properties.processTimeout().toMillis(), MILLISECONDS)) {
            terminate(process);
            throw new ThumbnailMakeException("thumbnail process timed out after " + properties.processTimeout());
        }
        return process.exitValue();
    }

    private void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(5, SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, SECONDS);
        }
    }
}
