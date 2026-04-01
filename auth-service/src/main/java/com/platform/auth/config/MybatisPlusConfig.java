package com.platform.auth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


@Configuration
public class MybatisPlusConfig {

        @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        return interceptor;
    }

        @Component
    public static class AutoFillHandler implements MetaObjectHandler {

    /**
     * 閹笛嗩攽fill閵?     */
        @Override
        public void insertFill(MetaObject metaObject) {
            LocalDateTime now = LocalDateTime.now();
            // INSERT 閺冭泛锝為崗?createdAt 閸?updatedAt
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }

    /**
     * 閺囧瓨鏌奻ill閵?     */
        @Override
        public void updateFill(MetaObject metaObject) {
            // UPDATE 閺冭泛褰ч弴瀛樻煀 updatedAt
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        }
    }
}

