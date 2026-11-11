package com.studymate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {
    List<StudyPlan> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
}