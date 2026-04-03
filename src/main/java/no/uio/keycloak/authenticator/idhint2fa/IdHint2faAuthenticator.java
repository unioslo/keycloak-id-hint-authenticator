/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.uio.keycloak.authenticator.idhint2fa;

import org.jboss.logging.Logger;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.crypto.SignatureVerifierContext;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.AsymmetricSignatureVerifierContext;
import org.keycloak.util.JsonSerialization;
import org.keycloak.representations.JsonWebToken;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;
/**
 * @author <a href="mailto:franciaa@uio.no">Francis Augusto Medeiros-Logeay</a>
 * @version $Revision: 1 $
 * Based on original code by <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 */

public class IdHint2faAuthenticator implements Authenticator {
    private static final Logger logger = Logger.getLogger(IdHint2faAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        try {
            AuthenticatorConfigModel config = context.getAuthenticatorConfig();

            if (config == null) {
                logger.error("Authenticator configuration is missing");
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
                return;
            }

            // Get configuration
            String attributeName = config.getConfig().get("attribute-matching");
            String jwksUri = config.getConfig().get("jkws-uri");

            if (jwksUri == null || jwksUri.isEmpty()) {
                logger.error("JWKS URI is not configured");
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
                return;
            }

            if (attributeName == null || attributeName.isEmpty()) {
                logger.error("Attribute matching is not configured");
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
                return;
            }

            // 1 - Get the id_token_hint from POST parameters
            MultivaluedMap<String, String> formParams = context.getHttpRequest().getDecodedFormParameters();
            String idTokenHint = formParams.getFirst("id_token_hint");

            if (idTokenHint == null || idTokenHint.isEmpty()) {
                logger.error("id_token_hint parameter is missing");
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            // 2 - Decode the JWT token
            JWSInput jwsInput;
            try {
                jwsInput = new JWSInput(idTokenHint);
            } catch (JWSInputException e) {
                logger.error("Failed to parse id_token_hint", e);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            JsonWebToken token;
            try {
                token = JsonSerialization.readValue(jwsInput.getContent(), JsonWebToken.class);
            } catch (Exception e) {
                logger.error("Failed to deserialize JWT token", e);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            // 3 - Validate the signature against the JWKS URI
            String kid = jwsInput.getHeader().getKeyId();
            if (kid == null || kid.isEmpty()) {
                logger.error("Token does not contain kid (key ID) header");
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            PublicKey publicKey = fetchPublicKeyFromJwks(jwksUri, kid);
            if (publicKey == null) {
                logger.error("Could not find public key with kid: " + kid);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            // Verify signature
            KeyWrapper keyWrapper = new KeyWrapper();
            keyWrapper.setKid(kid);
            keyWrapper.setPublicKey(publicKey);
            keyWrapper.setAlgorithm(jwsInput.getHeader().getRawAlgorithm());

            SignatureVerifierContext verifier = new AsymmetricSignatureVerifierContext(keyWrapper);
            boolean signatureValid = verifier.verify(jwsInput.getEncodedSignatureInput().getBytes(), jwsInput.getSignature());

            if (!signatureValid) {
                logger.error("Token signature validation failed");
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            // 4 - Search for the user by the configured attribute
            String claimValue = getClaimFromToken(token, "preferred_username");
            if (claimValue == null || claimValue.isEmpty()) {
                logger.error("Claim 'preferred_username' not found in token");
                context.failure(AuthenticationFlowError.UNKNOWN_USER);
                return;
            }

            UserModel user = findUserByAttribute(context, attributeName, claimValue);
            if (user == null) {
                logger.error("User not found with " + attributeName + "=" + claimValue);
                context.cancelLogin();
                //context.failure(AuthenticationFlowError.UNKNOWN_USER);
                return;
            }

            // After validating the token and finding the user:
            // Extract the sub claim from the incoming id_token_hint
            String sub = token.getSubject(); // or getClaimFromToken(token, "sub")

            if (sub != null && !sub.isEmpty()) {
                // Save it to the authentication session
                context.getAuthenticationSession().setUserSessionNote("microsoft_sub", sub);
            }
            // 5 - Return success
            context.setUser(user);
            context.success();

        } catch (Exception e) {
            logger.error("Unexpected error during authentication", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    private String getClaimFromToken(JsonWebToken token, String claimName) {
        // Check standard claims first
        if ("sub".equals(claimName) && token.getSubject() != null) {
            return token.getSubject();
        }

        // Check other claims
        Object claim = token.getOtherClaims().get(claimName);
        if (claim != null) {
            return claim.toString();
        }

        return null;
    }

    private UserModel findUserByAttribute(AuthenticationFlowContext context, String attributeName, String attributeValue) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // Search by standard attributes
        if ("email".equals(attributeName) || "preferred_username".equals(attributeName)) {
            UserModel user = session.users().getUserByEmail(realm, attributeValue);
            if (user != null) return user;

            user = session.users().getUserByUsername(realm, attributeValue);
            if (user != null) return user;
        }

        // Search by custom attribute
        List<UserModel> users = session.users().searchForUserByUserAttributeStream(realm, attributeName, attributeValue)
                .toList();

        if (users.isEmpty()) {
            return null;
        }

        if (users.size() > 1) {
            logger.warn("Multiple users found with " + attributeName + "=" + attributeValue + ", using first match");
        }

        return users.get(0);
    }

    private PublicKey fetchPublicKeyFromJwks(String jwksUri, String kid) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Failed to fetch JWKS, status code: " + response.statusCode());
                return null;
            }

            JSONObject jwks = new JSONObject(response.body());
            JSONArray keys = jwks.getJSONArray("keys");

            for (int i = 0; i < keys.length(); i++) {
                JSONObject key = keys.getJSONObject(i);
                String keyKid = key.optString("kid");

                if (kid.equals(keyKid)) {
                    return parsePublicKey(key);
                }
            }

            logger.error("Key with kid '" + kid + "' not found in JWKS");
            return null;

        } catch (Exception e) {
            logger.error("Failed to fetch or parse JWKS from " + jwksUri, e);
            return null;
        }
    }

    private PublicKey parsePublicKey(JSONObject key) throws Exception {
        // Check if x5c (X.509 certificate chain) is present
        if (key.has("x5c")) {
            JSONArray x5cArray = key.getJSONArray("x5c");
            if (x5cArray.length() > 0) {
                String certString = x5cArray.getString(0);
                byte[] certBytes = Base64.getDecoder().decode(certString);

                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                        new ByteArrayInputStream(certBytes));
                return cert.getPublicKey();
            }
        }

        // Fallback to n and e (modulus and exponent) for RSA
        if (key.has("n") && key.has("e")) {
            String n = key.getString("n");
            String e = key.getString("e");

            byte[] nBytes = Base64.getUrlDecoder().decode(n);
            byte[] eBytes = Base64.getUrlDecoder().decode(e);

            java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
            java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

            java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec);
        }

        throw new IllegalArgumentException("Key does not contain x5c or n/e parameters");
    }

    @Override
    public void action(AuthenticationFlowContext authenticationFlowContext) {
    }
    /*
     * This function performes the actual validation of a 2FA method
     */

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }


    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void close() {

    }



}
