package com.studymate;

import com.studymate.ApiDtos.SubjectRequest;
import com.studymate.ApiDtos.SubjectView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.regex.Pattern;
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
@RequestMapping("/api/subjects")
public class SubjectController {
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private final SubjectRepository subjects;
    private final StudyTaskRepository tasks;
    private final NoteRepository notes;
    private final UserRepository users;

    public SubjectController(SubjectRepository subjects, StudyTaskRepository tasks, NoteRepository notes, UserRepository users) {
        this.subjects = subjects;
        this.tasks = tasks;
        this.notes = notes;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SubjectView> list(@AuthenticationPrincipal UserPrincipal user) {
        return subjects.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(subject -> view(subject, user.getId())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SubjectView create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SubjectRequest request) {
        User user = users.getReferenceById(principal.getId());
        Subject subject = subjects.save(new Subject(user, request.name().trim(), clean(request.description()), color(request.color())));
        return view(subject, principal.getId());
    }

    @PutMapping("/{id}")
    @Transactional
    public SubjectView update(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        Subject subject = ownedSubject(id, user.getId());
        subject.setName(request.name().trim());
        subject.setDescription(clean(request.description()));
        subject.setColor(color(request.color()));
        return view(subjects.save(subject), user.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        Subject subject = ownedSubject(id, user.getId());
        var linkedTasks = tasks.findAllBySubject_IdAndUser_Id(id, user.getId());
        linkedTasks.forEach(task -> task.setSubject(null));
        tasks.saveAll(linkedTasks);
        var linkedNotes = notes.findAllBySubject_IdAndUser_Id(id, user.getId());
        linkedNotes.forEach(note -> note.setSubject(null));
        notes.saveAll(linkedNotes);
        subjects.delete(subject);
    }

    private Subject ownedSubject(Long id, Long userId) {
        return subjects.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
    }

    private SubjectView view(Subject subject, Long userId) {
        long total = tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), userId, "TODO")
                + tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), userId, "IN_PROGRESS")
                + tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), userId, "COMPLETED");
        long completed = tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), userId, "COMPLETED");
        return new SubjectView(subject.getId(), subject.getName(), subject.getDescription(), subject.getColor(),
                subject.getCreatedAt(), total, completed);
    }

    private String color(String value) { return value != null && HEX_COLOR.matcher(value).matches() ? value : "#88a891"; }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}