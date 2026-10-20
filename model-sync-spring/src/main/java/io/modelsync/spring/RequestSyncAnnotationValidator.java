package io.modelsync.spring;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
/** Fail startup for explicit invalid annotation signatures instead of silently ignoring them. */
public final class RequestSyncAnnotationValidator implements BeanPostProcessor {
    @Override public Object postProcessAfterInitialization(Object bean,String name){
        Class<?> type=AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(type,method->{
            if(AnnotatedElementUtils.hasAnnotation(method,RequestSync.class))RequestSyncAdvisor.validate(method,type);
        });
        return bean;
    }
}
