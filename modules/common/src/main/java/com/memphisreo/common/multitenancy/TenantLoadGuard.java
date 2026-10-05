package com.memphisreo.common.multitenancy;

import org.hibernate.annotations.TenantId;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

/**
 * Доповнення рубежу 1 з ADR-001. Фільтр Hibernate {@code @TenantId} діє на
 * запити, але НЕ на завантаження за ключем ({@code findById}/{@code em.find})
 * — без RLS агенція B отримала б запис агенції A за його id. Цей listener
 * перевіряє tenant кожної завантаженої сутності проти tenant-а сесії.
 */
public class TenantLoadGuard implements PostLoadEventListener, Integrator {

    private static final ClassValue<Optional<Field>> TENANT_FIELD = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(TenantId.class)) {
                        field.setAccessible(true);
                        return Optional.of(field);
                    }
                }
            }
            return Optional.empty();
        }
    };

    @Override
    public void onPostLoad(PostLoadEvent event) {
        check(event.getEntity(), event.getSession().getTenantIdentifierValue());
    }

    void check(Object entity, Object sessionTenant) {
        Optional<Field> tenantField = TENANT_FIELD.get(entity.getClass());
        if (tenantField.isEmpty()) {
            return;
        }
        Object entityTenant = read(tenantField.get(), entity);
        if (!Objects.equals(entityTenant, sessionTenant)) {
            throw new TenantIsolationViolationException("Завантажено " + entity.getClass().getSimpleName()
                    + " tenant-а " + entityTenant + " у сесії tenant-а " + sessionTenant);
        }
    }

    private static Object read(Field field, Object entity) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Не вдалося прочитати @TenantId поле " + field, e);
        }
    }

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class)
                .appendListeners(EventType.POST_LOAD, this);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        // Нічого прибирати: listener живе разом із SessionFactory.
    }
}
