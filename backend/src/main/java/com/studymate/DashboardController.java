package com.studymate;

import com.studymate.ApiDtos.DashboardView;
import com.studymate.ApiDtos.SubjectView;
import com.studymate.ApiDtos.TaskView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final StudyTaskRepository tasks;
    private final SubjectRepository subjects;

    public DashboardController(StudyTaskRepository tasks, SubjectRepository subjects) {
        this.tasks = tasks;
        this.subjects = subjects;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardView dashboard(@AuthenticationPrincipal UserPrincipal user) {
        List<SubjectView> subjectViews = subjects.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(subject -> {
                    long total = tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), user.getId(), "TODO")
                            + tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), user.getId(), "IN_PROGRESS")
                            + tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), user.getId(), "COMPLETED");
                    long completed = tasks.countBySubject_IdAndUser_IdAndStatus(subject.getId(), user.getId(), "COMPLETED");
                    return new SubjectView(subject.getId(), subject.getName(), subject.getDescription(), subject.getColor(),
                            subject.getCreatedAt(), total, completed);
                }).toList();
        List<TaskView> upcoming = tasks.findAllByUser_IdAndStatusNotAndDueDateGreaterThanEqualOrderByDueDateAsc(
                        user.getId(), "COMPLETED", LocalDate.now(), PageRequest.of(0, 5))
                .stream().map(task -> {
                    Subject subject = task.getSubject();
                    return new TaskView(task.getId(), task.getTitle(), task.getDetails(), subject == null ? null : subject.getId(),
                            subject == null ? null : subject.getName(), subject == null ? null : subject.getColor(),
                            task.getPriority(), task.getDueDate(), task.getStatus(), task.getCreatedAt());
                }).toList();
        return new DashboardView(tasks.countByUser_Id(user.getId()),
                tasks.countByUser_IdAndStatus(user.getId(), "COMPLETED"),
                tasks.countByUser_IdAndStatus(user.getId(), "IN_PROGRESS"), subjectViews.size(), upcoming, subjectViews);
    }
}