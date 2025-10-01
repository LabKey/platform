package org.labkey.api.security;

import com.google.api.client.auth.oauth2.TokenRequest;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.jetbrains.annotations.Nullable;

public abstract class OAuth2TokenProviderBase
{
    protected static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    protected static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    protected final String clientId;
    private final String clientSecret;
    private final String tokenUrl;

    protected OAuth2TokenProviderBase(String clientId, String clientSecret, @Nullable String tokenUrl)
    {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
    }

    protected String getClientSecret()
    {
        return clientSecret;
    }

    protected GenericUrl getTokenServerUrl()
    {
        if (tokenUrl == null)
            throw new UnsupportedOperationException("Subclass must override getTokenServerUrl() if tokenUrl is not provided.");
        return new GenericUrl(tokenUrl);
    }

    public abstract TokenRequest getTokenRequest(@Nullable String authCode);
}