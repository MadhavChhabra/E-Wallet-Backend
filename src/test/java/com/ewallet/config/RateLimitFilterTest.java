package com.ewallet.config;

import com.ewallet.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    /** Three logins per window: the three reset calls use the budget and the fourth is refused. */
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitService(3, 30, 60));

    @Test
    void passwordResetEndpointsShareTheLoginLimit() throws Exception {
        for (String path : new String[]{"request", "verify", "confirm"}) {
            assertThat(call("/api/v1/auth/password-reset/" + path)).isEqualTo(200);
        }
        assertThat(call("/api/v1/auth/password-reset/verify")).isEqualTo(429);
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("/api/v1/bank-accounts")).isEqualTo(200);
        }
    }

    private int call(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
