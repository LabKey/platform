/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.geomicroarray;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.geomicroarray.query.GEOMicroarrayProviderSchema;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GEOMicroarrayManager
{
    private static GEOMicroarrayManager _instance;

    private GEOMicroarrayManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GEOMicroarrayManager get()
    {
        if(_instance == null)
            _instance = new GEOMicroarrayManager();

        return _instance;
    }

    private static GEOMicroarrayProviderSchema getProviderSchema(User user, Container container)
    {
        return new GEOMicroarrayProviderSchema(user, container, new GEOMicroarrayAssayProvider(), null, false);
    }

    private static TableInfo getAnnotationSetTableInfo(User user, Container container)
    {
        return getProviderSchema(user, container).getTable(GEOMicroarrayProviderSchema.FEATURE_ANNOTATION_SET_TABLE_NAME);
    }

    private static TableInfo getAnnotationTableInfo(User user, Container container)
    {
        return getProviderSchema(user, container).getTable(GEOMicroarrayProviderSchema.FEATURE_ANNOTATION_TABLE_NAME);
    }

    public Integer deleteFeatureAnnotationSet(Integer rowId) throws SQLException
    {
        SqlExecutor executor = new SqlExecutor(GEOMicroarrayProviderSchema.getSchema());

        // Delete all annotations first.
        SQLFragment delAnnotationsFragment = new SQLFragment("DELETE FROM ");
        delAnnotationsFragment.append(GEOMicroarrayProviderSchema.SCHEMA_NAME);
        delAnnotationsFragment.append("." + GEOMicroarrayProviderSchema.FEATURE_ANNOTATION_TABLE_NAME);
        delAnnotationsFragment.append(" WHERE FeatureAnnotationSetId = ?").add(rowId);
        Integer rowsDeleted = executor.execute(delAnnotationsFragment);

        // Then Delete annotation set.
        SQLFragment delAnnotSetFragment = new SQLFragment("DELETE FROM ");
        delAnnotSetFragment.append(GEOMicroarrayProviderSchema.SCHEMA_NAME);
        delAnnotSetFragment.append("." + GEOMicroarrayProviderSchema.FEATURE_ANNOTATION_SET_TABLE_NAME);
        delAnnotSetFragment.append(" WHERE RowId = ?").add(rowId);
        executor.execute(delAnnotSetFragment);

        return rowsDeleted;
    }

    private Integer insertFeatureAnnotationSet(User user, Container container, String name, String vendor, BatchValidationException errors)
            throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        QueryUpdateService featureSetUpdateService = getAnnotationSetTableInfo(user, container).getUpdateService();

        if (featureSetUpdateService != null)
        {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("Name", name);
            row.put("Vendor", vendor);
            row.put("Container", container);

            List<Map<String, Object>> results = featureSetUpdateService.insertRows(user, container, Collections.singletonList(row), errors, null);
            return (Integer) results.get(0).get("RowId");
        }

        return null;
    }

    private Integer insertFeatureAnnotations(User user, Container container, Integer featureSetRowId, DataLoader loader, BatchValidationException errors) throws SQLException
    {
        QueryUpdateService queryUpdateService = getAnnotationTableInfo(user, container).getUpdateService();

        if(queryUpdateService != null)
        {
            DataIteratorContext dataIteratorContext = new DataIteratorContext(errors);
            DataIterator dataIterator = loader.getDataIterator(dataIteratorContext);
            SimpleTranslator translator = new SimpleTranslator(dataIterator, dataIteratorContext);

            for (int i = 1; i <= dataIterator.getColumnCount(); i++)
            {
                ColumnInfo colInfo = dataIterator.getColumnInfo(i);
                String alias = colInfo.getColumnName().replace("_", "");
                int aliasIndex = translator.addColumn(i);
                translator.addAliasColumn(alias, aliasIndex);
            }

            translator.addConstantColumn("featureannotationsetid", JdbcType.INTEGER, featureSetRowId);

            return queryUpdateService.importRows(user, container, translator, errors, null);
        }

        return -1;
    }

    public Integer createFeatureAnnotationSet(User user, Container c, GEOMicroarrayController.FeatureAnnotationSetForm form, DataLoader loader, BatchValidationException errors)
            throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        // Creates feature annotation set AND inserts all feature annotations from TSV
        Integer rowId = insertFeatureAnnotationSet(user, c, form.getName(), form.getVendor(), errors);

        if(!errors.hasErrors() && rowId != null)
            return insertFeatureAnnotations(user, c, rowId, loader, errors);

        return -1;
    }

    public Map<String, Object>[] getFeatureAnnotationSets(User user, Container container) throws SQLException
    {
        TableSelector tableSelector = new TableSelector(getAnnotationSetTableInfo(user, container), PageFlowUtil.set("RowId", "Name", "Vendor"), null, null);
        return tableSelector.getArray(Map.class);
    }

    private Map<String, Object>[] getFeatureAnnotationSets(Container container) throws SQLException
    {
        TableInfo annotationSetTableInfo = GEOMicroarrayProviderSchema.getTableInfoFeatureAnnotationSet();
        SimpleFilter containerFilter = new SimpleFilter();
        containerFilter.addCondition(FieldKey.fromParts("container"), container);
        TableSelector tableSelector = new TableSelector(annotationSetTableInfo, PageFlowUtil.set("RowId", "Name", "Vendor", "Container"), containerFilter, null);
        return tableSelector.getArray(Map.class);
    }

    public void delete(Container container)
    {
        DbScope scope = GEOMicroarrayProviderSchema.getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            for(Map<String, Object> row : getFeatureAnnotationSets(container))
            {
                deleteFeatureAnnotationSet((Integer) row.get("RowId"));
            }
            scope.commitTransaction();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
