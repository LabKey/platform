package org.labkey.api.security;

import com.google.api.client.auth.oauth2.TokenRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class SMTPOAuth2TokenProvider extends OAuth2TokenProviderBase
{
    private final String scope;
    private static final Logger logger = LogManager.getLogger(SMTPOAuth2TokenProvider.class);

    public SMTPOAuth2TokenProvider(String clientId, String clientSecret, String scope, String tokenURL)
    {
        super(clientId, clientSecret, tokenURL);
        this.scope = scope;
    }

    @Override
    public TokenRequest getTokenRequest(String authCode)
    {
        logger.info("Creating SMTP OAuth2 Token Request");
        TokenRequest request = new TokenRequest(
                HTTP_TRANSPORT, JSON_FACTORY, getTokenServerUrl(), "client_credentials"
        );
        request.put("client_id", clientId);
        request.put("client_secret", getClientSecret());
        request.put("scope", Arrays.asList(scope));
        return request;
    }
}
