/*
 * Copyright (c) 2010-2015 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.*;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: brittp
 * Date: Jan 30, 2007
 * Time: 1:59:01 PM
 */
public class TemplateDesigner implements EntryPoint
{
    private int getIntProperty(String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt(PropertyUtil.getServerProperty(name));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public void onModuleLoad()
    {
        RootPanel panel = StudyApplication.getRootPanel();
        String templateName = PropertyUtil.getServerProperty("templateName");
        int plateId = PropertyUtil.getServerProperty("plateId") != null ? Integer.parseInt(PropertyUtil.getServerProperty("plateId")) : 0;
        String assayTypeName = PropertyUtil.getServerProperty("assayTypeName");
        String templateTypeName = PropertyUtil.getServerProperty("templateTypeName");
        int rowCount = getIntProperty("templateRowCount", 8);
        int colCount = getIntProperty("templateColumnCount", 12);
        TemplateView view = new TemplateView(panel, plateId, templateName, assayTypeName, templateTypeName, rowCount, colCount);
        view.showAsync();
    }
}
