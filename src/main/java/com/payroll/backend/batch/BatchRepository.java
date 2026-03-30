package com.payroll.backend.batch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    Optional<Batch> findByName(String name);

    Optional<Batch> findTopByOrderByIdDesc();
}