package com.easwari.backend.repository;

import com.easwari.backend.domain.NepCreditRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NepCreditRuleRepository extends JpaRepository<NepCreditRule, Long> {
    List<NepCreditRule> findByActiveTrue();
    Optional<NepCreditRule> findFirstByActiveTrueOrderByIdDesc();
}
