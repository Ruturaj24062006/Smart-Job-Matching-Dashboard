package com.careermatch.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Additional cryptography and networking imports
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.math.BigInteger;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenProvider {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.accessTokenExpirationMs}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refreshTokenExpirationMs}")
    private long jwtRefreshExpirationMs;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anonKey}")
    private String supabaseAnonKey;

    private final Map<String, PublicKey> jwkCache = new ConcurrentHashMap<>();
    private long lastJwksFetchTime = 0;

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes();
        if (keyBytes.length < 32) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                keyBytes = digest.digest(keyBytes);
            } catch (Exception e) {
                // Ignore fallback
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private synchronized void fetchSupabaseJwks() {
        if (System.currentTimeMillis() - lastJwksFetchTime < 600000 && !jwkCache.isEmpty()) {
            return;
        }
        try {
            log.info("Fetching Supabase JWKS keys from: {}/auth/v1/.well-known/jwks.json", supabaseUrl);
            URL url = new URL(supabaseUrl + "/auth/v1/.well-known/jwks.json");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", supabaseAnonKey);
            
            if (conn.getResponseCode() == 200) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<?, ?> jwks = mapper.readValue(conn.getInputStream(), Map.class);
                List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
                if (keys != null) {
                    for (Map<String, Object> keyData : keys) {
                        String kid = (String) keyData.get("kid");
                        String kty = (String) keyData.get("kty");
                        String crv = (String) keyData.get("crv");
                        String xStr = (String) keyData.get("x");
                        String yStr = (String) keyData.get("y");
                        
                        if ("EC".equals(kty) && "P-256".equals(crv) && xStr != null && yStr != null) {
                            PublicKey pubKey = generateECPublicKey(xStr, yStr);
                            jwkCache.put(kid, pubKey);
                            log.info("Successfully cached Supabase JWK for key ID: {}", kid);
                        }
                    }
                    lastJwksFetchTime = System.currentTimeMillis();
                }
            } else {
                log.error("Supabase JWKS HTTP error: {}", conn.getResponseCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch or parse Supabase JWKS: {}", e.getMessage(), e);
        }
    }

    private PublicKey generateECPublicKey(String xBase64, String yBase64) throws Exception {
        byte[] xBytes = Base64.getUrlDecoder().decode(xBase64);
        byte[] yBytes = Base64.getUrlDecoder().decode(yBase64);
        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecParams = params.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(x, y);
        ECPublicKeySpec spec = new ECPublicKeySpec(point, ecParams);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(spec);
    }

    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        String email = claims.get("email", String.class);
        return email != null ? email : claims.getSubject();
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        // 1. Check custom claim "role"
        String role = claims.get("role", String.class);
        if (role != null) {
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role.toUpperCase();
            }
            return role;
        }
        // 2. Check user_metadata map
        Map<?, ?> metadata = claims.get("user_metadata", Map.class);
        if (metadata != null && metadata.get("role") != null) {
            String metaRole = metadata.get("role").toString();
            if (!metaRole.startsWith("ROLE_")) {
                metaRole = "ROLE_" + metaRole.toUpperCase();
            }
            return metaRole;
        }
        // Default fallback to STUDENT role
        return "ROLE_STUDENT";
    }

    public boolean validateTokenSignatureAndExpiry(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        String alg = header.getAlgorithm();
                        if ("HS256".equals(alg)) {
                            return getSigningKey();
                        } else if ("ES256".equals(alg)) {
                            String kid = (String) header.get("kid");
                            if (kid != null) {
                                PublicKey cachedKey = jwkCache.get(kid);
                                if (cachedKey != null) {
                                    return cachedKey;
                                }
                                fetchSupabaseJwks();
                                cachedKey = jwkCache.get(kid);
                                if (cachedKey != null) {
                                    return cachedKey;
                                }
                            }
                        }
                        throw new SignatureException("Unsupported signing algorithm or unknown key ID: " + alg);
                    }
                })
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        return createToken(claims, userDetails.getUsername(), jwtExpirationMs);
    }

    public String generateToken(String email, String role, java.util.UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase());
        claims.put("userId", userId != null ? userId.toString() : null);
        return createToken(claims, email, jwtExpirationMs);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return createToken(new HashMap<>(), userDetails.getUsername(), jwtRefreshExpirationMs);
    }

    public String generateRefreshToken(String email, String role, java.util.UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase());
        claims.put("userId", userId != null ? userId.toString() : null);
        return createToken(claims, email, jwtRefreshExpirationMs);
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationMs) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
