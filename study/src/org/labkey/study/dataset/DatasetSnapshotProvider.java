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
package org.labkey.study.dataset;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.AbstractSnapshotProvider;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.util.*;
/*
 * User: Karl Lum
 * Date: Jul 9, 2008
 * Time: 4:57:40 PM
 */

public class DatasetSnapshotProvider extends AbstractSnapshotProvider
{
    public String getName()
    {
        return "Study Dataset Snapshot";
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm form) throws Exception
    {
        Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());
        QueryView view = QueryView.create(form);
        List<DisplayColumn> columns = new ArrayList<DisplayColumn>();

        for (DisplayColumn c : view.getDisplayColumns())
        {
            if (!DataSetDefinition.isDefaultFieldName(c.getName(), study))
            {
                columns.add(c);
            }
        }
        return columns;
    }

    public ActionURL createSnapshot(QuerySnapshotForm form) throws Exception
    {
        DbSchema schema = StudyManager.getSchema();
        boolean startedTransaction = false;

        try
        {
            if (!schema.getScope().isTransactionActive())
            {
                schema.getScope().beginTransaction();
                startedTransaction = true;
            }
            // create the dataset definition
            Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());
            boolean isDemographicData = false;

            DataSetDefinition def = AssayPublishManager.getInstance().createAssayDataset(form.getViewContext().getUser(),
                    study, form.getSnapshotName(), null, null, isDemographicData);
            if (def != null)
            {
                QuerySnapshotDefinition snapshot = createSnapshotDef(form);
                if (snapshot == null)
                    throw new IllegalArgumentException("Unable to create the query snapshot definition");

                snapshot.save(form.getViewContext().getUser(), form.getViewContext().getContainer());
                String domainURI = def.getTypeURI();
                OntologyManager.ensureDomainDescriptor(domainURI, form.getSnapshotName(), form.getViewContext().getContainer());
                Domain d = PropertyService.get().getDomain(form.getViewContext().getContainer(), domainURI);

                QueryView view = QueryView.create(form);
                if (view != null)
                {
/*
                    Map<String, DisplayColumn> columnMap = new HashMap<String, DisplayColumn>();
                    for (DisplayColumn c : view.getDisplayColumns())
                        columnMap.put(c.getName(), c);

                    for (String column : columns)
                    {
                        DisplayColumn dc = columnMap.get(column);
                        if (dc != null)
                        {
                            DomainProperty prop = d.addProperty();
                            prop.setLabel(dc.getName());
                            prop.setName(dc.getName());

                            prop.setType(PropertyService.get().getType(form.getViewContext().getContainer(), PropertyType.getFromClass(dc.getValueClass()).getXmlName()));
                            prop.setDescription(dc.getDescription());
                            prop.setFormat(dc.getFormatString());
                            prop.setPropertyURI(domainURI + "." + dc.getName());
                        }
                    }
*/
                    for (ColumnInfo col : QueryService.get().getColumns(view.getTable(), snapshot.getColumns()).values())
                    {
                        addAsDomainProperty(d, col);
                    }
                    d.save(form.getViewContext().getUser());

                    // import the data
                    StringBuilder sb = new StringBuilder();
                    List<String> errors = new ArrayList<String>();

                    view.getTsvWriter().write(sb);
                    StudyManager.getInstance().importDatasetTSV(study, def, sb.toString(), System.currentTimeMillis(),
                            Collections.<String,String>emptyMap(), errors, true);
                }
                if (startedTransaction)
                    schema.getScope().commitTransaction();

                return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, def.getDataSetId());
            }
            return null;
        }
        finally
        {
            if (startedTransaction && schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
        }
    }

    public ActionURL updateSnapshot(QuerySnapshotForm form) throws Exception
    {
        QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(form.getViewContext().getContainer(), form.getSchemaName(), form.getSnapshotName());
        if (def != null)
        {
            QueryDefinition queryDef = def.getQueryDefinition();
            QueryForm sourceForm = new QueryForm();
            sourceForm.setSchemaName(queryDef.getSchemaName());
            sourceForm.setQueryName(queryDef.getName());
            sourceForm.setViewContext(form.getViewContext());

            QueryView view = QueryView.create(sourceForm);
            Study study = StudyManager.getInstance().getStudy(form.getViewContext().getContainer());

            // purge the dataset rows then recreate the new one...
            DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
            if (dsDef != null)
            {
                DbSchema schema = StudyManager.getSchema();
                boolean startedTransaction = false;

                try
                {
                    if (!schema.getScope().isTransactionActive())
                    {
                        schema.getScope().beginTransaction();
                        startedTransaction = true;
                    }
                    int numRowsDeleted = StudyManager.getInstance().purgeDataset(study, dsDef);

                    // import the new data
                    StringBuilder sb = new StringBuilder();
                    List<String> errors = new ArrayList<String>();

                    view.getTsvWriter().write(sb);
                    StudyManager.getInstance().importDatasetTSV(study, dsDef, sb.toString(), System.currentTimeMillis(),
                            Collections.<String,String>emptyMap(), errors, true);

                    if (startedTransaction)
                        schema.getScope().commitTransaction();

                    return new ActionURL(StudyController.DatasetAction.class, form.getViewContext().getContainer()).
                            addParameter(DataSetDefinition.DATASETKEY, dsDef.getDataSetId());
                }
                finally
                {
                    if (startedTransaction && schema.getScope().isTransactionActive())
                        schema.getScope().rollbackTransaction();
                }
            }
        }
        return null;
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def) throws Exception
    {
        ActionURL ret = super.updateSnapshotDefinition(context, def);

        // update the study dataset columns
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        DataSetDefinition dsDef = StudyManager.getInstance().getDataSetDefinition(study, def.getName());
        if (dsDef != null)
        {
            String domainURI = dsDef.getTypeURI();
            Domain domain = PropertyService.get().getDomain(context.getContainer(), domainURI);

            if (domain != null)
            {
                Map<String, DomainProperty> propertyMap = new HashMap<String, DomainProperty>();

                for (DomainProperty prop : domain.getProperties())
                    propertyMap.put(prop.getName(), prop);

                for (ColumnInfo col : QueryService.get().getColumns(dsDef.getTableInfo(context.getUser()), def.getColumns()).values())
                {
                    if (propertyMap.containsKey(col.getName()))
                        propertyMap.remove(col.getName());
                    else
                        addAsDomainProperty(domain, col);
                }

                for (DomainProperty prop : propertyMap.values())
                    prop.delete();
            }
        }
        return ret;
    }
}