package com.platform.events.config;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

import java.util.Set;

@Configuration
@EnableRabbit
@Import(RabbitEventConfig.class)
@ComponentScan(basePackages = "com.platform.events.support")
public class PlatformEventsConfig {

    private static final String RABBIT_LISTENER = RabbitListener.class.getName();
    private static final String REGISTRY_BEAN_NAME =
            "org.springframework.amqp.rabbit.config.internalRabbitListenerEndpointRegistry";

    /**
     * 全局懒加载模式下，@RabbitListener Bean 和 RabbitListenerEndpointRegistry 必须在启动时创建，
     * 否则消费者不会注册到 RabbitMQ。
     * <p>
     * 通过 AnnotatedBeanDefinition 的元数据判断，不做 Class.forName，不拉起依赖链。
     */
    @Bean
    static BeanFactoryPostProcessor rabbitListenerEagerInitPostProcessor() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                for (String beanName : beanFactory.getBeanDefinitionNames()) {
                    BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                    if (!bd.isLazyInit()) {
                        continue;
                    }
                    // Registry 必须参与 SmartLifecycle 启动
                    if (REGISTRY_BEAN_NAME.equals(beanName)) {
                        bd.setLazyInit(false);
                        continue;
                    }
                    // 通过注解元数据判断是否有 @RabbitListener（不实例化 Bean）
                    if (bd instanceof AnnotatedBeanDefinition abd) {
                        if (hasRabbitListenerMetadata(abd)) {
                            bd.setLazyInit(false);
                        }
                    }
                }
            }

            private boolean hasRabbitListenerMetadata(AnnotatedBeanDefinition abd) {
                // 检查类级别
                AnnotationMetadata metadata = abd.getMetadata();
                if (metadata.hasAnnotation(RABBIT_LISTENER) || metadata.hasMetaAnnotation(RABBIT_LISTENER)) {
                    return true;
                }
                // 检查方法级别
                Set<MethodMetadata> methods = metadata.getAnnotatedMethods(RABBIT_LISTENER);
                return methods != null && !methods.isEmpty();
            }
        };
    }
}
