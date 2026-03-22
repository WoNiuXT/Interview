package com.ningcai.interview.repository;

import com.ningcai.interview.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    // 根据账号查找用户（支持用户名/手机号/邮箱）
    default Optional<User> findByAccount(String account) {
        if (account == null) {
            return Optional.empty();
        }

        Optional<User> user = findByUsername(account);
        if (user.isPresent()) {
            return user;
        }

        user = findByPhone(account);
        if (user.isPresent()) {
            return user;
        }

        return findByEmail(account);
    }
}