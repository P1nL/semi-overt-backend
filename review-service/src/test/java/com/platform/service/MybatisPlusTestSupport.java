package com.platform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MyBatisPlusTestSupport业务接口，定义对外暴露的服务能力。
 */

final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    static void initLambdaCache(Class<?>... entityClasses) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test-mapper");
        for (Class<?> entityClass : entityClasses) {
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
            if (tableInfo == null) {
                tableInfo = TableInfoHelper.initTableInfo(assistant, entityClass);
            }
            LambdaUtils.installCache(tableInfo);
        }
    }
}
