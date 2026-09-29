package com.prathyusha.ecommerceapi.tests;

import com.prathyusha.ecommerceapi.client.ApiClient;
import com.prathyusha.ecommerceapi.config.FrameworkConfig;
import com.prathyusha.ecommerceapi.model.ApiError;
import com.prathyusha.ecommerceapi.model.AuthToken;
import com.prathyusha.ecommerceapi.model.LoginRequest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;

@Epic("Store API")
@Feature("Authentication")
public class AuthApiTests extends BaseApiTest {

    @Test(description = "Valid credentials return a bearer token")
    @Description("Happy-path login — the token issued here authenticates every other test in the suite.")
    @Severity(SeverityLevel.BLOCKER)
    public void validCredentialsReturnToken() {
        AuthToken auth = ApiClient.anonymous()
                .body(new LoginRequest(FrameworkConfig.username(), FrameworkConfig.password()))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/auth-schema.json"))
                .extract()
                .as(AuthToken.class);

        assertThat(auth.getToken(), not(isEmptyOrNullString()));
    }

    @Test(description = "Wrong password is rejected with 401 + machine-readable error code")
    @Severity(SeverityLevel.CRITICAL)
    public void wrongPasswordReturns401() {
        ApiError error = ApiClient.anonymous()
                .body(new LoginRequest(FrameworkConfig.username(), "wrong-password"))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body(matchesJsonSchemaInClasspath("schemas/error-schema.json"))
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo("AUTH_FAILED"));
    }

    @Test(description = "Unknown user is rejected with 401")
    @Severity(SeverityLevel.CRITICAL)
    public void unknownUserReturns401() {
        ApiClient.anonymous()
                .body(new LoginRequest("nobody.here", "whatever"))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test(description = "Protected endpoints reject requests without a token")
    @Description("Security regression guard: no Authorization header → 401, never data.")
    @Severity(SeverityLevel.CRITICAL)
    public void protectedEndpointWithoutTokenReturns401() {
        ApiError error = ApiClient.anonymous()
                .when()
                .get("/products")
                .then()
                .statusCode(401)
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo("UNAUTHORIZED"));
    }

    @Test(description = "Protected endpoints reject a forged token")
    @Severity(SeverityLevel.CRITICAL)
    public void protectedEndpointWithBadTokenReturns401() {
        ApiClient.authenticated("forged-token")
                .when()
                .get("/products")
                .then()
                .statusCode(401);
    }
}
