package com.jefiro.app247.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingLeadRequestSizeFilterTest {
    @Test
    void rejeitaPayloadDeclaradoAcimaDoLimite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/leads");
        request.setContent(new byte[(int) MarketingLeadRequestSizeFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MarketingLeadRequestSizeFilter().doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("Solicitação inválida");
    }
}
