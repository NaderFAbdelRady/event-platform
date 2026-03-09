package com.platform.api;

import com.platform.ai.AIGateway;
import com.platform.ai.AIProvider;
import com.platform.engine.*;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * REST API for the Component Modeler.
 * @RequestScoped makes this a proper CDI bean — required for @EJB and @Inject to work
 * correctly alongside JAX-RS in WildFly.
 */
@RequestScoped
@Path("/modeler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ComponentModelerResource {

    private static final Logger LOG = Logger.getLogger(ComponentModelerResource.class.getName());

    @EJB private ComponentRegistry  registry;
    @EJB private LifecycleManager   lifecycleManager;
    @EJB private DeadLetterQueue    dlq;
    @EJB private ComponentLoader    componentLoader;
    @Inject private AIGateway       aiGateway;

    // ── Engine Status ─────────────────────────────────────────

    @GET
    @Path("/engine/status")
    public Response getEngineStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("status",       "RUNNING");
            status.put("totalLoaded",  registry.size());
            status.put("totalActive",  registry.countByStatus(ComponentStatus.ACTIVE));
            status.put("totalFailed",  registry.countByStatus(ComponentStatus.FAILED));
            status.put("watchDir",     componentLoader.getComponentsDir());
            return Response.ok(status).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to get engine status", e);
            return serverError(e);
        }
    }

    @GET
    @Path("/engine/registry")
    public Response getRegistry() {
        try {
            List<Map<String, Object>> result = registry.getAll().stream()
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name",                e.getName());
                        m.put("status",              e.getStatus().name());
                        m.put("loadedAt",            e.getLoadedAt() != null ? e.getLoadedAt().toString() : null);
                        m.put("eventsProcessed",     e.getEventsProcessed());
                        m.put("eventsFailed",        e.getEventsFailed());
                        m.put("avgProcessingTimeMs", e.getAvgProcessingTimeMs());
                        m.put("lastError",           e.getLastError() != null ? e.getLastError() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to get registry", e);
            return serverError(e);
        }
    }

    // ── Lifecycle Control ─────────────────────────────────────

    @POST
    @Path("/engine/components/{name}/reload")
    public Response reload(@PathParam("name") String name) {
        try {
            ComponentEntry entry = registry.get(name);
            if (entry == null) return notFound("Component not found: " + name);
            lifecycleManager.unload(name);
            Map<String, Object> r = new HashMap<>();
            r.put("status", "RELOADING"); r.put("message", name + " queued for reload");
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/engine/components/{name}/unload")
    public Response unload(@PathParam("name") String name) {
        try {
            if (registry.get(name) == null) return notFound("Component not found: " + name);
            lifecycleManager.unload(name);
            Map<String, Object> r = new HashMap<>();
            r.put("status", "UNLOADED"); r.put("component", name);
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/engine/components/{name}/activate")
    public Response activate(@PathParam("name") String name) {
        try {
            if (registry.get(name) == null) return notFound("Component not found: " + name);
            lifecycleManager.activate(name);
            Map<String, Object> r = new HashMap<>();
            r.put("status", "ACTIVE"); r.put("component", name);
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/engine/reload-all")
    public Response reloadAll() {
        try {
            registry.getAll().forEach(e -> lifecycleManager.unload(e.getName()));
            Map<String, Object> r = new HashMap<>();
            r.put("status", "RELOADING_ALL");
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    // ── AI Code Generation ────────────────────────────────────

    @POST
    @Path("/components/generate")
    public Response generateComponent(GenerateRequest req) {
        try {
            if (req == null)
                return badRequest("Request body is required");
            if (req.getName() == null || req.getName().isBlank())
                return badRequest("name is required");
            if (req.getDescription() == null || req.getDescription().isBlank())
                return badRequest("description is required");
            if (req.getApiKey() == null || req.getApiKey().isBlank())
                return badRequest("apiKey is required");

            AIProvider provider;
            try {
                provider = AIProvider.valueOf(
                        req.getAiProvider() != null ? req.getAiProvider().toUpperCase() : "CLAUDE");
            } catch (Exception e) {
                return badRequest("Invalid AI provider: " + req.getAiProvider());
            }

            AIGateway.GenerationResult result = aiGateway.generate(
                    req.getName(),
                    req.getVersion() != null ? req.getVersion() : "1.0.0",
                    req.getDescription(),
                    provider,
                    req.getApiKey()
            );

            Map<String, Object> r = new HashMap<>();
            if (result.isSuccess()) {
                r.put("status",     "SUCCESS");
                r.put("component",  result.getComponentName());
                r.put("attempts",   result.getAttempts());
                r.put("sourceCode", result.getSourceCode());
                return Response.ok(r).build();
            } else {
                r.put("status",   "FAILED");
                r.put("error",    result.getErrorMessage());
                r.put("attempts", result.getAttempts());
                return Response.status(500).entity(r).build();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Generation failed", e);
            return serverError(e);
        }
    }

    // ── Dead Letter Queue ─────────────────────────────────────

    @GET
    @Path("/dlq")
    public Response getDlq() {
        try {
            return Response.ok(dlq.getAll()).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to get DLQ", e);
            return serverError(e);
        }
    }

    @GET
    @Path("/dlq/{id}")
    public Response getDlqEntry(@PathParam("id") String id) {
        try {
            DeadLetterQueue.DLQEntry entry = dlq.getById(id);
            if (entry == null) return notFound("DLQ entry not found: " + id);
            return Response.ok(entry).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/dlq/{id}/resolve")
    public Response resolveDlq(@PathParam("id") String id) {
        try {
            dlq.resolve(id);
            Map<String, Object> r = new HashMap<>();
            r.put("status", "resolved"); r.put("id", id);
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/dlq/{id}/retry")
    public Response retryDlq(@PathParam("id") String id) {
        try {
            // Re-resolve — actual retry logic can be extended here
            dlq.resolve(id);
            Map<String, Object> r = new HashMap<>();
            r.put("status", "retried"); r.put("id", id);
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @POST
    @Path("/dlq/retry-all")
    public Response retryAllDlq() {
        try {
            dlq.getAll().stream()
               .filter(e -> !e.isResolved())
               .forEach(e -> dlq.resolve(e.getId()));
            Map<String, Object> r = new HashMap<>();
            r.put("status", "retried_all");
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @DELETE
    @Path("/dlq/{id}")
    public Response deleteDlqEntry(@PathParam("id") String id) {
        try {
            dlq.delete(id);
            return Response.noContent().build();
        } catch (Exception e) { return serverError(e); }
    }

    @DELETE
    @Path("/dlq")
    public Response clearDlq() {
        try {
            dlq.clearResolved();
            Map<String, Object> r = new HashMap<>();
            r.put("status", "cleared");
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    // ── Metrics ───────────────────────────────────────────────

    @GET
    @Path("/metrics/overview")
    public Response getMetricsOverview() {
        try {
            long totalProcessed = registry.getAll().stream().mapToLong(ComponentEntry::getEventsProcessed).sum();
            long totalFailed    = registry.getAll().stream().mapToLong(ComponentEntry::getEventsFailed).sum();

            Map<String, Object> r = new HashMap<>();
            r.put("totalEventsProcessed",  totalProcessed);
            r.put("totalEventsFailed",     totalFailed);
            r.put("totalDeadLettered",     dlq.size());
            r.put("totalComponentsActive", registry.countByStatus(ComponentStatus.ACTIVE));
            return Response.ok(r).build();
        } catch (Exception e) { return serverError(e); }
    }

    @GET
    @Path("/metrics/components")
    public Response getComponentMetrics() {
        try {
            List<Map<String, Object>> metrics = registry.getAll().stream()
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name",                e.getName());
                        m.put("eventsProcessed",     e.getEventsProcessed());
                        m.put("eventsFailed",        e.getEventsFailed());
                        m.put("avgProcessingTimeMs", e.getAvgProcessingTimeMs());
                        m.put("status",              e.getStatus().name());
                        return m;
                    })
                    .collect(Collectors.toList());
            return Response.ok(metrics).build();
        } catch (Exception e) { return serverError(e); }
    }

    // ── Error helpers ─────────────────────────────────────────

    private Response serverError(Exception e) {
        Map<String, Object> r = new HashMap<>();
        r.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        r.put("status", 500);
        return Response.status(500).entity(r).build();
    }

    private Response badRequest(String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("error", msg); r.put("status", 400);
        return Response.status(400).entity(r).build();
    }

    private Response notFound(String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("error", msg); r.put("status", 404);
        return Response.status(404).entity(r).build();
    }

    // ── DTOs ──────────────────────────────────────────────────

    public static class GenerateRequest {
        private String name, version, description, aiProvider, apiKey;

        public String getName()        { return name; }
        public String getVersion()     { return version; }
        public String getDescription() { return description; }
        public String getAiProvider()  { return aiProvider; }
        public String getApiKey()      { return apiKey; }

        public void setName(String v)        { this.name = v; }
        public void setVersion(String v)     { this.version = v; }
        public void setDescription(String v) { this.description = v; }
        public void setAiProvider(String v)  { this.aiProvider = v; }
        public void setApiKey(String v)      { this.apiKey = v; }
    }
}
