package com.easwari.backend.controller;

import com.easwari.backend.domain.CollegeTimeslot;
import com.easwari.backend.repository.CollegeTimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/timeslots")
@RequiredArgsConstructor
public class TimeslotController {

    private final CollegeTimeslotRepository timeslotRepository;

    @GetMapping
    public ResponseEntity<List<CollegeTimeslot>> getAllTimeslots() {
        return ResponseEntity.ok(timeslotRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<CollegeTimeslot> createTimeslot(@RequestBody CollegeTimeslot timeslot) {
        CollegeTimeslot saved = timeslotRepository.save(timeslot);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CollegeTimeslot> updateTimeslot(@PathVariable Long id, @RequestBody CollegeTimeslot timeslot) {
        if (!timeslotRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        timeslot.setId(id);
        CollegeTimeslot saved = timeslotRepository.save(timeslot);
        return ResponseEntity.ok(saved);
    }
}
