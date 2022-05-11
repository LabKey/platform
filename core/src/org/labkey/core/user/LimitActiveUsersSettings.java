package org.labkey.core.user;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.UserManagementPermission;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public void save(@Nullable User user)
    {
        super.save();
        writeAuditLogEvent(ContainerManager.getRoot(), user);
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

    public Map<String, Object> getMetricsMap()
    {
        Map<String, Object> map = new HashMap<>();
        map.put(USER_WARNING, isUserWarning());
        map.put(USER_WARNING_LEVEL, getUserWarningLevel());
        map.put(USER_WARNING_MESSAGE, getUserWarningMessage());
        map.put(USER_LIMIT, isUserLimit());
        map.put(USER_LIMIT_LEVEL, getUserLimitLevel());
        map.put(USER_LIMIT_MESSAGE, getUserLimitMessage());

        return map;
    }

    /** Active users - system users **/
    public static int getActiveUserCount()
    {
        return UserManager.getActiveUserCount() - UserManager.getSystemUserCount();
    }

    public boolean isUserLimitReached()
    {
        if (!isUserLimit())
            return false;

        return getActiveUserCount() >= getUserLimitLevel();
    }

    /** Valid to call only if user limit is on **/
    public int getRemainingUserCount()
    {
        return Math.max(getUserLimitLevel() - getActiveUserCount(), 0);
    }

    public static void populateStartupProperties()
    {
        Collection<ConfigProperty> userLimitsProperties = ModuleLoader.getInstance().getConfigProperties("UserLimits");
        if (!userLimitsProperties.isEmpty())
        {
            LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
            userLimitsProperties
                .forEach(prop -> settings.storeStringValue(prop.getName(), prop.getValue()));
            settings.save(null);
        }
    }

    public static @Nullable HtmlString getWarningMessage(Container c, User user, boolean showAllWarnings)
    {
        if (c.hasPermission(user, UserManagementPermission.class))
        {
            LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
            int activeUsers = getActiveUserCount();
            int warningLevel = settings.getUserWarningLevel();
            int limitLevel = settings.getUserLimitLevel();

            if (settings.isUserLimit() && activeUsers >= limitLevel)
                return substitute(settings.getUserLimitMessage(), activeUsers, warningLevel, limitLevel);

            if (settings.isUserWarning() && activeUsers >= warningLevel)
                return substitute(settings.getUserWarningMessage(), activeUsers, warningLevel, limitLevel);

            if (showAllWarnings)
                return HtmlString.of("There are 10 active users on this deployment and the user limit is set to 15, which means there are 5 users remaining that can be added.");
        }

        return null;
    }

    private static HtmlString substitute(String message, int activeUsers, int warningLevel, int limitLevel)
    {
        Map<String, Integer> map = populatePropertyMap(new HashMap<>(), activeUsers, warningLevel, limitLevel);

        return HtmlString.unsafe(StringExpressionFactory.create(message).eval(map));
    }

    private static Map<String, Integer> populatePropertyMap(Map<String, Integer> map, int activeUsers, int warningLevel, int limitLevel)
    {
        map.put("ActiveUsers", activeUsers);
        map.put("WarningLevel", warningLevel);
        map.put("LimitLevel", limitLevel);
        map.put("RemainingUsers", Math.max(limitLevel - activeUsers, 0));

        return map;
    }

    public static Map<String, Integer> getPropertyMap()
    {
        LimitActiveUsersSettings settings = new LimitActiveUsersSettings();

        return populatePropertyMap(new LinkedHashMap<>(), getActiveUserCount(), settings.getUserWarningLevel(), settings.getUserLimitLevel());
    }

    public static ApiSimpleResponse getApiResponse(Container c, User user)
    {
        LimitActiveUsersSettings settings = new LimitActiveUsersSettings();

        ApiSimpleResponse response = new ApiSimpleResponse();
        response.put("messageHtml", getWarningMessage(c, user, false));
        response.put("activeUsers", getActiveUserCount());
        response.put("userLimit", settings.isUserLimit());
        response.put("userLimitLevel", settings.getUserLimitLevel());
        response.put("remainingUsers", settings.getRemainingUserCount());
        response.put("success", true);

        return response;
    }
}
