package com.platform.engine;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity that persists a component's metadata and source code to MySQL.
 * Survives redeployments — components are reloaded from this table on startup.
 */
@Entity
@Table(name = "component_definitions")
public class ComponentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 255)
    private String name;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "description", length = 1000)
    private String description;

    @Lob
    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ComponentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ComponentStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public ComponentDefinition() {}

    public ComponentDefinition(String name, String version, String description,
                                String sourceCode, ComponentStatus status) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.sourceCode = sourceCode;
        this.status = status;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public ComponentStatus getStatus() { return status; }
    public void setStatus(ComponentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "ComponentDefinition{name='" + name + "', version='" + version
                + "', status=" + status + "}";
    }
}
