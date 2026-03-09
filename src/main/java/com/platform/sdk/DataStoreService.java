package com.platform.sdk;

import java.util.List;
import java.util.Map;

/**
 * SDK Service — provides generic database access to components.
 * Components use collections (like MongoDB-style) without needing
 * to know the underlying table structure.
 * Injected by the engine at load time.
 */
public interface DataStoreService {

    /**
     * Saves a data object to the named collection.
     * Returns the generated ID.
     */
    String save(String collection, Map<String, Object> data);

    /**
     * Updates an existing record by ID in the named collection.
     */
    void update(String collection, String id, Map<String, Object> data);

    /**
     * Finds records in a collection matching all given filters.
     * Filters are AND-ed together.
     */
    List<Map<String, Object>> find(String collection, Map<String, Object> filters);

    /**
     * Finds a single record by its ID.
     */
    Map<String, Object> findById(String collection, String id);

    /**
     * Deletes a record by ID from the named collection.
     */
    void delete(String collection, String id);

    /**
     * Returns the count of records matching the given filters.
     */
    long count(String collection, Map<String, Object> filters);
}
