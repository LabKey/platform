/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.files.FileContentService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.FileLinkDisplayColumn;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class DefaultCustomPropertyRenderer implements CustomPropertyRenderer
{
    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, ViewContext context)
    {
        Object o = prop.value();
        if (o == null)
        {
            return "";
        }
        if (prop.getPropertyType() == PropertyType.FILE_LINK)
        {
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(o.toString()));
            o = FileLinkDisplayColumn.relativize(f, ServiceRegistry.get(FileContentService.class).getFileRoot(context.getContainer(), FileContentService.ContentType.files));
            if (o == null)
            {
                o = FileLinkDisplayColumn.relativize(f, ServiceRegistry.get(FileContentService.class).getFileRoot(context.getContainer(), FileContentService.ContentType.pipeline));
            }
            if (o == null)
            {
                o = f.toString();
            }
        }
        return PageFlowUtil.filter(o.toString());
    }

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return true;
    }

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(prop.getPropertyURI(), prop.getContainer());
        String name = prop.getName();
        if (pd != null)
            name = pd.getLabel() != null ? pd.getLabel() : pd.getName();
        return PageFlowUtil.filter(name);
    }
}
