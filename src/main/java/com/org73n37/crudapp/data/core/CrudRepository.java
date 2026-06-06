package com.org73n37.crudapp.data.core;

import com.org73n37.crudapp.infrastructure.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.ArrayList;

import com.org73n37.crudapp.logic.spi.CrudStorageProvider;
import com.org73n37.crudapp.logic.core.CrudService.Page;

@Repository
@Transactional
public class CrudRepository<T extends BaseEntity> implements CrudStorageProvider<T> {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private Class<T> entityClass;

    public CrudRepository() {
    }

    public CrudRepository(Class<T> entityClass, EntityManager entityManager) {
        this.entityClass = entityClass;
        this.entityManager = entityManager;
    }

    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    private String getActiveTenantId() {
        String id = TenantContext.getTenantId();
        return (id != null) ? id : "default";
    }

    public List<T> findAll() {
        String tenantId = getActiveTenantId();
        return entityManager.createQuery(
                "SELECT e FROM " + entityClass.getSimpleName() + " e WHERE e.tenantId = :tenantId", entityClass)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    public List<T> findAll(int offset, int limit) {
        String tenantId = getActiveTenantId();
        TypedQuery<T> query = entityManager.createQuery(
                "SELECT e FROM " + entityClass.getSimpleName() + " e WHERE e.tenantId = :tenantId", entityClass)
                .setParameter("tenantId", tenantId);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public Optional<T> findById(Long id) {
        String tenantId = getActiveTenantId();
        T entity = entityManager.find(entityClass, id);
        if (entity != null && tenantId.equals(entity.getTenantId())) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    public T save(T entity) {
        String tenantId = getActiveTenantId();
        
        if (entity.getId() == null) {
            entity.setTenantId(tenantId);
            entityManager.persist(entity);
            return entity;
        } else {
            T existing = entityManager.find(entityClass, entity.getId());
            if (existing != null && !tenantId.equals(existing.getTenantId())) {
                throw new SecurityException("Unauthorized access to entity under different tenant");
            }
            entity.setTenantId(tenantId);
            return entityManager.merge(entity);
        }
    }

    public void deleteById(Long id) {
        String tenantId = getActiveTenantId();
        T entity = entityManager.find(entityClass, id);
        if (entity != null && tenantId.equals(entity.getTenantId())) {
            entityManager.remove(entity);
        }
    }

    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CrudRepository.class);

    @Override
    public Page<T> findAll(int offset, int limit, Map<String, List<String>> queryParams, String sortParam, Class<?> dtoClass) {
        List<T> content = findAllList(offset, limit, queryParams, sortParam, dtoClass);
        long total = count();
        return new Page<>(content, total);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public List<T> findAllList(int offset, int limit, Map<String, List<String>> queryParams, String sortParam, Class<?> dtoClass) {
        String tenantId = getActiveTenantId();
        
        jakarta.persistence.criteria.CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        jakarta.persistence.criteria.CriteriaQuery<T> cq = cb.createQuery(entityClass);
        jakarta.persistence.criteria.Root<T> root = cq.from(entityClass);
        
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("tenantId"), tenantId));
        
        // Dynamic Filtering
        if (queryParams != null) {
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty() || key.equals("page") || key.equals("size") || key.equals("sort")) {
                    continue;
                }
                String value = values.get(0);
                
                try {
                    if (key.endsWith("_like")) {
                        String field = key.substring(0, key.length() - 5);
                        predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
                    } else if (key.endsWith("_gt")) {
                        String field = key.substring(0, key.length() - 3);
                        predicates.add(cb.greaterThan(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_lt")) {
                        String field = key.substring(0, key.length() - 3);
                        predicates.add(cb.lessThan(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_gte")) {
                        String field = key.substring(0, key.length() - 4);
                        predicates.add(cb.greaterThanOrEqualTo(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                    } else if (key.endsWith("_lte")) {
                        String field = key.substring(0, key.length() - 4);
                        predicates.add(cb.lessThanOrEqualTo(root.get(field), (Comparable) convertValue(value, root.get(field).getJavaType())));
                    } else {
                        predicates.add(cb.equal(root.get(key), convertValue(value, root.get(key).getJavaType())));
                    }
                } catch (Exception e) {
                    log.warn("Failed to apply query filter: {} = {}", key, value, e);
                }
            }
        }
        
        cq.where(cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        
        // Dynamic Sorting
        if (sortParam != null && !sortParam.trim().isEmpty()) {
            String[] parts = sortParam.split(",");
            String sortField = parts[0].trim();
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
            
            try {
                if (desc) {
                    cq.orderBy(cb.desc(root.get(sortField)));
                } else {
                    cq.orderBy(cb.asc(root.get(sortField)));
                }
            } catch (Exception e) {
                log.warn("Invalid sort field: {}", sortField, e);
            }
        }
        
        TypedQuery<T> query = entityManager.createQuery(cq);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        
        // Selective Fetching (Entity Graphs)
        if (dtoClass != null) {
            try {
                jakarta.persistence.EntityGraph<T> graph = entityManager.createEntityGraph(entityClass);
                List<String> attributeNames = new java.util.ArrayList<>();
                for (java.lang.reflect.Field f : com.org73n37.crudapp.infrastructure.mapping.ReflectionCache.getDeclaredFields(dtoClass)) {
                    String name = f.getName();
                    Class<?> searchClass = entityClass;
                    boolean found = false;
                    while (searchClass != null) {
                        for (java.lang.reflect.Field ef : com.org73n37.crudapp.infrastructure.mapping.ReflectionCache.getDeclaredFields(searchClass)) {
                            if (ef.getName().equals(name)) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                        searchClass = searchClass.getSuperclass();
                    }
                    if (found) {
                        attributeNames.add(name);
                    }
                }
                // add auditing/version/tenantId fields
                for (String field : List.of("id", "version", "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "tenantId")) {
                    attributeNames.add(field);
                }
                
                graph.addAttributeNodes(attributeNames.toArray(new String[0]));
                query.setHint("jakarta.persistence.fetchgraph", graph);
            } catch (Exception e) {
                log.warn("Failed to create dynamic EntityGraph for DTO {}", dtoClass.getSimpleName(), e);
            }
        }
        
        return query.getResultList();
    }

    private Object convertValue(String val, Class<?> targetType) {
        if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return Long.parseLong(val);
        }
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return Integer.parseInt(val);
        }
        if (targetType.equals(Double.class) || targetType.equals(double.class)) {
            return Double.parseDouble(val);
        }
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
            return Boolean.parseBoolean(val);
        }
        return val;
    }

    public long count() {
        String tenantId = getActiveTenantId();
        return entityManager.createQuery(
                "SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e WHERE e.tenantId = :tenantId", Long.class)
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
