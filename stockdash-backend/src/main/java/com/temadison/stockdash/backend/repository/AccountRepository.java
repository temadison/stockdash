package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByNameIgnoreCase(String name);
}
