package com.temadison.stockdash.backend.repository;

import com.temadison.stockdash.backend.domain.JobRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRunRepository extends JpaRepository<JobRunEntity, Long> {
}
