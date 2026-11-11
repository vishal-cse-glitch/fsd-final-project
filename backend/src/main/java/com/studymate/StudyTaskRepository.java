package com.studymate;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {
    List<StudyTask> findAllByUser_IdOrderByDueDateAsc(Long userId);
    List<StudyTask> findAllByUser_IdAndStatusNotAndDueDateGreaterThanEqualOrderByDueDateAsc(Long userId, String status, LocalDate date, Pageable pageable);
    List<StudyTask> findAllBySubject_IdAndUser_Id(Long subjectId, Long userId);
    java.util.Optional<StudyTask> findByIdAndUser_Id(Long id, Long userId);
    long countByUser_Id(Long userId);
    long countByUser_IdAndStatus(Long userId, String status);
    long countByUser_IdAndStatusNot(Long userId, String status);
    long countBySubject_IdAndUser_IdAndStatus(Long subjectId, Long userId, String status);
}