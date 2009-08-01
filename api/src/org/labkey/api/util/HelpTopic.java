/*
 * Copyright (c) 2007-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

/**
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

    private static final String HELP_ROOT_URL = "http://help.labkey.org/wiki/home/";
    private static final String HELP_VERSION_URL = "/documentation/" + HELP_VERSION + "/page.view?name=";

    private static final HelpTopic DEFAULT_HELP_TOPIC = new HelpTopic("default", Area.SERVER);

    public HelpTopic(String topic, Area area)
    {
        if (topic == null || area == null)
            throw new IllegalArgumentException("Neither help topic nor area can be null");
        
        _topic = topic;
        _area = area;
    }

    @Override
    public String toString()
    {
        return getHelpTopicLink();
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
