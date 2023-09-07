package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HtmlString;

import java.util.Collection;

public class WeakPasswordValidator extends RuleBasedPasswordValidator
{
    @Override
    protected int getMinimumLength()
    {
        return 6;
    }

    @Override
    protected boolean isLowerCaseEnabled()
    {
        return false;
    }

    @Override
    protected boolean isUpperCaseEnabled()
    {
        return false;
    }

    @Override
    protected boolean isDigitEnabled()
    {
        return false;
    }

    @Override
    protected boolean isSymbolEnabled()
    {
        return false;
    }

    @Override
    protected int getRequiredCharacterTypeCount()
    {
        return 0;
    }

    @Override
    public boolean isPreviousPasswordForbidden()
    {
        return false;
    }

    @Override
    protected boolean isDeprecated()
    {
        return true;
    }

    @NotNull
    @Override
    public HtmlString getSummaryRuleHtml()
    {
        return HtmlString.of("Your password must be at least " + getMinimumLengthText() + " characters and cannot contain spaces or match your email address.");
    }

    @Override
    protected boolean containsPersonalInfo(@NotNull String password, @NotNull User user, @Nullable Collection<String> messages)
    {
        String email = user.getEmail();

        if (email.equalsIgnoreCase(password))
        {
            if (null != messages)
                messages.add("Your password must not match your email address.");

            return true;
        }

        return false;
    }

    @Override
    protected String getPersonalInfoRule()
    {
        return "Must not match the user's email address.";
    }
}
