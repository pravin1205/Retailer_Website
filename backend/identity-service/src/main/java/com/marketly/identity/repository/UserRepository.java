package com.marketly.identity.repository;

import com.marketly.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.tenantRoles tr LEFT JOIN FETCH tr.role " +
           "WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.tenantRoles tr LEFT JOIN FETCH tr.role " +
           "WHERE u.phone = :phone AND u.deletedAt IS NULL")
    Optional<User> findByPhoneWithRoles(@Param("phone") String phone);
}
