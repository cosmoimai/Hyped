package com.hyped.app.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

    @Query(value = "SELECT * FROM app.app_user WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<UserAccountEntity> findByIdForUpdate(@Param("id") UUID id);
}
