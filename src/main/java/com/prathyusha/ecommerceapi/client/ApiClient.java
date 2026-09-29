package com.prathyusha.ecommerceapi.client;

import com.prathyusha.ecommerceapi.config.FrameworkConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Single place where every request specification is built.
 *
 * <p>Centralising this keeps auth headers, base URIs, logging and the
 * Allure filter consistent across the whole suite — no copy-pasted
 * {@code given()} blocks in test classes.
 */
public final class ApiClient {

    private ApiClient() {
        // utility class
    }

    /** Unauthenticated spec: used for login and negative auth tests. */
    public static RequestSpecification anonymous() {
        return given()
                .filter(new AllureRestAssured())
                .baseUri(FrameworkConfig.baseUri())
                .basePath(FrameworkConfig.basePath())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .log().ifValidationFails(LogDetail.ALL);
    }

    /** Authenticated spec carrying a Bearer token. */
    public static RequestSpecification authenticated(String bearerToken) {
        return anonymous()
                .header("Authorization", "Bearer " + bearerToken);
    }
}
