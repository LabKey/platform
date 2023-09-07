package org.labkey.api.security;

public class GoodPasswordValidator extends RuleBasedPasswordValidator
{
    @Override
    protected int getMinimumLength()
    {
        return 8;
    }

    @Override
    protected boolean isLowerCaseEnabled()
    {
        return true;
    }

    @Override
    protected boolean isUpperCaseEnabled()
    {
        return true;
    }

    @Override
    protected boolean isDigitEnabled()
    {
        return true;
    }

    @Override
    protected boolean isSymbolEnabled()
    {
        return true;
    }

    @Override
    protected int getRequiredCharacterTypeCount()
    {
        return 3;
    }

    @Override
    public boolean isPreviousPasswordForbidden()
    {
        return true;
    }

    @Override
    public boolean isDeprecated()
    {
        return false;
    }
}
