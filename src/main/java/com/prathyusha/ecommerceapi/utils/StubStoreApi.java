package com.prathyusha.ecommerceapi.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * A tiny in-JVM stub of an e-commerce store API.
 *
 * <p>Why not hit a real public API? Demos change under you, need the internet,
 * and can't model store rules (out-of-stock, invalid quantities). This stub is
 * deterministic, resettable, and encodes real domain logic — inventory actually
 * decrements when an order succeeds — so the tests verify behaviour, not
 * canned JSON.
 *
 * <p>Endpoints (all under {@code /api/v1}):
 * <ul>
 *   <li>{@code POST /auth/login} — {@code {"username","password"}} → token</li>
 *   <li>{@code GET /products} — product catalog (auth required)</li>
 *   <li>{@code GET /products/{id}} — single product (auth required)</li>
 *   <li>{@code GET /orders} — order history (auth required)</li>
 *   <li>{@code GET /orders/{id}} — single order (auth required)</li>
 *   <li>{@code POST /orders} — place an order, decrements stock (auth required)</li>
 * </ul>
 */
public class StubStoreApi {

    public static final String VALID_TOKEN = "demo-token-abc123";

    private static final String USERNAME = "sdet.demo";
    private static final String PASSWORD = "QualityRocks123";

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, ProductDef> catalog = new LinkedHashMap<>();
    /** productId → units on hand. Synchronized on order placement to mimic an inventory write. */
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final List<ObjectNode> orders = Collections.synchronizedList(new ArrayList<>());

    private record ProductDef(String name, String category, BigDecimal price) {
    }

    public StubStoreApi(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        seedData();

        server.createContext("/api/v1/auth/login", this::handleLogin);
        server.createContext("/api/v1/products", this::handleProducts);
        server.createContext("/api/v1/orders", this::handleOrders);
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
        catalog.put("PROD-101", new ProductDef("Wireless Headphones", "AUDIO", new BigDecimal("149.99")));
        catalog.put("PROD-102", new ProductDef("Mechanical Keyboard", "COMPUTING", new BigDecimal("89.99")));
        catalog.put("PROD-103", new ProductDef("USB-C Hub", "COMPUTING", new BigDecimal("39.99")));

        stock.put("PROD-101", 50);
        stock.put("PROD-102", 30);
        stock.put("PROD-103", 0); // deliberately out of stock — negative-path fixture

        // One historical order so order history is never empty.
        orders.add(orderNode("ORD-SEED0001", "PROD-102", 2, new BigDecimal("89.99"), "CONFIRMED"));
    }

    private ObjectNode productNode(String id) {
        ProductDef def = catalog.get(id);
        int units = stock.get(id);
        ObjectNode n = mapper.createObjectNode();
        n.put("id", id);
        n.put("name", def.name());
        n.put("category", def.category());
        n.put("price", def.price());
        n.put("currency", "USD");
        n.put("stock", units);
        n.put("status", units == 0 ? "OUT_OF_STOCK" : "ACTIVE");
        return n;
    }

    private ObjectNode orderNode(String orderId, String productId, int quantity,
                                 BigDecimal unitPrice, String status) {
        ObjectNode n = mapper.createObjectNode();
        n.put("orderId", orderId);
        n.put("productId", productId);
        n.put("quantity", quantity);
        n.put("unitPrice", unitPrice);
        n.put("totalAmount", unitPrice.multiply(BigDecimal.valueOf(quantity)));
        n.put("currency", "USD");
        n.put("status", status);
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

    private void handleProducts(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, error("METHOD_NOT_ALLOWED", "Use GET /products"));
            return;
        }
        String path = ex.getRequestURI().getPath(); // /api/v1/products[/...]
        String rest = path.substring("/api/v1/products".length());

        if (rest.isEmpty() || "/".equals(rest)) {
            ArrayNode arr = mapper.createArrayNode();
            catalog.keySet().forEach(id -> arr.add(productNode(id)));
            send(ex, 200, arr);
            return;
        }

        // /{id}
        String[] parts = rest.split("/");
        // parts[0] == "" because rest starts with "/"
        if (parts.length == 2) {
            String productId = parts[1];
            if (!catalog.containsKey(productId)) {
                send(ex, 404, error("PRODUCT_NOT_FOUND", "Product " + productId + " does not exist"));
                return;
            }
            send(ex, 200, productNode(productId));
            return;
        }
        send(ex, 404, error("NOT_FOUND", "No such endpoint: " + path));
    }

    private void handleOrders(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        String method = ex.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            String path = ex.getRequestURI().getPath(); // /api/v1/orders[/...]
            String rest = path.substring("/api/v1/orders".length());

            if (rest.isEmpty() || "/".equals(rest)) {
                ArrayNode arr = mapper.createArrayNode();
                synchronized (orders) {
                    orders.forEach(arr::add);
                }
                send(ex, 200, arr);
                return;
            }

            String[] parts = rest.split("/");
            if (parts.length == 2) {
                String orderId = parts[1];
                synchronized (orders) {
                    for (ObjectNode o : orders) {
                        if (orderId.equals(o.path("orderId").asText(""))) {
                            send(ex, 200, o);
                            return;
                        }
                    }
                }
                send(ex, 404, error("ORDER_NOT_FOUND", "Order " + orderId + " does not exist"));
                return;
            }
            send(ex, 404, error("NOT_FOUND", "No such endpoint: " + path));
            return;
        }

        if (!"POST".equalsIgnoreCase(method)) {
            send(ex, 405, error("METHOD_NOT_ALLOWED", "Use POST /orders"));
            return;
        }

        ObjectNode body = readBody(ex);
        String productId = body.path("productId").asText("");
        int quantity = body.path("quantity").isInt() ? body.path("quantity").asInt() : -1;

        if (quantity <= 0) {
            send(ex, 400, error("INVALID_QUANTITY", "Order quantity must be greater than zero"));
            return;
        }
        if (!catalog.containsKey(productId)) {
            send(ex, 404, error("PRODUCT_NOT_FOUND", "Product " + productId + " does not exist"));
            return;
        }

        synchronized (stock) {
            if (stock.get(productId) < quantity) {
                send(ex, 422, error("INSUFFICIENT_STOCK",
                        "Only " + stock.get(productId) + " unit(s) of " + productId + " in stock"));
                return;
            }
            stock.put(productId, stock.get(productId) - quantity);

            String orderId = "ORD-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 8).toUpperCase();
            ObjectNode order = orderNode(orderId, productId, quantity,
                    catalog.get(productId).price(), "CONFIRMED");
            orders.add(order);
            send(ex, 201, order);
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
