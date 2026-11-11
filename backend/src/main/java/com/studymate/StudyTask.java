package com.studymate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "study_tasks")
public class StudyTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false, length = 12)
    private String priority;

    private LocalDate dueDate;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected StudyTask() {}

    public StudyTask(User user, Subject subject, String title, String details, String priority, LocalDate dueDate, String status) {
        this.user = user;
        this.subject = subject;
        this.title = title;
        this.details = details;
        this.priority = priority;
        this.dueDate = dueDate;
        this.status = status;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Subject getSubject() { return subject; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public String getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public void setTitle(String title) { this.title = title; }
    public void setDetails(String details) { this.details = details; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setStatus(String status) { this.status = status; }
}