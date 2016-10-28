/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class ExternalDocsURLCustomPropertyRenderer implements CustomPropertyRenderer
{
    public static final String URI = "terms.fhcrc.org#ExternalDocumentation.href";

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return true;
    }

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return "External documentation";
    }

    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, Container c)
    {
        String label = null;
        for (ObjectProperty p : siblingProperties)
        {
            if (ExternalDocsLabelCustomPropertyRenderer.URI.equals(p.getPropertyURI()))
            {
                label = p.getStringValue();
            }
        }
        if (label == null)
        {
            label = prop.getStringValue();
        }
        String link = prop.getStringValue();
        try
        {
            URL url = new URL(prop.getStringValue());
            if (url.getProtocol().equals("file"))
            {
                ActionURL h = new ActionURL(ExperimentController.ShowExternalDocsAction.class, c);
                h.addParameter("objectURI", prop.getObjectURI());
                h.addParameter("propertyURI", prop.getPropertyURI());
                link = h.toString();
            }
        }
        catch (MalformedURLException e)
        {
            // That's OK, we won't try to do anything with the link
        }
        return "<a href=\"" + link + "\">" + PageFlowUtil.filter(label) + "</a>";
    }
}
