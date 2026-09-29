package com.sufiyan.moviebooking.config;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private DocumentContext spec;

    @BeforeEach
    void loadSpec() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        spec = JsonPath.parse(json);
    }

    @Test
    void swaggerUi_isPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    /** Guards against endpoints being added without showing up in Swagger. */
    @Test
    void everyApiEndpoint_isDocumented() {
        List<String> endpoints = apiEndpoints();
        assertThat(endpoints).isNotEmpty();

        List<String> missing = endpoints.stream()
                .filter(e -> {
                    String[] parts = e.split(" ", 2);
                    Map<String, Object> ops = spec.read("$.paths", Map.class);
                    Object pathItem = ops.get(parts[1]);
                    return !(pathItem instanceof Map<?, ?> m) || !m.containsKey(parts[0].toLowerCase(Locale.ROOT));
                })
                .toList();

        assertThat(missing).as("endpoints missing from /v3/api-docs").isEmpty();
    }

    @Test
    void everyOperation_hasSummaryAndTag() {
        for (String endpoint : apiEndpoints()) {
            Map<String, Object> op = operation(endpoint);
            assertThat((String) op.get("summary")).as("summary of " + endpoint).isNotBlank();
            assertThat((List<?>) op.get("tags")).as("tags of " + endpoint).isNotEmpty();
        }
    }

    @Test
    void protectedOperations_declareBearerAuth_andPublicOnesDoNot() {
        for (String endpoint : apiEndpoints()) {
            boolean expectSecured = !isPublic(endpoint);
            List<?> security = (List<?>) operation(endpoint).get("security");
            boolean secured = security != null && security.toString().contains(OpenApiConfig.BEARER_AUTH);
            assertThat(secured).as("bearerAuth on " + endpoint).isEqualTo(expectSecured);
        }
    }

    @Test
    void componentsAndStandardErrors_arePresent() {
        assertThat(spec.read("$.components.securitySchemes.bearerAuth.scheme", String.class)).isEqualTo("bearer");
        assertThat(spec.read("$.components.schemas.ApiError", Map.class)).isNotEmpty();

        assertThat(operation("POST /api/admin/cities").get("responses").toString())
                .contains("400", "401", "403", "409");
        assertThat(operation("GET /api/movies/{id}").get("responses").toString()).contains("404");
    }

    @Test
    void pagedMovieSearch_exposesPageSizeSortParams() {
        List<String> params = spec.read("$.paths['/api/movies'].get.parameters[*].name");
        assertThat(params).contains("title", "language", "genre", "page", "size", "sort");
    }

    @Test
    void authenticationPrincipal_isNotExposedAsParameter() {
        assertThat(operation("GET /api/auth/me").get("parameters")).isNull();
    }

    /** Mirrors the permitAll rules in SecurityConfig. */
    private static boolean isPublic(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        String method = parts[0];
        String path = parts[1];
        if (method.equals("POST")) {
            return path.equals("/api/auth/register") || path.equals("/api/auth/login");
        }
        return method.equals("GET") && (path.startsWith("/api/cities") || path.startsWith("/api/theaters")
                || path.startsWith("/api/movies") || path.startsWith("/api/shows"));
    }

    /** "METHOD /path" for every mapped controller method under /api. */
    private List<String> apiEndpoints() {
        List<String> result = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                if (!path.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    result.add(method.name() + " " + path);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        Map<String, Object> paths = spec.read("$.paths", Map.class);
        Map<String, Object> item = (Map<String, Object>) paths.get(parts[1]);
        assertThat(item).as("path " + parts[1]).isNotNull();
        return (Map<String, Object>) item.get(parts[0].toLowerCase(Locale.ROOT));
    }
}
