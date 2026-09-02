package com.mapmory.backend.waitlist;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaunchWaitlistService {

    private final LaunchWaitlistRepository repository;
    private final Clock clock;

    @Autowired
    public LaunchWaitlistService(LaunchWaitlistRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LaunchWaitlistService(LaunchWaitlistRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public LaunchWaitlistStatus subscribe(String rawEmail) {
        Email email = Email.from(rawEmail);
        if (repository.existsByEmail(email.value())) {
            return LaunchWaitlistStatus.ALREADY_SUBSCRIBED;
        }

        try {
            repository.saveAndFlush(LaunchWaitlistEntry.of(email, LocalDateTime.now(clock)));
            return LaunchWaitlistStatus.SUBSCRIBED;
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 주소가 들어온 경우에도 한 번만 저장하고 성공으로 취급한다.
            return LaunchWaitlistStatus.ALREADY_SUBSCRIBED;
        }
    }
}
