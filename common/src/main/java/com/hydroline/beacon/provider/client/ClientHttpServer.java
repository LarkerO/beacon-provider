package com.hydroline.beacon.provider.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.BeaconProviderMod;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.jce.ECNamedCurveTable;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class ClientHttpServer {
    private static final String SM2_PRIVATE_KEY = "55b23d5e236526e8576404285743210141315574512415152341241512512341"; // Example Key
    private static final int START_PORT = 45210;
    private static final int END_PORT = 45220;
    
    private HttpServer server;
    private final Supplier<JsonObject> clientInfoSupplier;
    private final Gson gson = new Gson();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public ClientHttpServer(Supplier<JsonObject> clientInfoSupplier) {
        this.clientInfoSupplier = clientInfoSupplier;
    }

    public void start() {
        for (int port = START_PORT; port <= END_PORT; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/api/getClientInfo", new ClientInfoHandler());
                server.setExecutor(Executors.newSingleThreadExecutor());
                server.start();
                BeaconProviderMod.LOGGER.info("Client HTTP Server started on port " + port);
                return;
            } catch (IOException e) {
                BeaconProviderMod.LOGGER.warn("Port " + port + " is occupied, trying next...");
            }
        }
        BeaconProviderMod.LOGGER.error("Failed to start Client HTTP Server: All ports " + START_PORT + "-" + END_PORT + " are occupied.");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private class ClientInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                JsonObject info = clientInfoSupplier.get();
                String jsonResponse = gson.toJson(info);
                
                String encryptedResponse = encryptSM2(jsonResponse);
                byte[] responseBytes = encryptedResponse.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                BeaconProviderMod.LOGGER.error("Error handling request", e);
                String errorMsg = "Internal Server Error";
                exchange.sendResponseHeaders(500, errorMsg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private String encryptSM2(String data) throws Exception {
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("sm2p256v1");
        BigInteger privateKeyInt = new BigInteger(SM2_PRIVATE_KEY, 16);
        ECDomainParameters domainParams = new ECDomainParameters(ecSpec.getCurve(), ecSpec.getG(), ecSpec.getN(), ecSpec.getH());
        
        // Derive Public Key from Private Key
        ECPoint q = domainParams.getG().multiply(privateKeyInt).normalize();
        org.bouncycastle.crypto.params.ECPublicKeyParameters publicKeyParams = new org.bouncycastle.crypto.params.ECPublicKeyParameters(
            q, 
            domainParams
        );

        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(true, new ParametersWithRandom(publicKeyParams));
        
        byte[] in = data.getBytes(StandardCharsets.UTF_8);
        byte[] out = engine.processBlock(in, 0, in.length);
        
        return Base64.getEncoder().encodeToString(out);
    }
}
