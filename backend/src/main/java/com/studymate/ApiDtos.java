package com.studymate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record UserView(Long id, String name, String email) {}
    public record AuthResponse(String token, UserView user) {}
    public record SubjectRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description, String color) {}
    public record SubjectView(Long id, String name, String description, String color, Instant createdAt, long totalTasks, long completedTasks) {}
    public record TaskRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 1000) String details,
            Long subjectId,
            String priority,
            LocalDate dueDate,
            String status) {}
    public record TaskView(Long id, String title, String details, Long subjectId, String subjectName, String subjectColor,
                           String priority, LocalDate dueDate, String status, Instant createdAt) {}
    public record NoteRequest(@NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 20000) String content, Long subjectId) {}
    public record NoteView(Long id, String title, String content, Long subjectId, String subjectName, Instant createdAt, Instant updatedAt) {}
    public record DashboardView(long totalTasks, long completedTasks, long inProgressTasks, long subjectCount,
                                List<TaskView> upcomingTasks, List<SubjectView> subjects) {}
    public record AskRequest(@NotBlank @Size(max = 6000) String prompt, @Size(max = 24) String mode) {}
    public record AskResponse(String answer) {}
    public record PlanRequest(@NotNull LocalDate startDate, @Min(1) @Max(14) int days,
                              @Min(1) @Max(16) int hoursPerDay, @Size(max = 500) String focus) {}
    public record PlanView(Long id, LocalDate startDate, LocalDate endDate, String planText, Instant createdAt) {}
}