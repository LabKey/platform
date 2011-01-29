/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.fop.svg.PDFTranscoder;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.visualization.sql.StudyVisualizationProvider;
import org.labkey.visualization.sql.VisualizationProvider;
import org.labkey.visualization.sql.VisualizationSQLGenerator;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.util.*;

/*
 * User: brittp
 * Date: Sep 13, 2010 10:02:53 AM
 */
public class VisualizationController extends SpringActionController
{
    public static final String NAME = "visualization";
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
        private boolean _dateMeasures;

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

        public boolean isDateMeasures()
        {
            return _dateMeasures;
        }

        public void setDateMeasures(boolean dateMeasures)
        {
            _dateMeasures = dateMeasures;
        }
    }

    public static Map<String, ? extends VisualizationProvider> getVisualizationProviders()
    {
        return Collections.singletonMap("study", new StudyVisualizationProvider());
    }

    public enum QueryType {
        builtIn,
        custom,
        all,
    }
    
    public static class MeasureFilter
    {
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
            Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = getVisualizationProviders().get(mf.getSchema());
                    if (provider == null)
                    {
                        errors.reject(ERROR_MSG, "No measure provider found for schema " + mf.getSchema());
                        return null;
                    }

                    if (form.isDateMeasures())
                    {
                        if (mf.getQuery() != null)
                            measures.putAll(provider.getDateMeasures(getViewContext(), mf.getQuery()));
                        else
                            measures.putAll(provider.getDateMeasures(getViewContext(), mf.getQueryType()));

                    }
                    else
                    {
                        if (mf.getQuery() != null)
                            measures.putAll(provider.getMeasures(getViewContext(), mf.getQuery()));
                        else
                            measures.putAll(provider.getMeasures(getViewContext(), mf.getQueryType()));
                    }
                }
            }
            else
            {
                // get all tables in this container
                for (VisualizationProvider provider : getVisualizationProviders().values())
                {
                    if (form.isDateMeasures())
                        measures.putAll(provider.getDateMeasures(getViewContext(), QueryType.all));
                    else
                        measures.putAll(provider.getMeasures(getViewContext(), QueryType.all));
                }
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<Map<String, Object>> measuresJSON = getColumnResponse(measures);
            resp.put("success", true);
            resp.put("measures", measuresJSON);

            return resp;
        }

        protected List<Map<String, Object>> getColumnResponse(Map<ColumnInfo, QueryView> cols)
        {
            List<Map<String, Object>> measuresJSON = new ArrayList<Map<String, Object>>();
            int count = 1;
            for (Map.Entry<ColumnInfo, QueryView> entry : cols.entrySet())
            {
                // add measure properties
                Map<String, Object> props = getColumnProps(entry.getKey());

                QueryView queryView = entry.getValue();
                props.put("schemaName", queryView.getSchema().getName());
                props.put("queryName", queryView.getQueryDef().getName());
                props.put("isUserDefined", !queryView.getQueryDef().isTableQueryDefinition());
                props.put("id", count++);

                measuresJSON.add(props);
            }
            return measuresJSON;
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
            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                VisualizationProvider provider = getVisualizationProviders().get(form.getSchemaName());
                if (provider == null)
                {
                    errors.reject(ERROR_MSG, "No measure provider found for schema " + form.getSchemaName());
                    return null;
                }
                Map<ColumnInfo, QueryView> dimensions = provider.getDimensions(getViewContext(), form.getQueryName());
                List<Map<String, Object>> dimensionJSON = getColumnResponse(dimensions);
                resp.put("success", true);
                resp.put("dimensions", dimensionJSON);
            }
            else
                throw new IllegalArgumentException("schemaName and queryName are required parameters");

            return resp;
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
        Map<String, Object> getBaseTypeProperties()
        {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("subjectColumn", StudyService.get().getSubjectColumnName(getContainer()));
            properties.put("subjectNounSingular", StudyService.get().getSubjectNounSingular(getContainer()));
            properties.put("subjectNounPlural", StudyService.get().getSubjectNounPlural(getContainer()));
            return properties;
        }

        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);

            List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();
/*  NOT SUPPORTED FOR 10.3
            // motion chart
            Map<String, Object> motion = getBaseTypeProperties();
            motion.put("type", "motion");
            motion.put("label", "Motion Chart");
            motion.put("icon", getViewContext().getContextPath() + "/reports/output_motionchart.jpg");
            types.add(motion);
*/
            // line chart
            Map<String, Object> line = getBaseTypeProperties();
            line.put("type", "line");
            line.put("label", "Time Chart");
            line.put("icon", getViewContext().getContextPath() + "/reports/output_linechart.jpg");

            List<Map<String, String>> lineAxis = new ArrayList<Map<String, String>>();
            lineAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            lineAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            line.put("axis", lineAxis);

            line.put("enabled", true);
            types.add(line);

/*  NOT SUPPORTED FOR 10.3
            // scatter chart
            Map<String, Object> scatter = getBaseTypeProperties();
            scatter.put("type", "scatter");
            scatter.put("label", "Scatter Plot");
            scatter.put("icon", getViewContext().getContextPath() + "/reports/output_scatterplot.jpg");

            List<Map<String, String>> scatterAxis = new ArrayList<Map<String, String>>();
            scatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            scatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            scatter.put("axis", scatterAxis);

            scatter.put("enabled", true);
            types.add(scatter);
*/
            // data grid
            Map<String, Object> data = getBaseTypeProperties();
            data.put("type", "dataGridTime");
            data.put("label", "Data Grid (Time)");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataAxis = new ArrayList<Map<String, String>>();
            dataAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            dataAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            data.put("axis", lineAxis);
            data.put("isGrid", true);
            data.put("enabled", true);

            types.add(data);

            // data grid
            Map<String, Object> dataScatter = getBaseTypeProperties();
            dataScatter.put("type", "dataGridScatter");
            dataScatter.put("label", "Data Grid (X/Y)");
            dataScatter.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataScatterAxis = new ArrayList<Map<String, String>>();
            dataScatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            dataScatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            dataScatter.put("axis", dataScatterAxis);
            dataScatter.put("isGrid", true);
            dataScatter.put("enabled", true);

            types.add(dataScatter);
