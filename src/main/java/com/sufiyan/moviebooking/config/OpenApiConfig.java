package com.sufiyan.moviebooking.config;

import com.sufiyan.moviebooking.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * OpenAPI / Swagger UI setup: available at /swagger-ui.html (spec at /v3/api-docs).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final String ERROR_REF = "#/components/schemas/ApiError";

    @Bean
    OpenAPI movieBookingOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken from POST /api/auth/login"));

        return new OpenAPI()
                .info(new Info()
                        .title("Movie Ticket Booking API")
                        .version("v1")
                        .description("""
                                Cities, theaters, screens, movies, shows and seat-level booking.

                                **Auth:** call `POST /api/auth/login`, copy `accessToken`, click **Authorize**
                                and paste it. Admin endpoints need an ADMIN token; browsing is public.

                                Every error uses the `ApiError` shape with a stable `error` code."""))
                .components(components);
    }

    /**
     * Adds the standard error responses to every operation so each endpoint documents its failure modes:
     * 400 when it takes input, 401/403 when it needs a token, 404 for id paths, 409 for writes.
     */
    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            // Registered here (after generation) because springdoc rebuilds the schema list from controllers.
            ModelConverters.getInstance().readAll(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
                    .forEach((method, operation) -> addErrors(path, method, operation)));
        };
    }

    private static void addErrors(String path, PathItem.HttpMethod method, Operation op) {
        ApiResponses responses = op.getResponses();
        boolean secured = op.getSecurity() != null && !op.getSecurity().isEmpty();
        boolean takesInput = op.getRequestBody() != null || (op.getParameters() != null && !op.getParameters().isEmpty());
        boolean write = method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.DELETE;

        if (takesInput) {
            putIfAbsent(responses, "400", "Validation failed or malformed request");
        }
        if (secured) {
            putIfAbsent(responses, "401", "Missing, invalid or expired token");
        }
        if (path.startsWith("/api/admin/")) {
            putIfAbsent(responses, "403", "Caller is not an ADMIN");
        }
        if (path.contains("{")) {
            putIfAbsent(responses, "404", "Resource not found");
        }
        if (write && !path.equals("/api/auth/login")) {
            putIfAbsent(responses, "409", "Conflicts with existing data (duplicate, has dependants, ...)");
        }
    }

    private static void putIfAbsent(ApiResponses responses, String code, String description) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse()
                    .description(description)
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref(ERROR_REF)))));
        }
    }
}
