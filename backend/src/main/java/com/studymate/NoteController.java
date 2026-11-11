package com.studymate;

import com.studymate.ApiDtos.NoteRequest;
import com.studymate.ApiDtos.NoteView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteRepository notes;
    private final SubjectRepository subjects;
    private final UserRepository users;

    public NoteController(NoteRepository notes, SubjectRepository subjects, UserRepository users) {
        this.notes = notes;
        this.subjects = subjects;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NoteView> list(@AuthenticationPrincipal UserPrincipal user) {
        return notes.findAllByUser_IdOrderByUpdatedAtDesc(user.getId()).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public NoteView create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody NoteRequest request) {
        Note note = new Note(users.getReferenceById(user.getId()), subject(request.subjectId(), user.getId()),
                request.title().trim(), request.content().trim());
        return view(notes.save(note));
    }

    @PutMapping("/{id}")
    @Transactional
    public NoteView update(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id,
                           @Valid @RequestBody NoteRequest request) {
        Note note = notes.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        note.setTitle(request.title().trim());
        note.setContent(request.content().trim());
        note.setSubject(subject(request.subjectId(), user.getId()));
        return view(notes.save(note));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        Note note = notes.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        notes.delete(note);
    }

    private Subject subject(Long subjectId, Long userId) {
        if (subjectId == null) return null;
        return subjects.findByIdAndUser_Id(subjectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject not found"));
    }

    private NoteView view(Note note) {
        Subject subject = note.getSubject();
        return new NoteView(note.getId(), note.getTitle(), note.getContent(), subject == null ? null : subject.getId(),
                subject == null ? null : subject.getName(), note.getCreatedAt(), note.getUpdatedAt());
    }
}