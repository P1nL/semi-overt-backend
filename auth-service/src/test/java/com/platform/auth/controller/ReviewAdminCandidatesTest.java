package com.platform.auth.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.auth.controller.internal.InternalUserController;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.kernel.enums.UserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewAdminCandidatesTest {
    @Test void authOwnsRoleFilterAndReturnsOnlyPublicSummaries() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),User.class);
        var mapper=mock(UserMapper.class);var admin=new User();admin.setId(2L);admin.setRole(UserRole.ADMIN);
        admin.setUsername("admin");admin.setEmail("private@example.invalid");admin.setPassword("not-exposed");
        when(mapper.selectList(any())).thenReturn(List.of(admin));
        var result=new InternalUserController(mapper).reviewAdmins();
        assertEquals(200,result.getCode());assertEquals(2L,result.getData().get(0).getId());
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<User>> query=ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("role"));
        assertTrue(query.getValue().getSqlSegment().contains("ORDER BY id ASC"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(UserRole.ADMIN));
    }
}
