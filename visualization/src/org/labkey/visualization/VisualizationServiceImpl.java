/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.visualization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisDataRequest;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationProvider.MeasureFilter;
import org.labkey.api.visualization.VisualizationProvider.MeasureSetRequest;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.visualization.sql.VisualizationCDSGenerator;
import org.labkey.visualization.sql.VisualizationSQLGenerator;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VisualizationServiceImpl implements VisualizationService
{
    public SQLResponse getDataGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException, IOException
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);

        ObjectReader r = new ObjectMapper().reader(VisDataRequest.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        VisDataRequest vdr = r.readValue(json.toString());
        vdr.setMetaDataOnly(true);

        VisualizationSQLGenerator generator = new VisualizationSQLGenerator();
        generator.setViewContext(context);
        generator.fromVisDataRequest(vdr);

        SQLResponse ret = new SQLResponse();
        ret.schemaKey = generator.getPrimarySchema().getSchemaPath();
        ret.sql = generator.getSQL();
        return ret;
    }

    public SQLResponse getDataCDSGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException, SQLException, BindException, IOException
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);

        ObjectReader r = new ObjectMapper().reader(VisDataRequest.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        VisDataRequest vdr = r.readValue(json.toString());
        vdr.setMetaDataOnly(true);

        VisualizationCDSGenerator generator = new VisualizationCDSGenerator(context, vdr);

        SQLResponse ret = new SQLResponse();
        ret.schemaKey = generator.getPrimarySchema().getSchemaPath();
        BindException errors = new NullSafeBindException(vdr, "form");
        ret.sql = generator.getSQL(errors);
        if (errors.hasErrors())
            throw errors;
        return ret;
    }


    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDimensions(Container c, User u, MeasureSetRequest measureRequest)
    {
        VisualizationProvider provider = getProvider(c, u, measureRequest);
        return provider.getDimensions(measureRequest.getQueryName());
    }


    public Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(Container c, User u, MeasureSetRequest measureRequest)
    {
        Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = new HashMap<>();

        if (measureRequest.getFilters() != null && measureRequest.getFilters().length > 0)
        {
            for (String filter : measureRequest.getFilters())
            {
                MeasureFilter mf = new MeasureFilter(filter);
                VisualizationProvider provider = getProvider(c, u, mf);

                if (measureRequest.isZeroDateMeasures())
                {
                    measures.putAll(provider.getZeroDateMeasures(mf.getQueryType()));
                }
                else if (measureRequest.isDateMeasures())
                {
                    if (mf.getQuery() != null)
                        measures.putAll(provider.getDateMeasures(mf.getQuery()));
                    else
                        measures.putAll(provider.getDateMeasures(mf.getQueryType()));
                }
                else if (measureRequest.isAllColumns())
                {
                    if (mf.getQuery() != null)
                        measures.putAll(provider.getAllColumns(mf.getQuery(), measureRequest.isShowHidden()));
                    else
                        measures.putAll(provider.getAllColumns(mf.getQueryType(), measureRequest.isShowHidden()));
                }
                else
                {
                    if (mf.getQuery() != null)
                        measures.putAll(provider.getMeasures(mf.getQuery()));
                    else
                        measures.putAll(provider.getMeasures(mf.getQueryType()));
                }
            }
        }
        else
        {
            // get all tables in this container
            for (VisualizationProvider provider : createVisualizationProviders(c, u, measureRequest.isShowHidden()).values())
            {
                if (measureRequest.isZeroDateMeasures())
                    measures.putAll(provider.getZeroDateMeasures(VisualizationProvider.QueryType.all));
                else if (measureRequest.isDateMeasures())
                    measures.putAll(provider.getDateMeasures(VisualizationProvider.QueryType.all));
                else
                    measures.putAll(provider.getMeasures(VisualizationProvider.QueryType.all));
            }
        }

        return measures;
    }


    public List<Map<String, Object>> toJSON(Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> dimMeasureCols)
    {
        return getColumnResponse(dimMeasureCols);
    }


    private Map<String, ? extends VisualizationProvider> createVisualizationProviders(Container c, User u, boolean showHidden)
    {
        Map<String, VisualizationProvider> result = new HashMap<>();
        DefaultSchema defaultSchema = DefaultSchema.get(u, c);
        for (QuerySchema querySchema : defaultSchema.getSchemas(showHidden))
        {
            VisualizationProvider provider = querySchema.createVisualizationProvider();
            if (provider != null)
            {
                result.put(querySchema.getName(), provider);
            }
        }
        return result;
    }


    @NotNull
    private VisualizationProvider getProvider(Container c, User u, MeasureFilter mf)
    {
        return getProvider(c, u, mf.getSchema());
    }


    @NotNull
    private VisualizationProvider getProvider(Container c, User u, MeasureSetRequest measureRequest)
    {
        return getProvider(c, u, measureRequest.getSchemaName());
    }


    @NotNull
    private VisualizationProvider getProvider(Container c, User u, String schema)
    {
        UserSchema userSchema = QueryService.get().getUserSchema(u, c, schema);
        if (userSchema == null)
        {
            throw new IllegalArgumentException("No measure schema found for " + schema);
        }

        VisualizationProvider provider = userSchema.createVisualizationProvider();
        if (provider == null)
        {
            throw new IllegalArgumentException("No measure provider found for schema " + userSchema.getSchemaPath());
        }

        return provider;
    }


    private static boolean isDemographicQueryDefinition(QueryDefinition q)
    {
        if (!StringUtils.equalsIgnoreCase("study", q.getSchemaName()) || !q.isTableQueryDefinition())
            return false;

        try
        {
            TableInfo t = q.getTable(null, false);
            if (!(t instanceof DatasetTable))
                return false;
            return ((DatasetTable)t).getDataset().isDemographicData();
        }
        catch (QueryException qe)
        {
            return false;
        }
    }


    private List<Map<String, Object>> getColumnResponse(Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> cols)
    {
        List<Map<String, Object>> measuresJSON = new ArrayList<>();
        Map<QueryDefinition, TableInfo> _tableInfoMap = new HashMap<>();
        Map<String, VisualizationProvider> _schemaVisualizationProviderMap = new HashMap<>();
        int count = 1;

        for (Map.Entry<Pair<FieldKey, ColumnInfo>, QueryDefinition> entry : cols.entrySet())
        {
            QueryDefinition query = entry.getValue();

            List<QueryException> errors = new ArrayList<>();
            TableInfo tableInfo = query.getTable(errors, false);
            if (errors.isEmpty() && !_tableInfoMap.containsKey(query))
                _tableInfoMap.put(query, tableInfo);

            if (!_schemaVisualizationProviderMap.containsKey(query.getSchema().getName()))
                _schemaVisualizationProviderMap.put(query.getSchema().getName(), query.getSchema().createVisualizationProvider());

            // add measure properties
            FieldKey fieldKey = entry.getKey().first;
            ColumnInfo column = entry.getKey().second;
            Map<String, Object> props = getColumnProps(fieldKey, column, query, _tableInfoMap);
            props.put("schemaName", query.getSchema().getName());
            props.put("queryName", getQueryName(query, false, _tableInfoMap));
            props.put("queryLabel", getQueryName(query, true, _tableInfoMap));
            props.put("queryDescription", getQueryDefinition(query, _tableInfoMap));
            props.put("isUserDefined", !query.isTableQueryDefinition());
            props.put("isDemographic", isDemographicQueryDefinition(query));
            props.put("phi", column.getPHI().name());
            props.put("hidden", column.isHidden() || (tableInfo != null && !tableInfo.getDefaultVisibleColumns().contains(column.getFieldKey())));
            props.put("queryType", getQueryType(query, _tableInfoMap));
            props.put("id", count++);

            // allow for the VisualizationProvider to annotate the response with other metadata properties
            _schemaVisualizationProviderMap.get(query.getSchema().getName()).addExtraColumnProperties(column, tableInfo, props);

            measuresJSON.add(props);
        }
        return measuresJSON;
    }


    private Map<String, Object> getColumnProps(FieldKey fieldKey, ColumnInfo col, QueryDefinition query, Map<QueryDefinition, TableInfo> _tableInfoMap)
    {
        Map<String, Object> props = new HashMap<>();

        props.put("name", fieldKey.toString());
        props.put("label", col.getLabel());
        props.put("longlabel", col.getLabel() + " (" + getQueryName(query, true, _tableInfoMap) + ")");
        props.put("type", col.getJdbcType().name());
        props.put("description", StringUtils.trimToEmpty(col.getDescription()));
        props.put("alias", VisualizationSourceColumn.getAlias(query.getSchemaName(), getQueryName(query, false, _tableInfoMap), col.getName()));

        props.put("isMeasure", col.isMeasure());
        props.put("isDimension", col.isDimension());
        props.put("isRecommendedVariable", col.isRecommendedVariable());
        props.put("defaultScale", col.getDefaultScale().name());

        Map<String, Object> lookupJSON = JsonWriter.getLookupInfo(col, false);
        if (lookupJSON != null)
        {
            props.put("lookup", lookupJSON);
        }

        props.put("shownInDetailsView", col.isShownInDetailsView());
        props.put("shownInInsertView", col.isShownInInsertView());
        props.put("shownInUpdateView", col.isShownInUpdateView());

        return props;
    }


    private String getQueryName(QueryDefinition query, boolean asLabel, Map<QueryDefinition, TableInfo> _tableInfoMap)
    {
        String queryName = query.getName();

        if (_tableInfoMap.containsKey(query))
        {
            TableInfo table = _tableInfoMap.get(query);
            if (table instanceof DatasetTable)
            {
                if (asLabel)
                    queryName = ((DatasetTable) table).getDataset().getLabel();
                else
                    queryName = ((DatasetTable) table).getDataset().getName();
            }
            else if (asLabel)
            {
                queryName = table.getTitle();
            }
        }

        return queryName;
    }


    private String getQueryDefinition(QueryDefinition query, Map<QueryDefinition, TableInfo> _tableInfoMap)
    {
        String description = query.getDescription();

        if (_tableInfoMap.containsKey(query))
        {
            TableInfo table = _tableInfoMap.get(query);
            if (table instanceof DatasetTable)
                description = ((DatasetTable) table).getDataset().getDescription();
        }

        return description;
    }

    private String getQueryType(QueryDefinition query, Map<QueryDefinition, TableInfo> _tableInfoMap)
    {
        if (_tableInfoMap.containsKey(query))
        {
            TableInfo table = _tableInfoMap.get(query);
            if (table instanceof DatasetTable)
                return VisualizationProvider.QueryType.datasets.toString();
        }

        if (query.isTableQueryDefinition())
        {
            return VisualizationProvider.QueryType.builtIn.toString();
        }

        return VisualizationProvider.QueryType.custom.toString();
    }
}
