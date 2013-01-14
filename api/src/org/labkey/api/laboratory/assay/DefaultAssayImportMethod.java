/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.apache.poi.ss.usermodel.Workbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 7:29 AM
 */
public class DefaultAssayImportMethod implements AssayImportMethod
{
    public static final String NAME = "Default Excel";
    protected static final String EMPTY_WELL_NAME = "empty";
    protected String _providerName;
    protected AssayProvider _ap;

    public DefaultAssayImportMethod(String providerName)
    {
        _providerName = providerName;
    }

    public AssayParser getFileParser(Container c, User u, int assayId)
    {
        return new DefaultAssayParser(this, c, u, assayId);
    }

    public String getName()
    {
        return NAME;
    }

    public String getLabel()
    {
        return "Default Excel Import";
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public boolean hideTemplateDownload()
    {
        return false;
    }

    public String getTooltip()
    {
        return "Choose this option to upload data using the basic, non-instrument specific excel template";
    }

    public boolean doEnterResultsInGrid()
    {
        return false;
    }

    public String getExampleDataUrl(ViewContext ctx)
    {
        return null;
    }

    public String getTemplateInstructions()
    {
        return null;
    }

    public JSONObject getMetadata(ViewContext ctx, ExpProtocol protocol)
    {
        JSONObject meta = new JSONObject();

        JSONObject batchMeta = new JSONObject();
        JSONObject importMethod = new JSONObject();
        importMethod.put("getInitialValue", "function(panel){if(panel.selectedMethod) {return panel.selectedMethod.name;}}");
        batchMeta.put("importMethod", importMethod);
        meta.put("Batch", batchMeta);

        JSONObject runMeta = new JSONObject();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        runMeta.put("Name", new JSONObject().put("value", ctx.getUser().getDisplayName(ctx.getUser()) + "_" + format.format(new Date())));

        JSONObject runDate = new JSONObject();
        runDate.put("getInitialValue", "function(){return new Date();}");
        runDate.put("extFormat", "Y-m-d");
        runMeta.put("performedBy", new JSONObject().put("value", ctx.getUser().getDisplayName(ctx.getUser())));
        runMeta.put("runDate", runDate);
        runMeta.put("comments", new JSONObject().put("height", 100));
        meta.put("Run", runMeta);

        JSONObject resultsMeta = new JSONObject();
        resultsMeta.put("sampleId", new JSONObject().put("lookups", false));
        resultsMeta.put("subjectId", new JSONObject().put("lookups", false));
        meta.put("Results", resultsMeta);

        return meta;
    }

    protected AssayProvider getAssayProvider()
    {
        if (_ap == null)
            _ap = AssayService.get().getProvider(_providerName);

        return _ap;
    }

    protected JSONObject getJsonObject(JSONObject parent, String key)
    {
        return parent.containsKey(key) ? parent.getJSONObject(key): new JSONObject();
    }

    public String getPreviewPanelClass()
    {
        return "Laboratory.ext.AssayPreviewPanel";
    }

    public boolean supportsRunTemplates()
    {
        return false;
    }

    public JSONObject toJson(ViewContext ctx, ExpProtocol protocol)
    {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("label", getLabel());
        json.put("supportsTemplates", supportsRunTemplates());

        json.put("hideTemplateDownload", hideTemplateDownload());
        json.put("tooltip", getTooltip());
        json.put("enterResultsInGrid", doEnterResultsInGrid());

        json.put("exampleDataUrl", getExampleDataUrl(ctx));
        json.put("templateInstructions", getTemplateInstructions());
        json.put("previewPanelClass", getPreviewPanelClass());
        json.put("metadata", getMetadata(ctx, protocol));

        return json;
    }

    public void generateTemplate(JSONObject json, HttpServletRequest request, HttpServletResponse response, boolean exportAsWebpage) throws BatchValidationException
    {
        doGenerateTemplate(json, request, response, exportAsWebpage);
    }

    public void doGenerateTemplate(JSONObject json, HttpServletRequest request, HttpServletResponse response, boolean exportAsWebpage) throws BatchValidationException
    {
        try
        {
            String filename = json.optString("templateName");
            ExcelWriter.ExcelDocumentType docType = ExcelWriter.ExcelDocumentType.xlsx;

            JSONObject resultDefaults = json.optJSONObject("Results");
            JSONArray results = json.getJSONArray("ResultRows");
            JSONArray rows = new JSONArray();
            for (JSONObject row : results.toJSONObjectArray())
            {
                Map<String, Object> map = new HashMap<String, Object>();

                rows.put(new JSONArray());
            }

            JSONObject sheet = new JSONObject();
            sheet.put("name", "Data");
            sheet.put("data", rows);

            JSONArray sheetsArray = new JSONArray();
            sheetsArray.put(sheet);
            Workbook workbook =  ExcelFactory.createFromArray(sheetsArray, docType);


            if (!exportAsWebpage){
                response.setContentType(docType.getMimeType());
                response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
                response.setHeader("Pragma", "private");
                response.setHeader("Cache-Control", "private");
            }
            workbook.write(response.getOutputStream());
        }
        catch (IOException e)
        {
            BatchValidationException bve = new BatchValidationException();
            bve.addRowError(new ValidationException(e.getMessage()));
            throw bve;
        }
    }

    protected Map<Object, Object> getWellMap96(final String keyProperty, final String valueProperty)
    {
        TableInfo ti = DbSchema.get("laboratory").getTable("well_layout");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("plate"), 1), null);

        final Map<Object, Object> wellMap = new HashMap<Object, Object>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                wellMap.put(object.getObject(keyProperty), object.getObject(valueProperty));
            }
        });

        return wellMap;
    }

    protected enum QUAL_RESULT
    {
        POS(),
        NEG(),
        OUTLIER(),
        ND();

        QUAL_RESULT()
        {

        }

        public Integer getRowId()
        {
            TableInfo ti = DbSchema.get("laboratory").getTable("qual_results");
            TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("meaning"), name()), null);
            if (ts.getRowCount() == 0)
                return null;

            Integer[] rowIds = ts.getArray(Integer.class);
            return rowIds[0];
        }
    }

    public List<String> getImportColumns(ViewContext ctx, ExpProtocol protocol)
    {
        List<String> columns = new ArrayList<String>();
        Domain resultDomain = getAssayProvider().getResultsDomain(protocol);
        JSONObject json = getMetadata(ctx, protocol).getJSONObject("Results");
        for (DomainProperty dp : resultDomain.getProperties())
        {
            JSONObject meta = json.containsKey(dp.getName()) ? json.getJSONObject(dp.getName()) : null;
            if (meta != null && meta.containsKey("setGlobally") && meta.getBoolean("setGlobally"))
                continue;
            else
                columns.add(dp.getLabel());
        }

        return columns;
    }

    public static enum SAMPLE_CATEGORY
    {
        Blank("Blank"),
        Control("Control"),
        NegControl("Neg Control"),
        PosControl("Pos Control"),
        Standard("Standard"),
        Unknown("Unknown");

        private String _label;

        SAMPLE_CATEGORY(String label)
        {
            _label = label;

        }

        public static SAMPLE_CATEGORY getEnum(String text)
        {
            text = text.replaceAll(" ", "");
            return SAMPLE_CATEGORY.valueOf(text);
        }

        public String getLabel()
        {
            return _label;
        }
    }
}
