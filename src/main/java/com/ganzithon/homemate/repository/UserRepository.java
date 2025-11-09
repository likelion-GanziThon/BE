package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByAccountLoginIdValue(String loginId);

    Optional<User> findByAccountLoginIdValue(String loginId);
}

