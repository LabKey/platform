/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.study.view;

import com.google.gwt.core.client.EntryPoint;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.view.GWTView;

import java.util.Map;

/**
 * User: matthewb
 * Date: Mar 31, 2010
 * Time: 2:21:17 PM
 */
public class StudyGWTView extends GWTView
{
    public StudyGWTView(StudyApplication.GWTModule module, Map<String, String> properties)
    {
        super("gwt.StudyApplication", properties);
        getModelBean().getProperties().put("GWTModule", module.getClass().getSimpleName());
    }

    public StudyGWTView(Class<? extends EntryPoint> clss, Map<String, String> properties)
    {
        this(clss.getName(), properties);
    }

    public StudyGWTView(String clss, Map<String, String> properties)
    {
        super("gwt.StudyApplication", properties);
        for (StudyApplication.GWTModule m : StudyApplication.GWTModule.values())
        {
            if (m.className.equals(clss))
            {
                getModelBean().getProperties().put("GWTModule", m.getClass().getSimpleName());
                return;
            }
        }
        throw new IllegalArgumentException(clss);
    }
}
