package com.platform.engine;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry of all loaded components.
 * Backed by a ConcurrentHashMap — reads are lock-free,
 * writes use container-managed locks.
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class ComponentRegistry {

    private final ConcurrentHashMap<String, ComponentEntry> registry = new ConcurrentHashMap<>();

    @Lock(LockType.WRITE)
    public void register(ComponentEntry entry) {
        registry.put(entry.getName(), entry);
    }

    @Lock(LockType.WRITE)
    public void unregister(String name) {
        registry.remove(name);
    }

    @Lock(LockType.READ)
    public ComponentEntry get(String name) {
        return registry.get(name);
    }

    @Lock(LockType.READ)
    public boolean contains(String name) {
        return registry.containsKey(name);
    }

    @Lock(LockType.READ)
    public Collection<ComponentEntry> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Lock(LockType.READ)
    public int size() {
        return registry.size();
    }

    @Lock(LockType.READ)
    public long countByStatus(ComponentStatus status) {
        return registry.values().stream()
                .filter(e -> e.getStatus() == status)
                .count();
    }
}
