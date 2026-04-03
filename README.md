# Keycloak ID Token Hint Authenticator for Entra ID External MFA

This Keycloak authenticator enables integration with Microsoft Entra ID's External Authentication Method Provider functionality. It validates `id_token_hint` tokens sent by Entra ID and identifies users based on configurable attributes.

## What It Does

This authenticator:
1. Receives an `id_token_hint` from Microsoft Entra ID during the external MFA flow
2. Validates the token signature against a configured JWKS endpoint
3. Extracts user claims from the token
4. Identifies the Keycloak user based on a configurable attribute mapping
5. Stores the original `sub` claim for echo-back to Entra ID

**Important:** This authenticator only **identifies** the user. It does **not** perform actual 2FA authentication. You must configure a separate 2FA authenticator in your authentication flow that performs the actual multi-factor authentication.

## Security Considerations

⚠️ **CRITICAL: Use a Dedicated Realm**

This integration should be deployed in a **separate, dedicated realm** to avoid security issues. Here's why:

### The Problem

Entra ID handles the first factor (e.g., password) and then delegates to Keycloak **only for the second factor**. If you configure this incorrectly, users could:
- Authenticate to Keycloak with just OTP/TOTP (bypassing the first factor requirement)
- Establish an SSO session in Keycloak with only a single factor
- Access other applications in the same realm with insufficient authentication

### The Solution

**Create a dedicated realm** for Entra ID external MFA that:

1. **Has NO cookie authenticator** - prevents SSO session creation
2. **Has NO password/username forms** - users should never authenticate directly to this realm
3. **Only validates the `id_token_hint`** from Entra ID and performs the second factor
4. **Has a single, simple authentication flow**:
   - ID Token Hint 2FA Authenticator (identifies user from Entra ID token)
   - Your 2FA Authenticator (REQUIRED - performs actual OTP/TOTP/FIDO2)
   - No other authenticators

### Example Safe Authentication Flow

```
External MFA Flow (bind to browser flow)
├── ID Token Hint 2FA Authenticator (REQUIRED)
└── OTP Form Authenticator (REQUIRED)
```

**Do NOT include:**
- Cookie authenticator
- Username/password forms
- "Remember Me" functionality
- Any conditional flows that could skip the 2FA step

### Why This Matters

If you reuse an existing realm with standard authentication flows:
- Users might bypass Entra ID and authenticate directly to Keycloak with just OTP
- They would get a Keycloak SSO session with only one factor
- This defeats the purpose of Entra ID's MFA policies

**The ideal setup:** A dedicated realm that acts purely as an external MFA provider for Entra ID, with no direct user authentication capabilities.

## Build Instructions

1. Install Maven and a recent OpenJDK (17+)
2. Build the project:
   ```bash
   mvn clean package
   ```
3. Copy the resulting JAR file to your Keycloak providers directory:
   ```bash
   cp target/id-hint-2fa.jar /opt/keycloak/providers/
   ```
4. Restart Keycloak

## Configuration

### 1. Configure the Authenticator

Add the authenticator to your authentication flow:

1. **Authentication** → **Flows** → Create a new flow or edit an existing one
2. Add execution: **ID Token Hint 2FA Authenticator**
3. Configure the authenticator:
   - **JWKS URI**: The URL where Entra ID publishes its public keys
   - **Attribute Matching**: The claim name to use for user lookup (e.g., `preferred_username`, `email`, `oid`)

#### Microsoft Entra ID JWKS URLs

Use the appropriate JWKS URI based on your Azure cloud:

| Azure Cloud | JWKS URI |
|-------------|----------|
| **Global Azure** (most common) | `https://login.microsoftonline.com/common/discovery/v2.0/keys` |
| **Azure US Government** | `https://login.microsoftonline.us/common/discovery/v2.0/keys` |
| **Azure China (21Vianet)** | `https://login.partner.microsoftonline.cn/common/discovery/v2.0/keys` |

**Note:** Most deployments should use the Global Azure URL. If you need tenant-specific keys, you can also use:
```
https://login.microsoftonline.com/{tenant-id}/discovery/v2.0/keys
```
Replace `{tenant-id}` with your Entra ID tenant ID or domain name.

### 2. Configure ACR-to-LOA Mappings

Entra ID sends ACR (Authentication Context Class Reference) values that must be mapped to Level of Authentication (LoA) values in Keycloak:

