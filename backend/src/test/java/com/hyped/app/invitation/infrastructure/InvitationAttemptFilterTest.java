package com.hyped.app.invitation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyped.app.common.api.ApiProblemWriter;
import com.hyped.app.common.api.RequestContextFilter;
import com.hyped.app.invitation.api.InvitationAttemptFilter;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InvitationAttemptFilterTest {
    @Test
    void forwardedHeadersDoNotBypassSharedPreviewLimitAndErrorsAreNotCached() throws Exception {
        var problems = new ApiProblemWriter(new com.fasterxml.jackson.databind.ObjectMapper());
        var filter = new InvitationAttemptFilter(Clock.systemUTC(), problems, 10);
        for (int index = 0; index < 11; index++) {
            var request = new MockHttpServletRequest("POST", index % 2 == 0
                    ? "/api/v1/public/invitations/preview" : "/api/v1/public/room-codes/preview");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + index);
            var response = new MockHttpServletResponse();
            new RequestContextFilter().doFilter(request, response,
                    (req, res) -> filter.doFilter(req, res, (ignored, output) -> { }));
            assertThat(response.getStatus()).isEqualTo(index < 10 ? 200 : 429);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            if (index == 10) {
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getContentAsString()).contains("RATE_LIMITED")
                        .doesNotContain("192.0.2.1", "198.51.100");
            }
        }
    }
}
