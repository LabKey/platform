/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.*;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.PropertyType;

import java.util.List;
import java.util.ArrayList;
/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 2:23:17 PM
 */

public abstract class AbstractSnapshotProvider implements QuerySnapshotService.I
{
    public String getDescription()
    {
        return null;
    }

    public ActionURL createSnapshot(QuerySnapshotForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public ActionURL updateSnapshot(QuerySnapshotForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def) throws Exception
    {
        def.save(context.getUser(), context.getContainer());
        return null;
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm queryForm) throws Exception
    {
        QueryView view = QueryView.create(queryForm);
        return view.getDisplayColumns();
    }

    public ActionURL getCustomizeURL(ViewContext context)
    {
        return PageFlowUtil.urlProvider(QueryUrls.class).urlCustomizeSnapshot(context.getContainer());  
    }

    /**
     * Creates and initializes a snapshot definition from the snapshot form. The definition will still need
     * to be saved to persist to the db.
     */
    protected QuerySnapshotDefinition createSnapshotDef(QuerySnapshotForm form)
    {
        QueryDefinition queryDef = form.getQueryDef();
        if (queryDef != null)
        {
            QuerySnapshotDefinition snapshot = QueryService.get().createQuerySnapshotDef(queryDef,  form.getSnapshotName());

            snapshot.setColumns(form.getFieldKeyColumns());
            return snapshot;
        }
        return null;
    }

    protected DomainProperty addAsDomainProperty(Domain domain, ColumnInfo column)
    {
        DomainProperty prop = domain.addProperty();
        prop.setLabel(column.getCaption());
        prop.setName(column.getName());

        prop.setType(PropertyService.get().getType(domain.getContainer(), PropertyType.getFromClass(column.getJavaClass()).getXmlName()));
        prop.setDescription(column.getDescription());
        prop.setFormat(column.getFormatString());
        prop.setPropertyURI(domain.getTypeURI() + "." + column.getName());

        return prop;
    }
}