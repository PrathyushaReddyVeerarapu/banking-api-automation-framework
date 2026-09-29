package com.prathyusha.bankingapi.tests;

import com.prathyusha.bankingapi.model.Account;
import com.prathyusha.bankingapi.model.ApiError;
import com.prathyusha.bankingapi.model.TransferRequest;
import com.prathyusha.bankingapi.model.TransferResult;
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

@Epic("Banking API")
@Feature("Transfers")
public class TransfersApiTests extends BaseApiTest {

    /**
     * Negative transfer scenarios live in a CSV so new cases are added
     * without touching Java: from,to,amount,expectedStatus,expectedCode
     */
    @DataProvider(name = "negativeTransfers")
    public Object[][] negativeTransfers() throws IOException, URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("testdata/negative-transfers.csv");
        List<String> lines = Files.readAllLines(Path.of(resource.toURI()));
        return lines.stream()
                .skip(1) // header
                .filter(l -> !l.isBlank())
                .map(l -> (Object[]) l.split(","))
                .toList()
                .toArray(new Object[0][]);
    }

    @Test(description = "A valid transfer debits the source and credits the destination")
    @Description("End-to-end money movement: balances are read before and after, so this test "
            + "passes regardless of execution order — no test depends on seeded balances.")
    @Severity(SeverityLevel.BLOCKER)
    public void successfulTransferMovesMoney() {
        BigDecimal beforeFrom = balanceOf("ACC-1001");
        BigDecimal beforeTo = balanceOf("ACC-1002");
        BigDecimal amount = new BigDecimal("250.00");

        TransferResult result = auth()
                .body(new TransferRequest("ACC-1001", "ACC-1002", amount, "USD", "rent split"))
                .when()
                .post("/transfers")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/transfer-schema.json"))
                .extract()
                .as(TransferResult.class);

        assertThat(result.getTransferId(), matchesPattern("TRX-[A-Z0-9]{8}"));
        assertThat(result.getStatus(), equalTo("COMPLETED"));
        assertThat(result.getFromAccountId(), equalTo("ACC-1001"));
        assertThat(result.getToAccountId(), equalTo("ACC-1002"));

        // The ledger actually moved — this is the assertion that matters.
        assertThat(balanceOf("ACC-1001"), equalTo(beforeFrom.subtract(amount)));
        assertThat(balanceOf("ACC-1002"), equalTo(beforeTo.add(amount)));
    }

    @Test(dataProvider = "negativeTransfers",
            description = "Invalid transfers are rejected with the right status and error code")
    @Severity(SeverityLevel.CRITICAL)
    public void invalidTransfersAreRejected(String from, String to, String amount,
                                            String expectedStatus, String expectedCode) {
        ApiError error = auth()
                .body(new TransferRequest(from, to, new BigDecimal(amount), "USD", "csv-case"))
                .when()
                .post("/transfers")
                .then()
                .statusCode(Integer.parseInt(expectedStatus))
                .body(matchesJsonSchemaInClasspath("schemas/error-schema.json"))
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo(expectedCode));
        assertThat(error.getMessage(), not(isEmptyOrNullString()));
    }

    @Test(description = "Transfer without auth is rejected before any validation runs")
    @Severity(SeverityLevel.CRITICAL)
    public void transferWithoutAuthReturns401() {
        // Anonymous spec on purpose — must stay 401, not 400/404.
        com.prathyusha.bankingapi.client.ApiClient.anonymous()
                .body(new TransferRequest("ACC-1001", "ACC-1002", new BigDecimal("10.00"), "USD", "x"))
                .when()
                .post("/transfers")
                .then()
                .statusCode(401);
    }

    @Step("Read current balance of {accountId}")
    private BigDecimal balanceOf(String accountId) {
        return auth()
                .when()
                .get("/accounts/" + accountId)
                .then()
                .statusCode(200)
                .extract()
                .as(Account.class)
                .getBalance();
    }
}
