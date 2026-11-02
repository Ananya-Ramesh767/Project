package com.easwari.backend.repository;

import com.easwari.backend.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByActiveTrue();
    List<Room> findByRoomTypeAndActiveTrue(String roomType);
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    boolean existsByRoomCodeAndIdNot(String roomCode, Long id);
}
