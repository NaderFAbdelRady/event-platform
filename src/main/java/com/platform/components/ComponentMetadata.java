package com.platform.components;

/**
 * Identity and descriptive information about a component.
 * Returned by EventComponent.getMetadata().
 */
public class ComponentMetadata {

    private String name;
    private String version;
    private String description;
    private String author;

    public ComponentMetadata() {}

    private ComponentMetadata(Builder builder) {
        this.name        = builder.name;
        this.version     = builder.version;
        this.description = builder.description;
        this.author      = builder.author;
    }

    /** Convenience constructor: new ComponentMetadata("MyComp", "1.0.0", "description") */
    public ComponentMetadata(String name, String version, String description) {
        this.name        = name;
        this.version     = version;
        this.description = description;
        this.author      = "AI Generated";
    }

    /** Convenience constructor: new ComponentMetadata("MyComp", "1.0.0", "description", "author") */
    public ComponentMetadata(String name, String version, String description, String author) {
        this.name        = name;
        this.version     = version;
        this.description = description;
        this.author      = author;
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String version    = "1.0.0";
        private String description = "";
        private String author      = "AI Generated";

        public Builder name(String name)               { this.name = name; return this; }
        public Builder version(String version)         { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder author(String author)           { this.author = author; return this; }

        public ComponentMetadata build() {
            if (name == null || name.isBlank())
                throw new IllegalStateException("Component name is required");
            return new ComponentMetadata(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────

    public String getName()        { return name; }
    public String getVersion()     { return version; }
    public String getDescription() { return description; }
    public String getAuthor()      { return author; }

    // ── Setters (needed for JSON deserialization) ─────────────

    public void setName(String name)               { this.name = name; }
    public void setVersion(String version)         { this.version = version; }
    public void setDescription(String description) { this.description = description; }
    public void setAuthor(String author)           { this.author = author; }

    @Override
    public String toString() {
        return name + " v" + version;
    }
}
