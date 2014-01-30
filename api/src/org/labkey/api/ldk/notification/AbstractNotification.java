package org.labkey.api.ldk.notification;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;

import java.text.SimpleDateFormat;

/**
 * User: bimber
 * Date: 1/7/14
 * Time: 5:30 PM
 */
abstract public class AbstractNotification implements Notification
{
    private Module _owner;

    protected final static Logger log = Logger.getLogger(AbstractNotification.class);
    protected final static SimpleDateFormat _dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
    protected final static SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    protected final static SimpleDateFormat _timeFormat = new SimpleDateFormat("kk:mm");

    public AbstractNotification(Module owner)
    {
        _owner = owner;
    }

    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(_owner);
    }

    /**
     * This should really be using URLHelpers better, but there is a lot of legacy URL strings
     * migrated into java and its not worth changing all of it at this point
     */
    protected String getExecuteQueryUrl(Container c, String schemaName, String queryName, @Nullable String viewName)
    {
        DetailsURL url = DetailsURL.fromString("/query/executeQuery.view", c);
        String ret = AppProps.getInstance().getBaseServerUrl() + url.getActionURL().toString();
        ret += "schemaName=" + schemaName + "&query.queryName=" + queryName;
        if (viewName != null)
            ret += "&query.viewName=" + viewName;

        return ret;
    }
}
