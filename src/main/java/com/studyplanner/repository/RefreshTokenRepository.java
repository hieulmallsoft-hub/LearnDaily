package com.studyplanner.repository;

import com.studyplanner.model.refreshtoken.RefreshToken;
import com.studyplanner.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
