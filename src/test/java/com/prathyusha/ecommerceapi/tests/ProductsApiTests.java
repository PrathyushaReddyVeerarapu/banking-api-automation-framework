package com.prathyusha.ecommerceapi.tests;

import com.prathyusha.ecommerceapi.model.ApiError;
import com.prathyusha.ecommerceapi.model.Product;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Epic("Store API")
@Feature("Products")
public class ProductsApiTests extends BaseApiTest {

    @DataProvider(name = "knownProducts")
    public Object[][] knownProducts() {
        return new Object[][]{
                {"PROD-101", "Wireless Headphones", "AUDIO", new BigDecimal("149.99")},
                {"PROD-102", "Mechanical Keyboard", "COMPUTING", new BigDecimal("89.99")},
                {"PROD-103", "USB-C Hub", "COMPUTING", new BigDecimal("39.99")},
        };
    }

    @Test(description = "List products returns the full catalog and matches the contract schema")
    @Severity(SeverityLevel.CRITICAL)
    public void listProductsMatchesSchema() {
        List<Product> products = auth()
                .when()
                .get("/products")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/product-list-schema.json"))
                .extract()
                .jsonPath()
                .getList("", Product.class);

        assertThat(products, hasSize(3));
    }

    @Test(dataProvider = "knownProducts",
            description = "Each known product returns correct name, category and price")
    @Severity(SeverityLevel.NORMAL)
    public void getProductById(String id, String name, String category, BigDecimal price) {
        Product product = auth()
                .when()
                .get("/products/" + id)
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/product-schema.json"))
                .extract()
                .as(Product.class);

        assertThat(product.getId(), equalTo(id));
        assertThat(product.getName(), equalTo(name));
        assertThat(product.getCategory(), equalTo(category));
        assertThat(product.getPrice(), equalTo(price));
        // Stock moves when orders run, so only untouched fixtures assert exact values.
        if (id.equals("PROD-102")) {
            assertThat(product.getStock(), equalTo(30));
        }
        if (id.equals("PROD-103")) {
            assertThat(product.getStock(), equalTo(0));
            assertThat(product.getStatus(), equalTo("OUT_OF_STOCK"));
        }
    }

    @Test(description = "Unknown product id returns 404 with a machine-readable code")
    @Description("Clients (and our UI) key off the error code, not the HTTP status alone.")
    @Severity(SeverityLevel.NORMAL)
    public void unknownProductReturns404() {
        ApiError error = auth()
                .when()
                .get("/products/PROD-9999")
                .then()
                .statusCode(404)
                .body(matchesJsonSchemaInClasspath("schemas/error-schema.json"))
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo("PRODUCT_NOT_FOUND"));
    }
}
