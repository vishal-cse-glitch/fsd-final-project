package com.studymate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findAllByUser_IdOrderByUpdatedAtDesc(Long userId);
    Optional<Note> findByIdAndUser_Id(Long id, Long userId);
    List<Note> findAllBySubject_IdAndUser_Id(Long subjectId, Long userId);
}