package com.org73n37.crudapp.data.jpa;

import com.org73n37.crudapp.data.core.BaseEntity;
import com.org73n37.crudapp.data.core.CrudRepository;
import com.org73n37.crudapp.logic.spi.CrudStorageProvider;
import com.org73n37.crudapp.logic.spi.CrudStorageProviderFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class JpaStorageProviderFactory implements CrudStorageProviderFactory {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean supports(Class<? extends BaseEntity> entityClass) {
        return entityClass.isAnnotationPresent(jakarta.persistence.Entity.class);
    }

    @Override
    public <T extends BaseEntity> CrudStorageProvider<T> getStorageProvider(Class<T> entityClass) {
        return new CrudRepository<>(entityClass, entityManager);
    }
}
