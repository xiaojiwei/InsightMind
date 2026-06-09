package com.graphinsight.indicator.interceptor;

import com.graphinsight.indicator.model.UserDataLog;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.hibernate.event.internal.DefaultLoadEventListener;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.sql.Timestamp;

@Component
public class UserDataLogListener extends DefaultLoadEventListener implements PostUpdateEventListener, PostInsertEventListener, PostDeleteEventListener {

    private static final String[] OBJECTS_SKIPPED = {"UserDataLog", "DimAllValues", "QueryPlan", "CacheReloadTask"};

    private static final Logger logger = LoggerFactory.getLogger(UserDataLogListener.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void registerListeners() {
        logger.info("register listeners ******");
        SessionFactoryImpl sessionFactoryImpl = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry eventListenerRegistry = sessionFactoryImpl.getServiceRegistry().getService(EventListenerRegistry.class);
        eventListenerRegistry.getEventListenerGroup(EventType.POST_INSERT).appendListener(this);
        eventListenerRegistry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(this);
        eventListenerRegistry.getEventListenerGroup(EventType.POST_DELETE).appendListener(this);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {

        if (!requiresLog(event.getEntity().getClass())) {
            return;
        }

        logger.info("delete start***********");

        long startTime = System.currentTimeMillis();
        StringBuilder fields = new StringBuilder();
        StringBuilder beforeValues = new StringBuilder();
        UserDataLog userDataLog = new UserDataLog();
        userDataLog.setUserId(getUserId());
        userDataLog.setLogId(String.valueOf(event.getId()));
        userDataLog.setFields(fields.toString());
        userDataLog.setBeforeValues(beforeValues.toString());
        userDataLog.setSimpleName(event.getEntity().getClass().getSimpleName());
        userDataLog.setObjectKey(event.getId().toString());
        userDataLog.setOperation("REMOVE");
        userDataLog.setCreateDate(new Timestamp(System.currentTimeMillis()));

        this.persistLog(userDataLog);

        long endTime = System.currentTimeMillis();
        logger.debug("日志时间： " + (endTime - startTime) + "ms");


    }

    private boolean requiresLog(Class<?> clazz) {
        for (String clsName : OBJECTS_SKIPPED) {
            if (clsName.equals(clazz.getSimpleName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {

        if (!requiresLog(event.getEntity().getClass())) {
            return;
        }

        logger.info("insert start***********");

        long startTime = System.currentTimeMillis();
        StringBuilder fields = new StringBuilder();
        StringBuilder afterValues = new StringBuilder();
        StringBuilder remark = new StringBuilder();

        for (int i = 0; i < event.getState().length; i++) {
            fields.append(event.getPersister().getPropertyNames()[i] + "|");
            afterValues.append(event.getState()[i] + "|");
            remark.append(event.getPersister().getPropertyNames()[i] + ": ");
            remark.append(event.getState()[i] + "\r\n");
        }

        UserDataLog userDataLog = new UserDataLog();
        userDataLog.setUserId(getUserId());
        userDataLog.setLogId(String.valueOf(event.getId()));
        userDataLog.setFields(fields.toString());
        userDataLog.setAfterValues(afterValues.toString());
//        userDataLog.setBeforeValues(afterValues.toString());
        userDataLog.setSimpleName(event.getEntity().getClass().getSimpleName());
        userDataLog.setObjectKey(event.getId().toString());
        userDataLog.setOperation("INSERT");
        userDataLog.setCreateDate(new Timestamp(System.currentTimeMillis()));

        this.persistLog(userDataLog);

        long endTime = System.currentTimeMillis();
        logger.debug("日志时间： " + (endTime - startTime) + "ms");

    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {

        if (!requiresLog(event.getEntity().getClass())) {
            return;
        }

        logger.info("update start***********");

        long startTime = System.currentTimeMillis();
        StringBuilder fields = new StringBuilder();
        StringBuilder beforeValues = new StringBuilder();
        StringBuilder afterValues = new StringBuilder();
        StringBuilder remark = new StringBuilder();

        for (int i = 0; i < event.getState().length; i++) {
            // Object oldValue = event.getOldState()[i];
            // Object newValue = event.getState()[i];
            // String propertyName = event.getPersister().getPropertyNames()[i];
            // logger.info(propertyName + ": " + oldValue + " -> " + newValue);
            fields.append(event.getPersister().getPropertyNames()[i] + "|");
            beforeValues.append(event.getOldState()[i] + "|");
            afterValues.append(event.getState()[i] + "|");
        }

        for (int i = 0; i < event.getDirtyProperties().length; i++) {
            int j = event.getDirtyProperties()[i];
            remark.append(event.getPersister().getPropertyNames()[j] + ": ");
            remark.append(event.getOldState()[j] + " -> ");
            remark.append(event.getState()[j] + "\r\n");
        }

        UserDataLog userDataLog = new UserDataLog();
        userDataLog.setUserId(getUserId());
        userDataLog.setLogId(String.valueOf(event.getId()));
        userDataLog.setFields(fields.toString());
        userDataLog.setBeforeValues(beforeValues.toString());
        userDataLog.setAfterValues(afterValues.toString());
        userDataLog.setSimpleName(event.getEntity().getClass().getSimpleName());
        userDataLog.setObjectKey(event.getId().toString());
        userDataLog.setOperation("UPDATE");
        userDataLog.setCreateDate(new Timestamp(System.currentTimeMillis()));

        this.persistLog(userDataLog);

        long endTime = System.currentTimeMillis();
        logger.debug("日志时间： " + (endTime - startTime) + "ms");

    }

    private void persistLog(UserDataLog userDataLog) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(userDataLog);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    @Override
    public boolean requiresPostCommitHanding(EntityPersister entityPersister) {
        return false;
    }

    private String getUserId() {
        return UserThreadLocalUtil.getUserName();
    }


}
