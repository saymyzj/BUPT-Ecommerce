package com.bupt.ecommerce.gateway.filter;

import com.bupt.ecommerce.common.security.JwtTokenUtil;
import com.bupt.ecommerce.common.security.RequestUser;
import com.bupt.ecommerce.common.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenUtilTest {

    @Test
    void shouldGenerateAndParseToken() {
        String token = JwtTokenUtil.generateToken(10001L, "alice", Role.CUSTOMER, Duration.ofMinutes(10), "secret");
        RequestUser requestUser = JwtTokenUtil.parseToken(token, "secret");

        assertEquals(10001L, requestUser.userId());
        assertEquals("alice", requestUser.username());
        assertEquals(Role.CUSTOMER, requestUser.role());
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = JwtTokenUtil.generateToken(10001L, "alice", Role.CUSTOMER, Duration.ofSeconds(-1), "secret");
        assertThrows(IllegalArgumentException.class, () -> JwtTokenUtil.parseToken(token, "secret"));
    }
}
