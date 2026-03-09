package com.platform.engine;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * EJB DAO for persisting and loading ComponentDefinitions from MySQL.
 * All operations are transactional via the container.
 */
@Stateless
public class ComponentDefinitionRepository {

    private static final Logger LOG = Logger.getLogger(ComponentDefinitionRepository.class.getName());

    @PersistenceContext(unitName = "PlatformPU")
    private EntityManager em;

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Save a new component or update an existing one (matched by name).
     */
    public ComponentDefinition saveOrUpdate(ComponentDefinition definition) {
        Optional<ComponentDefinition> existing = findByName(definition.getName());
        if (existing.isPresent()) {
            ComponentDefinition managed = existing.get();
            managed.setVersion(definition.getVersion());
            managed.setDescription(definition.getDescription());
            managed.setSourceCode(definition.getSourceCode());
            managed.setStatus(definition.getStatus());
            LOG.info("Updated component in DB: " + definition.getName());
            return em.merge(managed);
        } else {
            em.persist(definition);
            LOG.info("Saved new component to DB: " + definition.getName());
            return definition;
        }
    }

    /**
     * Update the status of a component (ACTIVE / INACTIVE / etc).
     */
    public void updateStatus(String name, ComponentStatus status) {
        findByName(name).ifPresent(def -> {
            def.setStatus(status);
            em.merge(def);
            LOG.info("Status updated for component '" + name + "' → " + status);
        });
    }

    /**
     * Permanently delete a component from the database.
     */
    public void delete(String name) {
        findByName(name).ifPresent(def -> {
            em.remove(em.contains(def) ? def : em.merge(def));
            LOG.info("Deleted component from DB: " + name);
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Load all ACTIVE components — called on startup to repopulate the registry.
     */
    public List<ComponentDefinition> findAllActive() {
        TypedQuery<ComponentDefinition> q = em.createQuery(
            "SELECT c FROM ComponentDefinition c WHERE c.status = :status",
            ComponentDefinition.class
        );
        q.setParameter("status", ComponentStatus.ACTIVE);
        return q.getResultList();
    }

    /**
     * Load all components regardless of status.
     */
    public List<ComponentDefinition> findAll() {
        return em.createQuery("SELECT c FROM ComponentDefinition c ORDER BY c.createdAt DESC",
                ComponentDefinition.class)
                .getResultList();
    }

    /**
     * Find a component by its unique name.
     */
    public Optional<ComponentDefinition> findByName(String name) {
        List<ComponentDefinition> results = em.createQuery(
            "SELECT c FROM ComponentDefinition c WHERE c.name = :name",
            ComponentDefinition.class
        ).setParameter("name", name).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
