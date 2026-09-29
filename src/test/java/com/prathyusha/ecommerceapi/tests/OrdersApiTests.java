package com.prathyusha.ecommerceapi.tests;

import com.prathyusha.ecommerceapi.model.ApiError;
import com.prathyusha.ecommerceapi.model.OrderRequest;
import com.prathyusha.ecommerceapi.model.OrderResult;
import com.prathyusha.ecommerceapi.model.Product;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Step;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.empty;

@Epic("Store API")
@Feature("Orders")
public class OrdersApiTests extends BaseApiTest {

    /**
     * Negative order scenarios live in a CSV so new cases are added
     * without touching Java: productId,quantity,expectedStatus,expectedCode
     */
    @DataProvider(name = "negativeOrders")
    public Object[][] negativeOrders() throws IOException, URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("testdata/negative-orders.csv");
        List<String> lines = Files.readAllLines(Path.of(resource.toURI()));
        return lines.stream()
                .skip(1) // header
                .filter(l -> !l.isBlank())
                .map(l -> (Object[]) l.split(","))
                .toList()
                .toArray(new Object[0][]);
    }

    @Test(description = "A valid order decrements inventory and totals correctly")
    @Description("End-to-end stock movement: inventory is read before and after, so this test "
            + "passes regardless of execution order — no test depends on seeded stock values.")
    @Severity(SeverityLevel.BLOCKER)
    public void successfulOrderDecrementsStock() {
        int stockBefore = stockOf("PROD-101");
        int quantity = 2;

        OrderResult result = auth()
                .body(new OrderRequest("PROD-101", quantity))
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/order-schema.json"))
                .extract()
                .as(OrderResult.class);

        assertThat(result.getOrderId(), matchesPattern("ORD-[A-Z0-9]{8}"));
        assertThat(result.getStatus(), equalTo("CONFIRMED"));
        assertThat(result.getProductId(), equalTo("PROD-101"));
        assertThat(result.getQuantity(), equalTo(quantity));
        assertThat(result.getTotalAmount(), equalTo(new BigDecimal("149.99").multiply(BigDecimal.valueOf(quantity))));

        // The inventory actually moved — this is the assertion that matters.
        assertThat(stockOf("PROD-101"), equalTo(stockBefore - quantity));
    }

    @Test(dataProvider = "negativeOrders",
            description = "Invalid orders are rejected with the right status and error code")
    @Severity(SeverityLevel.CRITICAL)
    public void invalidOrdersAreRejected(String productId, String quantity,
                                         String expectedStatus, String expectedCode) {
        ApiError error = auth()
                .body(new OrderRequest(productId, Integer.parseInt(quantity)))
                .when()
                .post("/orders")
                .then()
                .statusCode(Integer.parseInt(expectedStatus))
                .body(matchesJsonSchemaInClasspath("schemas/error-schema.json"))
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo(expectedCode));
        assertThat(error.getMessage(), not(isEmptyOrNullString()));
    }

    @Test(description = "Order without auth is rejected before any validation runs")
    @Severity(SeverityLevel.CRITICAL)
    public void orderWithoutAuthReturns401() {
        // Anonymous spec on purpose — must stay 401, not 400/404.
        com.prathyusha.ecommerceapi.client.ApiClient.anonymous()
                .body(new OrderRequest("PROD-101", 1))
                .when()
                .post("/orders")
                .then()
                .statusCode(401);
    }

    @Test(description = "Order history exists for the store")
    @Severity(SeverityLevel.NORMAL)
    public void orderHistoryIsNotEmpty() {
        List<?> orders = auth()
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("");

        assertThat(orders, not(empty()));
    }

    @Step("Read current stock of {productId}")
    private int stockOf(String productId) {
        return auth()
                .when()
                .get("/products/" + productId)
                .then()
                .statusCode(200)
                .extract()
                .as(Product.class)
                .getStock();
    }
}
