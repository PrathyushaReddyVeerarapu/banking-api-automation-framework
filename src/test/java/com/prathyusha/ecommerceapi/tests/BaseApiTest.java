package com.prathyusha.ecommerceapi.tests;

import com.prathyusha.ecommerceapi.client.ApiClient;
import com.prathyusha.ecommerceapi.config.FrameworkConfig;
import com.prathyusha.ecommerceapi.model.AuthToken;
import com.prathyusha.ecommerceapi.model.LoginRequest;
import com.prathyusha.ecommerceapi.utils.StubStoreApi;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;

/**
 * Boots the stub store API once per suite and logs in once per test class.
 * Every test class extends this — no test ever worries about setup.
 */
public abstract class BaseApiTest {

    private static StubStoreApi stub;
    private static boolean started = false;

    protected String token;

    @BeforeSuite(alwaysRun = true)
    public void startStubStoreApi() throws IOException {
        if (!started) {
            stub = new StubStoreApi(FrameworkConfig.mockPort());
            stub.start();
            started = true;
        }
    }

    @AfterSuite(alwaysRun = true)
    public void stopStubStoreApi() {
        if (stub != null) {
            stub.stop();
            stub = null;
            started = false;
        }
    }

    @BeforeClass(alwaysRun = true)
    public void login() {
        AuthToken auth = ApiClient.anonymous()
                .body(new LoginRequest(FrameworkConfig.username(), FrameworkConfig.password()))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .as(AuthToken.class);

        assertThat("login must return a token", auth.getToken(), not(isEmptyOrNullString()));
        token = auth.getToken();
    }

    protected RequestSpecification auth() {
        return ApiClient.authenticated(token);
    }
}
