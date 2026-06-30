package com.jnhro1.rechatbackend.user;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSeeder implements ApplicationRunner {

    // FYI: 데모/테스트에서 join 대상으로 쓰는 고정 유저. 변경 시 이 목록만 수정.
    private static final List<String> SEED_USERNAMES = List.of("alice", "bob", "carol");

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (String username : SEED_USERNAMES) {
            if (!userRepository.existsByUsername(username)) {
                userRepository.save(User.of(username));
                created++;
            }
        }
        log.info("유저 시드 완료: 신규 {}명 / 전체 후보 {}명", created, SEED_USERNAMES.size());
    }
}