**Realm Settings** → **Authentication** → **Policies** → Configure these mappings:

| ACR Value | LOA Level |
|-----------|-----------|
| `possessionorinherence` | 2 |
| `knowledgeorpossession` | 2 |
| `knowledgeorinherence` | 2 |
| `knowledge` | 1 |
| `possession` | 2 |
| `inherence` | 2 |

Without these mappings, you'll see errors like:
```
Requested essential acr value 'possessionorinherence' is not a number and it is not mapped in the ACR-To-Loa mappings
```

### 3. Configure Your 2FA Authenticator (REQUIRED)

**This authenticator does NOT perform 2FA** - you must add a separate 2FA authenticator to your flow that performs actual multi-factor authentication (e.g., OTP, FIDO2, SMS).

You have two options for providing the ACR and AMR claims:

#### Option A: Dynamic Claims (Recommended)

Modify your 2FA authenticator code to set authentication session notes after successful authentication:

```java
@Override
public void authenticate(AuthenticationFlowContext context) {
    // ... perform your 2FA authentication ...
    
    // After successful authentication, set session notes:
    
    // Set ACR (Authentication Context Class Reference)
    context.getAuthenticationSession().setAuthNote("acr", "possessionorinherence");
    
    // Set AMR (Authentication Method Reference) - must be JSON array
    context.getAuthenticationSession().setAuthNote("amr", "[\"otp\"]");
    // Use appropriate AMR value: otp, sms, fido, hwk, face, fpt, etc.
    
    context.success();
}
```

This approach is more flexible and allows the claims to reflect the actual authentication method used.

#### Option B: Hardcoded Claims (Simple Alternative)

If you don't want to modify your authenticator code, you can use hardcoded protocol mappers instead of User Session Note mappers (see section 4 below). Use **Hardcoded claim** mappers instead of **User Session Note** mappers for ACR and AMR.

This is simpler to configure but less flexible - the same ACR/AMR values will always be returned regardless of which 2FA method was actually used.

### 4. Configure Protocol Mappers

Create a client scope (or edit an existing one) with these mappers:

**Client Scopes** → Create/Edit scope → **Mappers** tab:

#### ACR Mapper (User Session Note)
- Name: `acr-mapper`
- Mapper Type: **User Session Note**
- User Session Note: `acr`
- Token Claim Name: `acr`
- Claim JSON Type: `String`
- Add to ID token: **ON**
- Add to access token: **ON**

#### AMR Mapper (User Session Note)
- Name: `amr-mapper`
- Mapper Type: **User Session Note**
- User Session Note: `amr`
- Token Claim Name: `amr`
- Claim JSON Type: `JSON`
- Add to ID token: **ON**

#### Sub Echo Mapper (User Session Note)
- Name: `sub-echo-mapper`
- Mapper Type: **User Session Note**
- User Session Note: `microsoft_sub`
- Token Claim Name: `sub`
- Claim JSON Type: `String`
- Add to ID token: **ON**

Then assign this client scope to your Entra ID client.

### 5. Supported AMR Values

Choose the appropriate AMR value based on your actual 2FA method:

| AMR Value | Authentication Method |
|-----------|----------------------|
| `otp` | One-time password |
| `sms` | SMS confirmation |
| `tel` | Telephone confirmation |
| `fido` | FIDO2 token |
| `hwk` | Hardware-secured key |
| `swk` | Software-secured key |
| `face` | Facial biometric |
| `fpt` | Fingerprint biometric |
| `iris` | Iris biometric |
| `retina` | Retina biometric |
| `vbm` | Voice biometric |
| `sc` | Smart card |
| `pop` | Proof of possession |

## How It Works

1. User attempts to sign in to Entra ID
2. Entra ID redirects to Keycloak with `id_token_hint` parameter
3. This authenticator validates the token and identifies the user
4. Your 2FA authenticator performs actual authentication and sets ACR/AMR session notes
5. Protocol mappers add ACR/AMR/sub claims to the response token
6. Keycloak redirects back to Entra ID with the authenticated token
7. Entra ID validates the response and completes the sign-in

## References

- [Microsoft Entra External Authentication Method Provider Documentation](https://learn.microsoft.com/en-us/entra/identity/authentication/concept-authentication-external-method-provider)
- [OpenID Connect Core Specification](https://openid.net/specs/openid-connect-core-1_0.html)

## License

Apache License 2.0
