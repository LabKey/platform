package org.labkey.query.reports;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copyright (c) 2008-2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Sep 13, 2010 10:02:53 AM
 */
public class VisualizationController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(VisualizationController.class);

    public VisualizationController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static class DimensionsForm extends MeasuresForm
    {
        private boolean _includeDemographics;

        public boolean isIncludeDemographics()
        {
            return _includeDemographics;
        }

        public void setIncludeDemographics(boolean includeDemographics)
        {
            _includeDemographics = includeDemographics;
        }
    }

    public static class MeasuresForm
    {
        private String[] _filters = new String[0];
        private String _schemaName;
        private String _queryName;
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String[] getFilters()
        {
            return _filters;
        }

        public void setFilters(String[] filters)
        {
            _filters = filters;
        }
    }

    public static class MeasureFilter
    {
        public enum QueryType {
            builtIn,
            custom,
            all,
        }

        private String _schema;
        private String _query;
        private QueryType _queryType = QueryType.all;

        public MeasureFilter(String filter)
        {
            parse(filter);
        }

        protected void parse(String filter)
        {
            String[] parts = filter.split("\\|");

            assert(parts.length >= 2) : "Invalid filter value";

            _schema = parts[0];

            if (!parts[1].equals("~"))
                _query = parts[1];

            if (parts.length >= 3)
                _queryType = QueryType.valueOf(parts[2]);
        }

        public String getSchema()
        {
            return _schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public QueryType getQueryType()
        {
            return _queryType;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetMeasuresAction<Form extends MeasuresForm> extends ApiAction<Form>
    {
        public ApiResponse execute(Form form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<Map<String, Object>> measures = new ArrayList<Map<String, Object>>();
            int count = 1;

            for (Map.Entry<String, Map<String, TableInfo>> schemaEntry : getTables(form).entrySet())
            {
                for (Map.Entry<String, TableInfo> tableEntry : schemaEntry.getValue().entrySet())
                {
                    QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), schemaEntry.getKey(), tableEntry.getKey());

                    for (ColumnInfo col : tableEntry.getValue().getColumns())
                    {
                        if (col.isMeasure())
                        {
                            // add measure properties
                            Map<String, Object> props = getColumnProps(col);

                            props.put("schemaName", schemaEntry.getKey());
                            props.put("queryName", tableEntry.getKey());
                            props.put("isUserDefined", (def != null && !def.isTableQueryDefinition()));
                            props.put("id", count++);

                            measures.add(props);
                        }
                    }
                }
            }
            resp.put("success", true);
            resp.put("measures", measures);

            return resp;
        }

        protected Map<String, Map<String, TableInfo>> getTables(MeasuresForm form)
        {
            Map<String, Map<String, TableInfo>> tables = new HashMap<String, Map<String, TableInfo>>();
            DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());

            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    QuerySchema schema = defSchema.getSchema(mf.getSchema());
                    if (schema instanceof UserSchema)
                    {
                        UserSchema uschema = (UserSchema)schema;

                        if (mf.getQuery() == null)
                        {
                            // add all tables from this schema
                            getTablesFromSchema(uschema, mf.getQueryType(), tables);
                        }
                        else
                            // add a specific table
                            addTable(uschema, mf.getQuery(), tables);
                    }
                }
            }
            else
            {
                // get all tables in this container
                for (String schemaName : defSchema.getUserSchemaNames())
                {
                    QuerySchema schema = defSchema.getSchema(schemaName);
                    if (schema instanceof UserSchema)
                    {
                        getTablesFromSchema((UserSchema)schema, MeasureFilter.QueryType.all, tables);
                    }
                }
            }
            return tables;
        }

        private void getTablesFromSchema(UserSchema schema, MeasureFilter.QueryType type, Map<String, Map<String, TableInfo>> schemas)
        {
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.custom)
            {
                Map<String,QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(getUser(), getContainer(), schema.getSchemaName());
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isHidden())
                    {
                        addTable(schema, qdef.getName(), schemas);
                    }
                }
            }

            // built in tables
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.builtIn)
            {
                for (String name : schema.getTableNames())
                {
                    addTable(schema, name, schemas);
                }
            }
        }

        private void addTable(UserSchema schema, String tableName, Map<String, Map<String, TableInfo>> schemas)
        {
            TableInfo tinfo = schema.getTable(tableName);
            if (tinfo != null)
            {
                if (!schemas.containsKey(schema.getSchemaName()))
                    schemas.put(schema.getSchemaName(), new HashMap<String, TableInfo>());

                Map<String, TableInfo> tables = schemas.get(schema.getSchemaName());
                if (!tables.containsKey(tableName))
                    tables.put(tableName, tinfo);
            }
        }

        protected Set<String> getSchemaNames(MeasuresForm form)
        {
            if (form.getSchemaName() != null)
                return Collections.singleton(form.getSchemaName());
            else
            {
                DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
                return defSchema.getUserSchemaNames();
            }
        }

        protected Set<String> getTableNames(MeasuresForm form, UserSchema schema)
        {
            if (form.getQueryName() != null)
                return Collections.singleton(form.getQueryName());
            else
                return schema.getTableNames();
        }

        protected Map<String, Object> getColumnProps(ColumnInfo col)
        {
            Map<String, Object> props = new HashMap<String, Object>();

            props.put("name", col.getName());
            props.put("label", col.getLabel());
            props.put("type", col.getSqlTypeName());
            props.put("description", StringUtils.trimToEmpty(col.getDescription()));

            return props;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDimensionsAction extends GetMeasuresAction<DimensionsForm>
    {
        public ApiResponse execute(DimensionsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<Map<String, Object>> dimensions = new ArrayList<Map<String, Object>>();

            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                QuerySchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());

                if (schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    TableInfo tinfo = uschema.getTable(form.getQueryName());

                    getDimensions(tinfo, form.getSchemaName(), form.getQueryName(), dimensions);
                }

                if (form.isIncludeDemographics())
                {
                    // include dimensions from demographic data sources, probably only relevant for studies
                    Study study = StudyService.get().getStudy(getContainer());
                    if (study != null)
                    {
                        for (DataSet ds : study.getDataSets())
                        {
                            if (ds.isDemographicData())
                            {
                                getDimensions(ds.getTableInfo(getUser()), "study", ds.getName(), dimensions);
                            }
                        }
                    }
                }
                resp.put("success", true);
                resp.put("dimensions", dimensions);
            }
            else
                throw new IllegalArgumentException("schemaName and queryName are required parameters");

            return resp;
        }

        protected void getDimensions(TableInfo tinfo, String schema, String query, List<Map<String, Object>> dimensions)
        {
            if (tinfo != null)
            {
                QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), schema, query);

                for (ColumnInfo col : tinfo.getColumns())
                {
                    if (col.isDimension())
                    {
                        // add measure properties
                        Map<String, Object> props = getColumnProps(col);

                        props.put("schemaName", schema);
                        props.put("queryName", query);
                        props.put("isUserDefined", (def != null && !def.isTableQueryDefinition()));

                        dimensions.add(props);
                    }
                }
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDimensionValues extends GetMeasuresAction
    {
        public ApiResponse execute(MeasuresForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.getName() != null && form.getSchemaName() != null && form.getQueryName() != null)
            {
                QuerySchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());
                List<Map<String, String>> values = new ArrayList<Map<String, String>>();

                if (schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    TableInfo tinfo = uschema.getTable(form.getQueryName());

                    if (tinfo != null)
                    {
                        ColumnInfo col = tinfo.getColumn(form.getName());
                        if (col != null)
                        {
                            Table.TableResultSet rs = null;
                            try {
                                SQLFragment sql = QueryService.get().getSelectSQL(tinfo, Collections.singleton(col), null, null, Table.ALL_ROWS, 0);

                                rs = Table.executeQuery(uschema.getDbSchema(), sql.getSQL().replaceFirst("SELECT", "SELECT DISTINCT"), sql.getParamsArray());
                                Iterator<Map<String, Object>> it = rs.iterator();

                                while (it.hasNext())
                                {
                                    Map<String, Object> row = it.next();

                                    if (row.containsKey(col.getName()))
                                    {
                                        Object o = row.get(col.getName());
                                        if (o != null)
                                            values.add(Collections.singletonMap("value", ConvertUtils.convert(o)));
                                    }
                                }
                            }
                            finally
                            {
                                ResultSetUtil.close(rs);
                            }
                        }
                    }
                }
                resp.put("success", true);
                resp.put("values", values);
            }
            else
                throw new IllegalArgumentException("name, schemaName and queryName are required parameters");

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetVisualizationTypes extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);

            List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();

            // motion chart
            Map<String, Object> motion = new HashMap<String, Object>();
            motion.put("type", "motion");
            motion.put("label", "Motion Chart");
            motion.put("icon", getViewContext().getContextPath() + "/reports/output_motionchart.jpg");
            types.add(motion);

            // line chart
            Map<String, Object> line = new HashMap<String, Object>();
            line.put("type", "line");
            line.put("label", "Time Chart");
            line.put("icon", getViewContext().getContextPath() + "/reports/output_linechart.jpg");

            List<Map<String, String>> lineAxis = new ArrayList<Map<String, String>>();
            lineAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            line.put("axis", lineAxis);

            line.put("enabled", true);
            types.add(line);

            // scatter chart
            Map<String, Object> scatter = new HashMap<String, Object>();
            scatter.put("type", "scatter");
            scatter.put("label", "Scatter Plot");
            scatter.put("icon", getViewContext().getContextPath() + "/reports/output_scatterplot.jpg");

            List<Map<String, String>> scatterAxis = new ArrayList<Map<String, String>>();
            scatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            scatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            scatter.put("axis", scatterAxis);

            scatter.put("enabled", true);
            types.add(scatter);

            // data grid
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("type", "dataGrid");
            data.put("label", "Data Grid");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");
            types.add(data);

            // excel data export
            Map<String, Object> excel = new HashMap<String, Object>();
            excel.put("type", "excelExport");
            excel.put("label", "Excel Data Export");
            excel.put("icon", getViewContext().getContextPath() + "/reports/output_excel.jpg");
            types.add(excel);

            // TSV data export
            Map<String, Object> tsv = new HashMap<String, Object>();
            tsv.put("type", "tsvExport");
            tsv.put("label", "Tab-delimited Data Export");
            tsv.put("icon", getViewContext().getContextPath() + "/reports/output_text.jpg");
            types.add(tsv);

            resp.put("types", types);

            return resp;
        }
    }

    public static class GetDataForm implements CustomApiForm
    {
        private Map<String,Object> _extendedProperites;

        public void bindProperties(Map<String, Object> props)
        {
            _extendedProperites = props;
        }

        public Map<String, Object> getExtendedProperites()
        {
            return _extendedProperites;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDataAction extends ApiAction<GetDataForm>
    {
        @Override
        public ApiResponse execute(GetDataForm getDataForm, BindException errors) throws Exception
        {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
