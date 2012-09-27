/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.study.samples.settings;

import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.study.model.StudyManager;
import org.labkey.api.data.Container;

import java.util.Map;

/*
 * User: brittp
 * Date: May 8, 2009
 * Time: 2:51:41 PM
 */

public class RepositorySettings
{
    private static final String KEY_SIMPLE = "Simple";
    private static final String KEY_ENABLE_REQUESTS = "EnableRequests";
    private boolean _simple;
    private boolean _enableRequests;
    private Container _container;

    public RepositorySettings(Container container)
    {
        _container = container;
    }

    public RepositorySettings(Container container, Map<String, String> map)
    {
        this(container);
        String simple = map.get(KEY_SIMPLE);
        _simple = null != simple && Boolean.parseBoolean(simple);
        String enableRequests = map.get(KEY_ENABLE_REQUESTS);
        _enableRequests = null == enableRequests ? !_simple : Boolean.parseBoolean(enableRequests);
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_SIMPLE, Boolean.toString(_simple));
        map.put(KEY_ENABLE_REQUESTS, Boolean.toString(_enableRequests));
    }

    public boolean isSimple()
    {
        return _simple;
    }

    public void setSimple(boolean simple)
    {
        _simple = simple;
    }

    public boolean isEnableRequests()
    {
        Study study = StudyService.get().getStudy(_container);
        if (study != null && (study.isAncillaryStudy() || study.isSnapshotStudy()))
            return false;
        return _enableRequests;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setEnableRequests(boolean enableRequests)
    {
        assert _simple || enableRequests : "Specimen requests may only be enabled for advanced specimen repository type";
        _enableRequests = enableRequests;
    }

    public static RepositorySettings getDefaultSettings(Container container)
    {
        RepositorySettings settings = new RepositorySettings(container);
        if (null != StudyManager.getInstance().getStudy(container))
        {
            settings.setSimple(false);
            settings.setEnableRequests(true);
        }
        else
        {
            settings.setSimple(true);
            settings.setEnableRequests(false);
        }
        return settings;
    }
}
