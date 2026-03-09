package com.platform.service;

import com.platform.sdk.DataStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

@Stateless
public class DataStoreServiceImpl implements DataStoreService {

    private static final Logger LOG = Logger.getLogger(DataStoreServiceImpl.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @PersistenceContext(unitName = "platformPU")
    private EntityManager em;

    @Override
    public String save(String collection, Map<String, Object> data) {
        String id = UUID.randomUUID().toString();
        String json;
        try { json = JSON.writeValueAsString(data); } 
        catch (Exception e) { json = "{}"; }

        em.createNativeQuery(
            "INSERT INTO data_store (id, collection_name, data, created_at) " +
            "VALUES (:id, :col, :data, :ts)")
            .setParameter("id",   id)
            .setParameter("col",  collection)
            .setParameter("data", json)
            .setParameter("ts",   LocalDateTime.now())
            .executeUpdate();

        return id;
    }

    @Override
    public void update(String collection, String id, Map<String, Object> data) {
        String json;
        try { json = JSON.writeValueAsString(data); } 
        catch (Exception e) { json = "{}"; }

        em.createNativeQuery(
            "UPDATE data_store SET data = :data, updated_at = :ts " +
            "WHERE id = :id AND collection_name = :col")
            .setParameter("data", json)
            .setParameter("ts",   LocalDateTime.now())
            .setParameter("id",   id)
            .setParameter("col",  collection)
            .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> find(String collection, Map<String, Object> filters) {
        List<Object[]> rows = em.createNativeQuery(
            "SELECT id, data FROM data_store WHERE collection_name = :col")
            .setParameter("col", collection)
            .getResultList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            try {
                Map<String, Object> record = JSON.readValue((String) row[1], Map.class);
                record.put("_id", row[0]);
                if (matchesFilters(record, filters)) result.add(record);
            } catch (Exception e) {
                LOG.warning("Failed to parse record: " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> findById(String collection, String id) {
        Object result = em.createNativeQuery(
            "SELECT data FROM data_store WHERE id = :id AND collection_name = :col")
            .setParameter("id",  id)
            .setParameter("col", collection)
            .getSingleResult();

        try {
            Map<String, Object> record = JSON.readValue((String) result, Map.class);
            record.put("_id", id);
            return record;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public void delete(String collection, String id) {
        em.createNativeQuery(
            "DELETE FROM data_store WHERE id = :id AND collection_name = :col")
            .setParameter("id",  id)
            .setParameter("col", collection)
            .executeUpdate();
    }

    @Override
    public long count(String collection, Map<String, Object> filters) {
        return find(collection, filters).size();
    }

    private boolean matchesFilters(Map<String, Object> record, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<String, Object> f : filters.entrySet()) {
            Object val = record.get(f.getKey());
            if (val == null || !val.equals(f.getValue())) return false;
        }
        return true;
    }
}