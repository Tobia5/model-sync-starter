package io.modelsync.spring;

import io.modelsync.core.*;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.*;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.*;
import java.util.concurrent.*;
/** Narrow Spring AOP advisor. No Hibernate callbacks and no remote invocation. */
public final class RequestSyncAdvisor extends AbstractPointcutAdvisor {
    private final SyncGateway gateway; private final LocalTransactions tx;
    private final ConcurrentMap<String,Expression> expressions=new ConcurrentHashMap<>();
    private final Pointcut pointcut=new StaticMethodMatcherPointcut(){
        @Override public boolean matches(Method method,Class<?> targetClass){
            Method actual=AopUtils.getMostSpecificMethod(method,targetClass);
            return AnnotatedElementUtils.hasAnnotation(actual,RequestSync.class);
        }
    };
    public RequestSyncAdvisor(SyncGateway gateway,LocalTransactions tx){this.gateway=gateway;this.tx=tx;}
    @Override public Pointcut getPointcut(){return pointcut;}
    @Override public Advice getAdvice(){return (MethodInterceptor)this::invoke;}
    private Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass=AopUtils.getTargetClass(invocation.getThis());
        Method method=AopUtils.getMostSpecificMethod(invocation.getMethod(),targetClass);
        RequestSync spec=AnnotatedElementUtils.findMergedAnnotation(method,RequestSync.class);
        if(spec==null)return invocation.proceed();
        validate(method,targetClass);
        gateway.binding(spec.binding());
        if(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("@RequestSync cannot mutate inside a read-only transaction");
        // Lock ordering is ledger -> business projection, matching worker commit.
        // The intent remains invisible until this transaction commits; rollback removes it.
        try {
            return tx.required(()->{
                SimpleEvaluationContext context=SimpleEvaluationContext.forReadOnlyDataBinding().build();
                Object[] args=invocation.getArguments();
                for(int i=0;i<args.length;i++){context.setVariable("p"+i,args[i]);context.setVariable("a"+i,args[i]);}
                String key=value(spec.key(),context);String tenant=value(spec.tenant(),context);
                gateway.request(spec.binding(),tenant,key,spec.priority(),spec.force());
                try{return invocation.proceed();}catch(Throwable error){throw new InvocationFailure(error);}
            });
        }catch(InvocationFailure failure){throw failure.getCause();}
    }
    private String value(String source,SimpleEvaluationContext context){
        Object value=expressions.computeIfAbsent(source,s->new SpelExpressionParser().parseExpression(s)).getValue(context);
        if(value==null)throw new IllegalArgumentException("RequestSync expression evaluated to null");
        return value.toString();
    }
    public static void validate(Method method,Class<?> type){
        RequestSync spec=AnnotatedElementUtils.findMergedAnnotation(method,RequestSync.class);
        if(spec!=null && (spec.key().contains("#result") || spec.tenant().contains("#result")))
            throw new IllegalArgumentException("@RequestSync needs input identity; #result cannot preserve ledger-first lock ordering");
        if(!Modifier.isPublic(method.getModifiers()) || Modifier.isFinal(method.getModifiers()))
            throw new IllegalArgumentException("@RequestSync requires a public, non-final method");
        if(AnnotatedElementUtils.hasAnnotation(method,Transactional.class) || AnnotatedElementUtils.hasAnnotation(type,Transactional.class))
            throw new IllegalArgumentException("@RequestSync owns its transaction; do not combine it with @Transactional");
        if(MergedAnnotations.from(method).isPresent("jakarta.transaction.Transactional") || MergedAnnotations.from(type).isPresent("jakarta.transaction.Transactional"))
            throw new IllegalArgumentException("@RequestSync must not be combined with Jakarta @Transactional");
        if(AnnotatedElementUtils.hasAnnotation(method,Async.class) || AnnotatedElementUtils.hasAnnotation(type,Async.class))
            throw new IllegalArgumentException("@RequestSync must not be combined with @Async");
        Class<?> returns=method.getReturnType();
        if(CompletionStage.class.isAssignableFrom(returns) || Future.class.isAssignableFrom(returns)
                || ReactiveAdapterRegistry.getSharedInstance().getAdapter(returns)!=null)
            throw new IllegalArgumentException("@RequestSync is synchronous; use an explicit JPA transaction bridge for reactive code");
    }
    private static final class InvocationFailure extends RuntimeException {
        InvocationFailure(Throwable cause){super(cause);}
    }
}
