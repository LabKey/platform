package org.labkey.core.user;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.StringExpressionFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.labkey.core.user.LimitActiveUsersSettings.StartupProperties.*;

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

    public void setUserWarning(boolean warning)
    {
        storeBooleanValue(userWarning, warning);
    }

    public boolean isUserWarning()
    {
        return lookupBooleanValue(userWarning, false);
    }

    public void setUserWarningLevel(int level)
    {
        storeIntValue(userWarningLevel, level);
    }

    public int getUserWarningLevel()
    {
        return lookupIntValue(userWarningLevel, 10);
    }

    public void setUserWarningMessage(String message)
    {
        storeStringValue(userWarningMessage, message);
    }

    public String getUserWarningMessage()
    {
        return lookupStringValue(userWarningMessage, "");
    }

    public void setUserLimit(boolean limit)
    {
        storeBooleanValue(userLimit, limit);
    }

    public boolean isUserLimit()
    {
        return lookupBooleanValue(userLimit, false);
    }

    public void setUserLimitLevel(int level)
    {
        storeIntValue(userLimitLevel, level);
    }

    public int getUserLimitLevel()
    {
        return lookupIntValue(userLimitLevel, 10);
    }

    public void setUserLimitMessage(String message)
    {
        storeStringValue(userLimitMessage, message);
    }

    public String getUserLimitMessage()
    {
        return lookupStringValue(userLimitMessage, "");
    }

    public Map<StartupProperties, Object> getMetricsMap()
    {
        Map<StartupProperties, Object> map = new HashMap<>();
        map.put(userWarning, isUserWarning());
        map.put(userWarningLevel, getUserWarningLevel());
        map.put(userWarningMessage, getUserWarningMessage());
        map.put(userLimit, isUserLimit());
        map.put(userLimitLevel, getUserLimitLevel());
        map.put(userLimitMessage, getUserLimitMessage());

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

    public enum StartupProperties implements StartupProperty, SafeToRenderEnum
    {
        userWarning("Enable user warning"),
        userWarningLevel("Warning level user count"),
        userWarningMessage("Warning level message"),
        userLimit("Enable user limit"),
        userLimitLevel("User limit"),
        userLimitMessage("User limit message");

        private final String _description;

        StartupProperties(String description)
        {
            _description = description;
        }

        @Override
        public String getDescription()
        {
            return _description;
        }
    }

    public static void populateStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>("UserLimits", StartupProperties.class)
        {
            @Override
            public void handle(Map<StartupProperties, StartupPropertyEntry> properties)
            {
                if (!properties.isEmpty())
                {
                    LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
                    properties
                        .forEach((prop, entry) -> settings.storeStringValue(prop.name(), entry.getValue()));
                    settings.save(null);
                }
            }
        });
    }

    public static @Nullable HtmlString getWarningMessage(Container c, User user, boolean showAllWarnings)
    {
        if (c.hasPermission(user, AddUserPermission.class))
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
                return HtmlString.of("There are 10 active users on this deployment and the user limit is set to 15, which means 5 users can be added.");
        }

        return null;
    }

    private static HtmlString substitute(String message, int activeUsers, int warningLevel, int limitLevel)
    {
        // Display no warning if message is empty
        if (StringUtils.isEmpty(message))
            return null;

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
