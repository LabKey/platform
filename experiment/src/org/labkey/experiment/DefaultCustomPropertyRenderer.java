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
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.files.FileContentService;
import org.labkey.api.study.assay.FileLinkDisplayColumn;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * Responsible for showing custom field values (like assay run properties or sample set columns) in experiment module detail pages.
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class DefaultCustomPropertyRenderer implements CustomPropertyRenderer
{
    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, Container c)
    {
        Object o = prop.value();
        if (o == null)
        {
            return "";
        }
        if (prop.getPropertyType() == PropertyType.FILE_LINK)
        {
            File f = FileUtil.getAbsoluteCaseSensitiveFile(new File(o.toString()));
            o = FileLinkDisplayColumn.relativize(f, FileContentService.get().getFileRoot(c, FileContentService.ContentType.files));
            if (o == null)
            {
                o = FileLinkDisplayColumn.relativize(f, FileContentService.get().getFileRoot(c, FileContentService.ContentType.pipeline));
            }
            if (o == null)
            {
                o = f.toString();
            }
        }

        String value;

        // TODO: Should have a standard method that does this
        if (o instanceof Date)
            value = DateUtil.formatDateInfer(c, (Date)o);
        else if (o instanceof Number)
            value = Formats.formatNumber(c, (Number) o);
        else
            value = o.toString();

        return PageFlowUtil.filter(value);
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
