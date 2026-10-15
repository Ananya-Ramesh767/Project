package com.odysseus.backend.repository;

import com.odysseus.backend.domain.RoomChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomChangeHistoryRepository extends JpaRepository<RoomChangeHistory, Long> {
    List<RoomChangeHistory> findByBatchIdOrderByTimestampDesc(Long batchId);
    List<RoomChangeHistory> findByActiveTrueOrderByTimestampDesc();
}
