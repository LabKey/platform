package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpSession;

/**
 * Created by adam on 4/3/2016.
 */
public class SessionApiKeyManager extends SessionKeyManager<HttpSession>
{
    private final static SessionApiKeyManager INSTANCE = new SessionApiKeyManager();

    public static SessionApiKeyManager get()
    {
        return INSTANCE;
    }

    private SessionApiKeyManager()
    {
    }

    @Override
    @NotNull String getSessionAttributeName()
    {
        return "apikeys";
    }

    @Override
    @Nullable String getKeyPrefix()
    {
        return "session";
    }

    @Override
    HttpSession createContext(HttpSession session, User user)
    {
        return session;
    }

    @Override
    HttpSession validateContext(HttpSession session, String apiKey)
    {
        return isKeyInSession(session, apiKey) ? session : null;
    }
}
