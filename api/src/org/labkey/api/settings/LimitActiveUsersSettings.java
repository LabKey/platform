package org.labkey.api.settings;

import org.labkey.api.data.ContainerManager;

public class LimitActiveUsersSettings extends AbstractWriteableSettingsGroup
{
    public LimitActiveUsersSettings()
    {
        makeWriteable(ContainerManager.getRoot());
    }

    @Override
    protected String getGroupName()
    {
        return "LimitActiveUsers";
    }

    @Override
    protected String getType()
    {
        return "Limit Active Users settings";
    }

    @Override
    public void save()
    {
        super.save();
    }

    private static final String USER_WARNING = "userWarning";
    private static final String USER_WARNING_LEVEL = "userWarningLevel";
    private static final String USER_WARNING_MESSAGE = "userWarningMessage";
    private static final String USER_LIMIT = "userLimit";
    private static final String USER_LIMIT_LEVEL = "userLimitLevel";
    private static final String USER_LIMIT_MESSAGE = "userLimitMessage";

    public void setUserWarning(boolean userWarning)
    {
        storeBooleanValue(USER_WARNING, userWarning);
    }

    public boolean isUserWarning()
    {
        return lookupBooleanValue(USER_WARNING, false);
    }

    public void setUserWarningLevel(int userWarningLevel)
    {
        storeIntValue(USER_WARNING_LEVEL, userWarningLevel);
    }

    public int getUserWarningLevel()
    {
        return lookupIntValue(USER_WARNING_LEVEL, 10);
    }

    public void setUserWarningMessage(String userWarningMessage)
    {
        storeStringValue(USER_WARNING_MESSAGE, userWarningMessage);
    }

    public String getUserWarningMessage()
    {
        return lookupStringValue(USER_WARNING_MESSAGE, "");
    }

    public void setUserLimit(boolean userLimit)
    {
        storeBooleanValue(USER_LIMIT, userLimit);
    }

    public boolean isUserLimit()
    {
        return lookupBooleanValue(USER_LIMIT, false);
    }

    public void setUserLimitLevel(int userLimitLevel)
    {
        storeIntValue(USER_LIMIT_LEVEL, userLimitLevel);
    }

    public int getUserLimitLevel()
    {
        return lookupIntValue(USER_LIMIT_LEVEL, 10);
    }

    public void setUserLimitMessage(String userLimitMessage)
    {
        storeStringValue(USER_LIMIT_MESSAGE, userLimitMessage);
    }

    public String getUserLimitMessage()
    {
        return lookupStringValue(USER_LIMIT_MESSAGE, "");
    }
}
