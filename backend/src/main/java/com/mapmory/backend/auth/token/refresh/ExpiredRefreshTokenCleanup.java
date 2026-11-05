package com.mapmory.backend.auth.token.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 만료되고 7일이 지난 refresh 토큰과 토큰이 남지 않은 세션을 지운다. (#454, ADR 0011)
 *
 * <p>만료된 토큰은 어차피 쓸 수 없다. 지우면 없는 토큰과 같아져 다시 와도 401로 끝나고, 세션을 끊지는 않는다.
 * 재사용 감지와 게스트 응답 유실 재발급은 만료 전 토큰으로 일어나므로 영향이 없다. 응답 유실은 살아 있는 후속 토큰이 있어야 하고,
 * 후속 토큰은 항상 이전 토큰보다 늦게 만료된다. 7일은 로그를 볼 때 토큰 행을 찾을 수 있게 남기는 여유다.
 *
 * <p>나눠서 지운다. 묶음마다 따로 커밋되므로 회전의 행 잠금과 오래 겹치지 않고, 끊겨도 다음 날 이어서 지운다.
 * 세션은 토큰과 같은 트랜잭션에서 만들어지므로 토큰 없이 보이는 세션은 다시 쓰이지 않는다.
 */
@Component
public class ExpiredRefreshTokenCleanup {

    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int BATCH_SIZE = 1_000;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFamilyRepository familyRepository;
    private final Clock clock;

    public ExpiredRefreshTokenCleanup(RefreshTokenRepository refreshTokenRepository,
                                      RefreshTokenFamilyRepository familyRepository,
                                      Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.familyRepository = familyRepository;
        this.clock = clock;
    }

    /**
     * @return 지운 토큰 수
     */
    public int run() {
        return run(BATCH_SIZE);
    }

    int run(int batchSize) {
        int removedTokens = removeExpiredTokens(batchSize);
        removeEmptyFamilies(batchSize);
        return removedTokens;
    }

    private int removeExpiredTokens(int batchSize) {
        LocalDateTime before = LocalDateTime.now(clock).minus(RETENTION);
        int removed = 0;
        int batch;
        do {
            batch = refreshTokenRepository.deleteExpiredBefore(before, batchSize);
            removed += batch;
        } while (batch == batchSize);
        return removed;
    }

    private void removeEmptyFamilies(int batchSize) {
        long after = 0;
        List<Long> familyIds;
        do {
            familyIds = familyRepository.findEmptyIdsAfter(after, batchSize);
            if (!familyIds.isEmpty()) {
                familyRepository.deleteAllByIdIn(familyIds);
                after = familyIds.getLast();
            }
        } while (familyIds.size() == batchSize);
    }
}
