package com.ganzithon.homemate.repository;

import com.ganzithon.homemate.entity.User;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByAccountLoginIdValue(String loginId);

    Optional<User> findByAccountLoginIdValue(String loginId);

    List<User> findByIdIn(Collection<Long> ids);
}

