package com.mapmory.backend.travelrecord.thumbnail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    public void checkReady() {
        int exitCode = run("--check");
        if (exitCode != 0) {
            throw new ThumbnailMakeException("thumbnail script is not ready. exitCode=" + exitCode);
        }
    }

    @Override
    public Outcome make(Path original, Path thumbnail) {
        // 프로세스는 스크립트 디렉터리에서 돌므로 상대 경로를 그대로 넘기면 엉뚱한 곳을 가리킨다.
        int exitCode = run(original.toAbsolutePath().normalize().toString(), thumbnail.toAbsolutePath().normalize().toString(),
                "--max-edge", String.valueOf(properties.maxEdge()));
        if (exitCode == EXIT_UNDECODABLE) {
            return Outcome.UNDECODABLE;
        }
        if (exitCode != 0) {
            throw new ThumbnailMakeException("thumbnail process failed. exitCode=" + exitCode);
        }
        try {
            if (!Files.isRegularFile(thumbnail) || Files.size(thumbnail) == 0L) {
                throw new ThumbnailMakeException("thumbnail process finished without output: " + thumbnail);
            }
        } catch (IOException e) {
            throw new ThumbnailMakeException("cannot read thumbnail output: " + thumbnail, e);
        }
        return Outcome.CREATED;
    }

    private int run(String... arguments) {
        Path scriptDirectory = properties.scriptDirectory().toAbsolutePath().normalize();
        Path script = scriptDirectory.resolve(SCRIPT);
        if (!Files.isRegularFile(script)) {
            throw new ThumbnailMakeException("thumbnail script not found: " + script);
        }
        List<String> command = new ArrayList<>(List.of(properties.pythonCommand(), script.toString()));
        command.addAll(List.of(arguments));
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(scriptDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            return waitFor(process);
        } catch (IOException e) {
            throw new ThumbnailMakeException("cannot run thumbnail process", e);
        } catch (InterruptedException e) {
            // 종료 중이라도 변환 프로세스를 남기지 않는다. 남으면 워커가 지운 작업 디렉터리에 계속 쓴다.
            if (process != null) {
                terminateQuietly(process);
            }
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

    private void terminateQuietly(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
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
