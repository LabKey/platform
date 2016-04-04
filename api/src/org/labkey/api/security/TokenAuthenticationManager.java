package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpSession;

/**
 * Created by adam on 4/3/2016.
 */
public class TokenAuthenticationManager extends SessionKeyManager<User>
{
    private final static TokenAuthenticationManager INSTANCE = new TokenAuthenticationManager();

    public static TokenAuthenticationManager get()
    {
        return INSTANCE;
    }

    private TokenAuthenticationManager()
    {
    }

    @Override
    @NotNull String getSessionAttributeName()
    {
        return "token";
    }

    @Override
    @Nullable String getKeyPrefix()
    {
        return null;
    }

    @Override
    User createContext(HttpSession session, User user)
    {
        return user;
    }

    @Override
    User validateContext(User user, String key)
    {
        return user;
    }
}
