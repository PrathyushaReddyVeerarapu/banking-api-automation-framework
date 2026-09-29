package com.prathyusha.bankingapi.config;

/**
 * Central configuration for the framework.
 *
 * <p>Values can be overridden without touching code, e.g.:
 * <pre>
 * mvn test -Dapi.baseUri=https://banking-api.qa.example.com -Dapi.basePath=/api/v1
 * </pre>
 */
public final class FrameworkConfig {

    private FrameworkConfig() {
        // utility class
    }

    public static String baseUri() {
        return System.getProperty("api.baseUri", "http://localhost:8089");
    }

    public static String basePath() {
        return System.getProperty("api.basePath", "/api/v1");
    }

    public static String username() {
        return System.getProperty("api.username", "sdet.demo");
    }

    public static String password() {
        return System.getProperty("api.password", "QualityRocks123");
    }

    /** WireMock port used by the self-contained stub banking API. */
    public static int mockPort() {
        return Integer.parseInt(System.getProperty("mock.port", "8089"));
    }
}
