/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 2:23:17 PM
 */

public abstract class AbstractSnapshotProvider implements QuerySnapshotService.Provider
{
    private static final Map<String, Class> propertyClassMap = new HashMap<>();

    static
    {
        propertyClassMap.put(boolean.class.getName(), Boolean.class);
        propertyClassMap.put(int.class.getName(), Integer.class);
        propertyClassMap.put(short.class.getName(), Integer.class);
        propertyClassMap.put(long.class.getName(), Double.class);
        propertyClassMap.put(double.class.getName(), Double.class);
        propertyClassMap.put(float.class.getName(), Float.class);
    }

    public String getDescription()
    {
        return null;
    }

    public ActionURL updateSnapshot(QuerySnapshotForm form, BindException errors) throws Exception
    {
        return updateSnapshot(form, errors, false);
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, BindException errors) throws Exception
    {
        def.save(context.getUser());
        return null;
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm queryForm, BindException errors) throws Exception
    {
        QueryView view = QueryView.create(queryForm, errors);
        return view.getDisplayColumns();
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
            snapshot.setUpdateDelay(form.getUpdateDelay());

            ViewContext context = form.getViewContext();
            CustomView mergedFilterTempView = queryDef.createCustomView(context.getUser(), "tempCustomView");

            if (form.getViewName() != null)
            {
                CustomView customSrc = queryDef.getCustomView(context.getUser(), context.getRequest(), form.getViewName());
                if (customSrc != null)
                {
                    snapshot.setColumns(customSrc.getColumns());

                    if (customSrc.hasFilterOrSort())
                        mergedFilterTempView.setFilterAndSort(customSrc.getFilterAndSort());
                }
            }

            // Merge the custom view and URL filters together
            ActionURL mergedFilterURL = context.cloneActionURL();
            mergedFilterTempView.applyFilterAndSortToURL(mergedFilterURL, QueryView.DATAREGIONNAME_DEFAULT);

            // The combined filters is what we want in this custom view
            mergedFilterTempView.setFilterAndSortFromURL(mergedFilterURL, QueryView.DATAREGIONNAME_DEFAULT);

            snapshot.setFilter(mergedFilterTempView.getFilterAndSort());

            return snapshot;
        }
        return null;
    }

    public static DomainProperty addAsDomainProperty(Domain domain, ColumnInfo column)
    {
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(column.getPropertyURI(), domain.getContainer());
        DomainProperty prop;
        String name = column.getName();
        // 14750 replace '/' with '.' so columns can be mapped correctly on import from the exported file
        if (name.contains("/"))
            name = name.replace('/', '.');

        if (pd != null)
        {
            PropertyDescriptor newProp = pd.clone();

            // initialize so the domain doesn't get upset
            newProp.setContainer(domain.getContainer());
            newProp.setPropertyURI(getPropertyURI(domain, column));
            newProp.setPropertyId(0);
            newProp.setName(name);
            newProp.setLabel(column.getLabel());
            // Clear out any copied-over name
            newProp.setStorageColumnName(null);

            prop = domain.addPropertyOfPropertyDescriptor(newProp);
        }
        else
        {
            prop = domain.addProperty();
            prop.setLabel(column.getLabel());
            prop.setName(name);

            Class clz = column.getJavaClass();
            // need to map primitives to object class equivalents
            if (propertyClassMap.containsKey(clz.getName()))
                clz = propertyClassMap.get(clz.getName());

            PropertyType type = PropertyType.getFromClass(clz);
            prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
            prop.setDescription(column.getDescription());
            prop.setFormat(column.getFormat());
            prop.setPropertyURI(getPropertyURI(domain, column));
        }
        return prop;
    }

    public static String getPropertyURI(Domain domain, ColumnInfo column)
    {
        return domain.getTypeURI() + "." + column.getName();
    }

    @Override
    public TableInfo getTableInfoQuerySnapshotDef()
    {
        return DbSchema.get("query").getTable("QuerySnapshotDef");
    }
}
