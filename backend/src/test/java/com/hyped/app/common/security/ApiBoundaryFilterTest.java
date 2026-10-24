package com.hyped.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyped.app.common.api.ApiProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiBoundaryFilterTest {
    private final ApiBoundaryFilter filter = new ApiBoundaryFilter(new ApiProblemWriter(new ObjectMapper()));

    @Test
    void boundsChunkedBodiesAndReturnsSafeHeaders() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/rooms");
        request.setContent(new byte[ApiBoundaryFilter.MAX_BODY + 1]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> res.getWriter().write("unexpected"));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).doesNotContain("unexpected", "Exception");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void preservesExactBytesForVerificationAndMvc() throws Exception {
        var request = new MockHttpServletRequest("PATCH", "/api/v1/rooms/id");
        byte[] body = "{\"title\":\"世界\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(body);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
        });
    }
}
