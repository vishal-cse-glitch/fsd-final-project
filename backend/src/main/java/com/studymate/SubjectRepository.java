package com.studymate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findAllByUser_IdOrderByCreatedAtDesc(Long userId);
    Optional<Subject> findByIdAndUser_Id(Long id, Long userId);
}