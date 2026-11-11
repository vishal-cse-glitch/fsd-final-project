package com.studymate;

import com.studymate.ApiDtos.AskRequest;
import com.studymate.ApiDtos.AskResponse;
import com.studymate.ApiDtos.PlanRequest;
import com.studymate.ApiDtos.PlanView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final Set<String> MODES = Set.of("explain", "summarize", "question", "tips");
    private final AiService ai;
    private final SubjectRepository subjects;
    private final StudyTaskRepository tasks;
    private final StudyPlanRepository plans;
    private final UserRepository users;

    public AiController(AiService ai, SubjectRepository subjects, StudyTaskRepository tasks,
                        StudyPlanRepository plans, UserRepository users) {
        this.ai = ai;
        this.subjects = subjects;
        this.tasks = tasks;
        this.plans = plans;
        this.users = users;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        String mode = request.mode() == null ? "explain" : request.mode().toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose explain, summarize, question, or tips mode");
        return new AskResponse(ai.generate("Study request mode: " + mode + "\n\nStudent request:\n" + request.prompt().trim(), 512));
    }

    @PostMapping("/plan")
    @Transactional
    public PlanView createPlan(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody PlanRequest request) {
        LocalDate endDate = request.startDate().plusDays(request.days() - 1L);
        List<Subject> userSubjects = subjects.findAllByUser_IdOrderByCreatedAtDesc(user.getId());
        List<StudyTask> pendingTasks = tasks.findAllByUser_IdOrderByDueDateAsc(user.getId()).stream()
                .filter(task -> !"COMPLETED".equals(task.getStatus()))
                .sorted(Comparator.comparingInt(AiController::priorityRank)
                        .thenComparing(StudyTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        String tipPrompt = "Give one actionable study tip in one short sentence. Do not make a schedule or mention dates. "
                + (request.focus() == null || request.focus().isBlank() ? "" : "Focus: " + request.focus().trim() + ". ")
                + "Subjects: " + userSubjects.stream().map(Subject::getName).reduce((a, b) -> a + ", " + b).orElse("none")
                + ". Pending tasks: " + pendingTasks.stream().limit(8).map(StudyTask::getTitle)
                        .reduce((a, b) -> a + "; " + b).orElse("none");
        String studyTip = ai.generate(tipPrompt, 128).replaceAll("\\s+", " ").trim();
        int sentenceEnd = studyTip.indexOf('.');
        if (sentenceEnd >= 0) studyTip = studyTip.substring(0, sentenceEnd + 1);
        if (studyTip.length() > 300) studyTip = studyTip.substring(0, 300).trim();
        String planText = buildPlanText(request, endDate, userSubjects, pendingTasks, studyTip);
        StudyPlan plan = plans.save(new StudyPlan(users.getReferenceById(user.getId()), request.startDate(), endDate, planText));
        return view(plan);
    }

    @GetMapping("/plans")
    @Transactional(readOnly = true)
    public List<PlanView> listPlans(@AuthenticationPrincipal UserPrincipal user) {
        return plans.findAllByUser_IdOrderByCreatedAtDesc(user.getId()).stream().map(this::view).toList();
    }

    private PlanView view(StudyPlan plan) {
        return new PlanView(plan.getId(), plan.getStartDate(), plan.getEndDate(), plan.getPlanText(), plan.getCreatedAt());
    }

    private String buildPlanText(PlanRequest request, LocalDate endDate, List<Subject> userSubjects,
                                 List<StudyTask> pendingTasks, String studyTip) {
        int totalMinutes = request.hoursPerDay() * 60;
        int reviewMinutes = Math.min(15, Math.max(5, totalMinutes / 6));
        int focusMinutes = totalMinutes - reviewMinutes;
        int blocks = (focusMinutes + 59) / 60;
        int minutesPerBlock = focusMinutes / blocks;
        int extraMinutes = focusMinutes % blocks;
        StringBuilder plan = new StringBuilder("# Study Plan\n\n**AI study tip:** ")
                .append(studyTip).append("\n\n");
        int activityIndex = 0;
        for (LocalDate date = request.startDate(); !date.isAfter(endDate); date = date.plusDays(1)) {
            plan.append("## ").append(date).append('\n');
            for (int block = 0; block < blocks; block++) {
                int blockMinutes = minutesPerBlock + (block < extraMinutes ? 1 : 0);
                plan.append("- **Study block ").append(block + 1).append(" (").append(blockMinutes)
                        .append(" min):** ").append(activityFor(activityIndex++, userSubjects, pendingTasks, request.focus())).append('\n');
                if (block + 1 < blocks) plan.append("- **Break (10 min).** Step away and rest.\n");
            }
            plan.append("- **Daily review (").append(reviewMinutes)
                    .append(" min):** Recall the key points and note what to study next.\n\n");
        }
        return plan.toString().trim();
    }

    private String activityFor(int index, List<Subject> userSubjects, List<StudyTask> pendingTasks, String focus) {
        if (index < pendingTasks.size()) {
            StudyTask task = pendingTasks.get(index);
            String subject = task.getSubject() == null ? "" : " for " + task.getSubject().getName();
            return "Work on \"" + task.getTitle() + "\"" + subject + " (" + task.getPriority().toLowerCase(Locale.ROOT) + " priority).";
        }
        if (!userSubjects.isEmpty()) {
            return "Review key concepts and practice questions for "
                    + userSubjects.get(index % userSubjects.size()).getName() + ".";
        }
        if (focus != null && !focus.isBlank()) {
            String focusArea = focus.trim();
            return "Study your focus area: " + focusArea + (focusArea.matches(".*[.!?]$") ? "" : ".");
        }
        return "Review your course notes and practice the most challenging concepts.";
    }

    private static int priorityRank(StudyTask task) {
        return switch (task.getPriority()) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}