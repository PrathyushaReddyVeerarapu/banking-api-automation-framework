package com.prathyusha.bankingapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * A tiny in-JVM stub of a banking API.
 *
 * <p>Why not hit a real public API? Demos like Restful Booker change under you,
 * need the internet, and can't model banking rules (holds, insufficient funds).
 * This stub is deterministic, resettable, and encodes real domain logic —
 * balances actually move when a transfer succeeds — so the tests verify
 * behaviour, not canned JSON.
 *
 * <p>Endpoints (all under {@code /api/v1}):
 * <ul>
 *   <li>{@code POST /auth/login} — {@code {"username","password"}} → token</li>
 *   <li>{@code GET /accounts} — list accounts (auth required)</li>
 *   <li>{@code GET /accounts/{id}} — single account (auth required)</li>
 *   <li>{@code GET /accounts/{id}/transactions} — transaction history (auth required)</li>
 *   <li>{@code POST /transfers} — move money (auth required)</li>
 * </ul>
 */
public class StubBankApi {

    public static final String VALID_TOKEN = "demo-token-abc123";

    private static final String USERNAME = "sdet.demo";
    private static final String PASSWORD = "QualityRocks123";

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    /** accountId → balance. Synchronized on transfer to mimic a ledger write. */
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, List<ObjectNode>> transactions = new ConcurrentHashMap<>();

    public StubBankApi(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        seedData();

        server.createContext("/api/v1/auth/login", this::handleLogin);
        server.createContext("/api/v1/accounts", this::handleAccounts);
        server.createContext("/api/v1/transfers", this::handleTransfers);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // seed data
    // ------------------------------------------------------------------

    private void seedData() {
        balances.put("ACC-1001", new BigDecimal("5000.00"));
        balances.put("ACC-1002", new BigDecimal("120.00"));

        transactions.put("ACC-1001", new ArrayList<>(List.of(
                txn("TXN-SEED-1", "ACC-1001", "CREDIT", new BigDecimal("5000.00"), "Initial funding"))));
        transactions.put("ACC-1002", new ArrayList<>(List.of(
                txn("TXN-SEED-2", "ACC-1002", "CREDIT", new BigDecimal("120.00"), "Initial funding"))));
    }

    private ObjectNode txn(String id, String accountId, String type, BigDecimal amount, String reference) {
        ObjectNode n = mapper.createObjectNode();
        n.put("transactionId", id);
        n.put("accountId", accountId);
        n.put("type", type);
        n.put("amount", amount);
        n.put("currency", "USD");
        n.put("reference", reference);
        return n;
    }

    private ObjectNode accountNode(String id) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("owner", id.equals("ACC-1001") ? "Ava Martinez" : "Liam Chen");
        n.put("type", id.equals("ACC-1001") ? "CHECKING" : "SAVINGS");
        n.put("currency", "USD");
        n.put("balance", balances.get(id));
        n.put("status", "ACTIVE");
        return n;
    }

    // ------------------------------------------------------------------
    // handlers
    // ------------------------------------------------------------------

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, error("METHOD_NOT_ALLOWED", "Use POST /auth/login"));
            return;
        }
        ObjectNode body = readBody(ex);
        String user = body.path("username").asText("");
        String pass = body.path("password").asText("");

        if (USERNAME.equals(user) && PASSWORD.equals(pass)) {
            ObjectNode ok = mapper.createObjectNode();
            ok.put("token", VALID_TOKEN);
            ok.put("expiresInSeconds", 3600);
            send(ex, 200, ok);
        } else {
            send(ex, 401, error("AUTH_FAILED", "Invalid username or password"));
        }
    }

    private void handleAccounts(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        String path = ex.getRequestURI().getPath(); // /api/v1/accounts[/...]
        String rest = path.substring("/api/v1/accounts".length());

        if ("GET".equalsIgnoreCase(ex.getRequestMethod()) && (rest.isEmpty() || "/".equals(rest))) {
            ArrayNode arr = mapper.createArrayNode();
            balances.keySet().stream().sorted().forEach(id -> arr.add(accountNode(id)));
            send(ex, 200, arr);
            return;
        }

        // /{id} or /{id}/transactions
        String[] parts = rest.split("/");
        // parts[0] == "" because rest starts with "/"
        if (parts.length >= 2 && "GET".equalsIgnoreCase(ex.getRequestMethod())) {
            String accountId = parts[1];
            if (!balances.containsKey(accountId)) {
                send(ex, 404, error("ACCOUNT_NOT_FOUND", "Account " + accountId + " does not exist"));
                return;
            }
            if (parts.length == 2) {
                send(ex, 200, accountNode(accountId));
                return;
            }
            if (parts.length == 3 && "transactions".equals(parts[2])) {
                ArrayNode arr = mapper.createArrayNode();
                transactions.get(accountId).forEach(arr::add);
                send(ex, 200, arr);
                return;
            }
        }
        send(ex, 404, error("NOT_FOUND", "No such endpoint: " + path));
    }

    private void handleTransfers(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, error("METHOD_NOT_ALLOWED", "Use POST /transfers"));
            return;
        }
        if (!authorized(ex)) {
            return;
        }
        ObjectNode body = readBody(ex);
        String from = body.path("fromAccountId").asText("");
        String to = body.path("toAccountId").asText("");
        BigDecimal amount = body.path("amount").isNumber()
                ? body.path("amount").decimalValue() : null;
        String currency = body.path("currency").asText("USD");
        String reference = body.path("reference").asText("");

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            send(ex, 400, error("INVALID_AMOUNT", "Transfer amount must be greater than zero"));
            return;
        }
        if (!balances.containsKey(from)) {
            send(ex, 404, error("ACCOUNT_NOT_FOUND", "Source account " + from + " does not exist"));
            return;
        }
        if (!balances.containsKey(to)) {
            send(ex, 404, error("ACCOUNT_NOT_FOUND", "Destination account " + to + " does not exist"));
            return;
        }
        if (from.equals(to)) {
            send(ex, 400, error("INVALID_TRANSFER", "Source and destination accounts must differ"));
            return;
        }

        synchronized (balances) {
            if (balances.get(from).compareTo(amount) < 0) {
                send(ex, 422, error("INSUFFICIENT_FUNDS",
                        "Insufficient funds in account " + from));
                return;
            }
            balances.put(from, balances.get(from).subtract(amount));
            balances.put(to, balances.get(to).add(amount));

            String transferId = "TRX-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 8).toUpperCase();
            transactions.get(from).add(txn(transferId + "-D", from, "DEBIT", amount, reference));
            transactions.get(to).add(txn(transferId + "-C", to, "CREDIT", amount, reference));

            ObjectNode ok = mapper.createObjectNode();
            ok.put("transferId", transferId);
            ok.put("fromAccountId", from);
            ok.put("toAccountId", to);
            ok.put("amount", amount);
            ok.put("currency", currency);
            ok.put("status", "COMPLETED");
            send(ex, 201, ok);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private boolean authorized(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (("Bearer " + VALID_TOKEN).equals(auth)) {
            return true;
        }
        send(ex, 401, error("UNAUTHORIZED", "Missing or invalid Authorization header"));
        return false;
    }

    private ObjectNode readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return mapper.createObjectNode();
        }
        return (ObjectNode) mapper.readTree(bytes);
    }

    private ObjectNode error(String code, String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("code", code);
        n.put("message", message);
        return n;
    }

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
