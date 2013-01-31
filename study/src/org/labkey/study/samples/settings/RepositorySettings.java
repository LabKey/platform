/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import java.util.ArrayList;
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

    // Columns to group by n the specimen web part: Group1: Group By 1, Then By 2, Then By 3. Same for Group2
    private static final String makeKeySpecWebPartGroup(Integer grouping, Integer groupBy)
    {
        return "SpecWebPart_Group" + grouping.toString() + "." + groupBy.toString();
    }

    private boolean _simple;
    private boolean _enableRequests;
    private String[][] _specWebPartColumnGroup = new String[2][3];      // 2 groupings; 3 groupBys within each

    private Container _container;

    public RepositorySettings(Container container)
    {
        _container = container;
        setSpecimenWebPartGroupingDefaults();
    }

    public RepositorySettings(Container container, Map<String, String> map)
    {
        this(container);
        String simple = map.get(KEY_SIMPLE);
        _simple = null != simple && Boolean.parseBoolean(simple);
        String enableRequests = map.get(KEY_ENABLE_REQUESTS);
        _enableRequests = null == enableRequests ? !_simple : Boolean.parseBoolean(enableRequests);

        String firstGrouping = map.get(makeKeySpecWebPartGroup(0,0));
        if (null != firstGrouping)
        {
            for (int i = 0; i < 2; i += 1)      // Only 2 grouping supported
            {
                for (int k = 0; k < 3; k += 1)   // Only 2 groupBys supported
                {
                    _specWebPartColumnGroup[i][k] = map.get(makeKeySpecWebPartGroup(i,k));
                    if (null == _specWebPartColumnGroup[i][k])
                        _specWebPartColumnGroup[i][k] = "";
                }
            }
        }
    }

    public void populateMap(Map<String, String> map)
    {
        map.put(KEY_SIMPLE, Boolean.toString(_simple));
        map.put(KEY_ENABLE_REQUESTS, Boolean.toString(_enableRequests));

        for (int i = 0; i < 2; i += 1)      // Only 2 grouping supported
        {
            for (int k = 0; k < 3; k += 1)   // Only 2 groupBys supported
            {
                map.put(makeKeySpecWebPartGroup(i,k),
                        (null != _specWebPartColumnGroup[i][k]) ? _specWebPartColumnGroup[i][k] : "");
            }
        }
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

    public ArrayList<String[]> getSpecimenWebPartGroupings()
    {
        // List of groupings
        //      Each grouping is a list of Strings that name the columns
        ArrayList<String[]> groupings = new ArrayList<String[]>(2);
        for (int i = 0; i < 2; i += 1)      // Only 2 grouping supported
        {
            String[] grouping = new String[3];
            for (int k = 0; k < 3; k += 1)   // Only 3 groupBys supported
            {
                grouping[k] = (null != _specWebPartColumnGroup[i][k]) ? _specWebPartColumnGroup[i][k] : "";
            }
            groupings.add(grouping);
        }
        return groupings;
    }

    public void setSpecimenWebPartGroupings(ArrayList<String[]> groupings)
    {
        setSpecimenWebPartGroupingDefaults();
        for (int i = 0; i < groupings.size() && i < 2; i += 1)      // Only 2 groupings supported
        {
            String[] grouping = groupings.get(i);
            for (int k = 0; k < grouping.length && k < 3; k += 1)   // Only 3 groupBys supported
            {
                _specWebPartColumnGroup[i][k] = (null != grouping[k]) ? grouping[k] : "";
            }
        }
    }

    private void setSpecimenWebPartGroupingDefaults()
    {
        _specWebPartColumnGroup[0][0] = "Primary Type";
        _specWebPartColumnGroup[0][1] = "Derivative Type";
        _specWebPartColumnGroup[0][2] = "Additive Type";
        _specWebPartColumnGroup[1][0] = "Derivative Type";
        _specWebPartColumnGroup[1][1] = "Additive Type";
        _specWebPartColumnGroup[1][2] = "";
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
