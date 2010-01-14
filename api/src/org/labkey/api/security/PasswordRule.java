package org.labkey.api.security;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * User: adam
 * Date: Jan 13, 2010
 * Time: 12:39:21 PM
 */
public enum PasswordRule
{
    weak
    {
        private Pattern passwordPattern = Pattern.compile("^\\S{6,}$");  // At least six, non-whitespace characters

        @NotNull
        @Override
        public String getRuleHTML()
        {
            return "Passwords must be six characters or more and can't match your email address.";
        }

        @Override
        boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages)
        {
            if (!passwordPattern.matcher(password).matches())
            {
                messages.add("Your password must be six characters or more.");
                return false;
            }

            String email = user.getEmail();

            if (email.equalsIgnoreCase(password))
            {
                messages.add("Your password can't match your email address.");
                return false;
            }

            return true;
        }
    },

    strong
    {
        @NotNull
        @Override
        public String getRuleHTML()
        {
            return "Passwords must be eight characters or more, and contain all kinds of crazy characters.";
        }

        @Override
        boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages)
        {
            return false;
        }
    };

    public boolean isValid(String password1, String password2, User user, Collection<String> messages)
    {
        if (StringUtils.isBlank(password1) || StringUtils.isBlank(password2))
        {
            messages.add("You must enter two passwords.");
            return false;
        }

        if (!password1.equals(password2))
        {
            messages.add("Your password entries didn't match.");
            return false;
        }

        return isValid(password1, user, messages);
    }

    abstract boolean isValid(@NotNull String password, @NotNull User user, @NotNull Collection<String> messages);

    public abstract @NotNull String getRuleHTML();
}
