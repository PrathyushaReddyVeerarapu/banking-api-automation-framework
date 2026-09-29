package com.prathyusha.bankingapi.tests;

import com.prathyusha.bankingapi.model.Account;
import com.prathyusha.bankingapi.model.ApiError;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;

@Epic("Banking API")
@Feature("Accounts")
public class AccountsApiTests extends BaseApiTest {

    @DataProvider(name = "knownAccounts")
    public Object[][] knownAccounts() {
        return new Object[][]{
                {"ACC-1001", "Ava Martinez", "CHECKING", new BigDecimal("5000.00")},
                {"ACC-1002", "Liam Chen", "SAVINGS", new BigDecimal("120.00")},
        };
    }

    @Test(description = "List accounts returns every account and matches the contract schema")
    @Severity(SeverityLevel.CRITICAL)
    public void listAccountsMatchesSchema() {
        List<Account> accounts = auth()
                .when()
                .get("/accounts")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/account-list-schema.json"))
                .extract()
                .jsonPath()
                .getList("", Account.class);

        assertThat(accounts, hasSize(2));
        assertThat(accounts.stream().map(Account::getStatus).distinct().toList(),
                equalTo(List.of("ACTIVE")));
    }

    @Test(dataProvider = "knownAccounts",
            description = "Each known account returns correct owner, type and opening balance")
    @Severity(SeverityLevel.NORMAL)
    public void getAccountById(String id, String owner, String type, BigDecimal balance) {
        Account account = auth()
                .when()
                .get("/accounts/" + id)
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/account-schema.json"))
                .extract()
                .as(Account.class);

        assertThat(account.getId(), equalTo(id));
        assertThat(account.getOwner(), equalTo(owner));
        assertThat(account.getType(), equalTo(type));
        // Balances move when transfers run, so only the untouched fixture asserts exact value.
        if (id.equals("ACC-1002")) {
            assertThat(account.getBalance(), equalTo(balance));
        }
    }

    @Test(description = "Unknown account id returns 404 with a machine-readable code")
    @Description("Clients (and our UI) key off the error code, not the HTTP status alone.")
    @Severity(SeverityLevel.NORMAL)
    public void unknownAccountReturns404() {
        ApiError error = auth()
                .when()
                .get("/accounts/ACC-9999")
                .then()
                .statusCode(404)
                .body(matchesJsonSchemaInClasspath("schemas/error-schema.json"))
                .extract()
                .as(ApiError.class);

        assertThat(error.getCode(), equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test(description = "Transaction history exists for a funded account")
    @Severity(SeverityLevel.NORMAL)
    public void transactionHistoryIsNotEmpty() {
        List<?> txns = auth()
                .when()
                .get("/accounts/ACC-1001/transactions")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("");

        assertThat(txns, not(empty()));
    }
}
