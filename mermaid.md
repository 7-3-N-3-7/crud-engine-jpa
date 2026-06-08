# JPA Storage Module Architecture (Mermaid)

This file contains Mermaid diagrams visualizing the structure and design of the JPA storage module (`crud-engine-jpa`).

## 1. Class Structure

```mermaid
classDiagram
    class CrudRepository~T~ {
        -EntityManager entityManager
        -Class~T~ entityClass
        +findAll() List~T~
        +findById(Long) Optional~T~
        +save(T) T
        +deleteById(Long) void
        +findAll(int, int, Map, String, Class) Page~T~
    }

    class JpaStorageProviderFactory {
        -EntityManager entityManager
        +supports(Class) boolean
        +getStorageProvider(Class) CrudStorageProvider
    }

    JpaStorageProviderFactory --> CrudRepository : creates
```

## 2. Query Builder Execution Flow

```mermaid
graph TD
    start([Start findAll request]) --> getCB[Get CriteriaBuilder]
    getCB --> createCQ[Create CriteriaQuery]
    createCQ --> root[Create Root Entity Path]
    root --> tenantPred[Add tenantId = activeTenant Predicate]
    
    tenantPred --> checkParams{Has Filter Params?}
    checkParams -- Yes --> parseParams[Parse _like, _gt, _lt, _gte, _lte suffixes]
    parseParams --> addFilterPreds[Add filter Predicates]
    addFilterPreds --> checkSort{Has Sort Param?}
    checkParams -- No --> checkSort
    
    checkSort -- Yes --> addOrderBy[Add orderBy desc/asc]
    checkSort -- No --> checkDto{Has DTO Class?}
    addOrderBy --> checkDto
    
    checkDto -- Yes --> createGraph[Create EntityGraph based on DTO fields]
    createGraph --> setHint[Set hint jakarta.persistence.fetchgraph]
    setHint --> query[Execute TypedQuery]
    checkDto -- No --> query
    
    query --> done([Return List of Entities])
```
