package org.labkey.api.util;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

/**
 * Created by IntelliJ IDEA.
 * User: Tamra Myers
 * Date: Feb 15, 2007
 * Time: 1:10:09 PM
 */
public class HelpTopic
{
    public enum Area
    {
        CPAS("cpas"),
        FLOW("flow"),
        STUDY("study"),
        SERVER("server"),
        DEVELOPER("developer"),
        DEFAULT(SERVER.getAreaPath());

        private String _area;

        Area(String area)
        {
            _area = area;
        }

        public String getAreaPath()
        {
            return _area;
        }
    }

    private String _topic;
    private Area _area;

    private static String HELP_VERSION = null;

    static
    {
        // Get core module version number, truncate to one decimal place, and use as help version
        Module core = ModuleLoader.getInstance().getCoreModule();
        double coreVersion = core.getVersion();
        HELP_VERSION = Formats.f1.format(Math.floor(coreVersion*10)/10);
    }

    private static final String HELP_ROOT_URL = "http://help.labkey.org/Wiki/home/";
    private static final String HELP_VERSION_URL = "/documentation/" + HELP_VERSION + "/page.view?name=";

    private static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default", Area.SERVER);

    public HelpTopic(String topic, Area area)
    {
        if (topic == null || area == null)
            throw new IllegalArgumentException("Neither help topic nor area can be null");
        
        _topic = topic;
        _area = area;
    }

    public String getHelpTopicLink()
    {
        return HELP_ROOT_URL + _area.getAreaPath() + HELP_VERSION_URL + _topic;
    }

    public static String getDefaultHelpURL()
    {
        return DEFAULT_HELP_TOPIC.getHelpTopicLink();
    }
}