/*  NOT SUPPORTED FOR 10.3
            // excel data export
            Map<String, Object> excel = getBaseTypeProperties();
            excel.put("type", "excelExport");
            excel.put("label", "Excel Data Export");
            excel.put("icon", getViewContext().getContextPath() + "/reports/output_excel.jpg");
            types.add(excel);

            // TSV data export
            Map<String, Object> tsv = getBaseTypeProperties();
            tsv.put("type", "tsvExport");
            tsv.put("label", "Tab-delimited Data Export");
            tsv.put("icon", getViewContext().getContextPath() + "/reports/output_text.jpg");
            types.add(tsv);
*/
            resp.put("types", types);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDataAction extends ApiAction<VisualizationSQLGenerator>
    {
        @Override
        public ApiResponse execute(VisualizationSQLGenerator sqlGenerator, BindException errors) throws Exception
        {
            ApiQueryResponse response = getApiResponse(getViewContext(), sqlGenerator.getPrimarySchema(), sqlGenerator.getSQL(), errors);
            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<String, Object>();
            Map<String, String> measureNameToColumnName = sqlGenerator.getColumnMapping();
            extraProperties.put("measureToColumn", measureNameToColumnName);
            response.setExtraReturnProperties(extraProperties);

            return response;
        }

        private ApiQueryResponse getApiResponse(ViewContext context, UserSchema schema, String sql, BindException errors) throws Exception
        {
            String schemaName = schema.getName();
            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query

            QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), schemaName, sql);

            QuerySettings settings = new QuerySettings(getViewContext(), "visualization", def.getName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            //by default, return all rows
            settings.setShowRows(ShowRows.ALL);

            //apply optional settings (maxRows, offset)
            boolean metaDataOnly = false;

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            return new ExtendedApiQueryResponse(view, getViewContext(), false,
                    false, schemaName, def.getName(), 0, null, metaDataOnly);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetZeroDateAction extends GetMeasuresAction<MeasuresForm>
    {
        public ApiResponse execute(MeasuresForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = getVisualizationProviders().get(mf.getSchema());
                    if (provider == null)
                    {
                        errors.reject(ERROR_MSG, "No measure provider found for schema " + mf.getSchema());
                        return null;
                    }
                    measures.putAll(provider.getZeroDateMeasures(getViewContext(), mf.getQueryType()));
                }
            }
            else
            {
                // get all tables in this container
                for (VisualizationProvider provider : getVisualizationProviders().values())
                    measures.putAll(provider.getZeroDateMeasures(getViewContext(), QueryType.all));
            }

            List<Map<String, Object>> measuresJSON = getColumnResponse(measures);
            resp.put("success", true);
            resp.put("measures", measuresJSON);

            return resp;
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Alternately a form-encoded post with a parameter called svg to allow JavaScript clients to access it
     */
    @RequiresPermissionClass(ReadPermission.class)
    abstract class ExportSVGAction extends BaseViewAction
    {
        String _svgSource;

        @Override
        protected String getCommandClassMethodName()
        {
            return "execute";
        }

        @Override
        public void validate(Object o, Errors errors)
        {
            return; //TODO: Validate XML
        }

        protected String getSVGSource() throws Exception
        {
            if (null != _svgSource)
                return _svgSource;

            HttpServletRequest request = getViewContext().getRequest();
            if (request.getContentType().startsWith("text/xml"))
                _svgSource = PageFlowUtil.getStreamContentsAsString(request.getInputStream());
            else
                _svgSource = request.getParameter("svg");

            //svg MUST have right namespace for Batik, but svg generated by browser doesn't necessarily declare it
            //Since changing namespace recursively for all nodes not supported by DOM impl, just poke it in here.
            if (null != _svgSource && !_svgSource.contains("xmlns=\"" + SVGDOMImplementation.SVG_NAMESPACE_URI + "\"") && !_svgSource.contains("xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'"))
                _svgSource = _svgSource.replace("<svg", "<svg xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'");

            return _svgSource;
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Content-type of request must be text/xml, not any kind of multipart
     * Returns a PNG image.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class ExportImageAction extends ExportSVGAction
    {

        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("image/png");
            response.addHeader("Content-Disposition", "attachment; filename=visualization.png");


            TranscoderInput xIn = new TranscoderInput(new StringReader(getSVGSource()));
            TranscoderOutput xOut = new TranscoderOutput(response.getOutputStream());

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, java.awt.Color.WHITE);
            transcoder.transcode(xIn, xOut);

            return null;
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Content-type of request must be text/xml, not any kind of multipart
     * Returns a PDF document containing the visualization as a scalable vector graphic
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class ExportPDFAction extends ExportSVGAction
    {

        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("application/pdf");
            response.addHeader("Content-Disposition", "attachment; filename=visualization.pdf");

            TranscoderInput xIn = new TranscoderInput(new StringReader(getSVGSource()));
            TranscoderOutput xOut = new TranscoderOutput(response.getOutputStream());

            new PDFTranscoder().transcode(xIn, xOut);

            return null;
        }
    }

}
