package com.studymate;

import com.studymate.ApiDtos.TaskRequest;
import com.studymate.ApiDtos.TaskView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> STATUSES = Set.of("TODO", "IN_PROGRESS", "COMPLETED");
    private final StudyTaskRepository tasks;
    private final SubjectRepository subjects;
    private final UserRepository users;

    public TaskController(StudyTaskRepository tasks, SubjectRepository subjects, UserRepository users) {
        this.tasks = tasks;
        this.subjects = subjects;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TaskView> list(@AuthenticationPrincipal UserPrincipal user) {
        return tasks.findAllByUser_IdOrderByDueDateAsc(user.getId()).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TaskView create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody TaskRequest request) {
        var task = new StudyTask(users.getReferenceById(user.getId()), subject(request.subjectId(), user.getId()),
                request.title().trim(), clean(request.details()), normalize(request.priority(), "MEDIUM", PRIORITIES),
                request.dueDate(), normalize(request.status(), "TODO", STATUSES));
        return view(tasks.save(task));
    }

    @PutMapping("/{id}")
    @Transactional
    public TaskView update(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id,
                           @Valid @RequestBody TaskRequest request) {
        StudyTask task = ownedTask(id, user.getId());
        task.setTitle(request.title().trim());
        task.setDetails(clean(request.details()));
        task.setSubject(subject(request.subjectId(), user.getId()));
        task.setPriority(normalize(request.priority(), "MEDIUM", PRIORITIES));
        task.setDueDate(request.dueDate());
        task.setStatus(normalize(request.status(), "TODO", STATUSES));
        return view(tasks.save(task));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        tasks.delete(ownedTask(id, user.getId()));
    }

    private StudyTask ownedTask(Long id, Long userId) {
        return tasks.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private Subject subject(Long subjectId, Long userId) {
        if (subjectId == null) return null;
        return subjects.findByIdAndUser_Id(subjectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject not found"));
    }

    private TaskView view(StudyTask task) {
        Subject subject = task.getSubject();
        return new TaskView(task.getId(), task.getTitle(), task.getDetails(), subject == null ? null : subject.getId(),
                subject == null ? null : subject.getName(), subject == null ? null : subject.getColor(), task.getPriority(),
                task.getDueDate(), task.getStatus(), task.getCreatedAt());
    }

    private String normalize(String value, String fallback, Set<String> allowed) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task priority or status");
        return normalized;
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
}