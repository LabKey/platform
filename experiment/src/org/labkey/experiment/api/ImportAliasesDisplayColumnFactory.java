package org.labkey.experiment.api;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ImportAliasesDisplayColumnFactory implements DisplayColumnFactory
{
    private final String _prefix;

    public ImportAliasesDisplayColumnFactory(String prefix)
    {
        _prefix = prefix == null ? null : prefix.toLowerCase();
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn dataColumn = new DataColumn(colInfo)
        {
            private JSONObject getValueFromCtx(RenderContext ctx) throws IOException
            {
                boolean isDataClass = getDescription() != null && getDescription().contains("data class");
                JSONObject json = null;
                Integer rowId = (Integer)getValue(ctx);
                Map<String, Map<String, Object>> aliasMap = null;
                if (!isDataClass)
                {
                    ExpSampleType sampleType = SampleTypeService.get().getSampleType(rowId);
                    if (sampleType != null)
                    {
                        aliasMap = sampleType.getImportAliasMap();

                    }
                }
                else
                {
                    ExpDataClass dataClass = ExperimentServiceImpl.get().getDataClass(rowId);
                    if (dataClass != null)
                    {
                        aliasMap = dataClass.getImportAliasMap();

                    }
                }

                if (aliasMap != null)
                {
                    Map<String, Map<String, Object>> finalAliasMap = aliasMap;
                    List<String> importKeys = aliasMap.keySet().stream()
                            .filter(key -> _prefix == null || ((String) finalAliasMap.get(key).get("inputType")).toLowerCase().startsWith(_prefix))
                            .sorted().toList();

                    if (!importKeys.isEmpty())
                    {
                        json = new JSONObject();
                        for (String importKey : importKeys)
                            json.put(importKey, aliasMap.get(importKey));
                    }
                }

                return json;
            }

            @Override
            public Object getJsonValue(RenderContext ctx)
            {
                Object value = getDisplayValue(ctx);
                return value != null ? value.toString() : null;
            }

            @Override
            public Object getDisplayValue(RenderContext ctx)
            {
                try
                {
                    return getValueFromCtx(ctx);
                }
                catch (IOException e)
                {
                    return HtmlString.of("Bad import alias object");
                }
            }

            @NotNull
            @Override
            public HtmlString getFormattedHtml(RenderContext ctx)
            {
                try
                {
                    JSONObject value = getValueFromCtx(ctx);
                    if (null == value)
                        return HtmlString.EMPTY_STRING;

                    DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
                    pp.indentArraysWith(new DefaultIndenter());

                    Object json = JsonUtil.DEFAULT_MAPPER.readValue(value.toString(), Object.class);
                    String strValue = JsonUtil.DEFAULT_MAPPER.writer(pp).writeValueAsString(json);
                    String filteredValue = PageFlowUtil.filter(strValue, true);
                    return HtmlString.unsafe("<div>" + filteredValue + "</div>");
                }
                catch (IOException e)
                {
                    return HtmlString.of("Bad import alias object");
                }
            }
        };

        dataColumn.setTextAlign("left");
        return dataColumn;
    }
}
