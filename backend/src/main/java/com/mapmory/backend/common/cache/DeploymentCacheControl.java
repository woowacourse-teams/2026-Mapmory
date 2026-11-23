package com.mapmory.backend.common.cache;

import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 배포 중 두 로컬 캐시가 함께 사용할 모드와 세대. 프로세스마다 따로 제어한다. */
@Component
public class DeploymentCacheControl {

    private static final Logger log = LoggerFactory.getLogger(DeploymentCacheControl.class);

    private volatile Snapshot snapshot;

    public DeploymentCacheControl(@Value("${deployment.cache.initial-mode:NORMAL}") Mode initialMode) {
        this.snapshot = new Snapshot(Objects.requireNonNull(initialMode), 0, Instant.now());
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    synchronized Snapshot changeMode(Mode mode) {
        Objects.requireNonNull(mode);
        Snapshot previous = snapshot;
        if (previous.mode() == mode) {
            return previous;
        }
        // 세대를 키에 포함하므로 이전 세대의 진행 중 조회가 늦게 끝나도 재사용되지 않는다.
        snapshot = new Snapshot(mode, previous.generation() + 1, Instant.now());
        log.info("Deployment cache mode changed: {} -> {}, generation={}",
                previous.mode(), mode, snapshot.generation());
        return snapshot;
    }

    public enum Mode {
        NORMAL, BYPASS
    }

    public record Snapshot(Mode mode, long generation, Instant changedAt) {
        public boolean bypass() {
            return mode == Mode.BYPASS;
        }
    }
}
