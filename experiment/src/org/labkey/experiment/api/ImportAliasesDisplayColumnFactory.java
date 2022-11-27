package org.labkey.experiment.api;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.json.old.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                JSONObject json = null;
                Integer rowId = (Integer)getValue(ctx);
                ExpSampleType sampleType = SampleTypeService.get().getSampleType(rowId);
                if (sampleType != null)
                {
                    Map<String, String> aliasMap = sampleType.getImportAliasMap();
                    List<String> importKeys = aliasMap.keySet().stream()
                            .filter(key -> _prefix == null || aliasMap.get(key).toLowerCase().startsWith(_prefix))
                            .sorted()
                            .collect(Collectors.toList());

                    if (importKeys.size() > 0)
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

                    ObjectMapper mapper = new ObjectMapper();
                    DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
                    pp.indentArraysWith(new DefaultIndenter());

                    Object json = mapper.readValue(value.toString(), Object.class);
                    String strValue = mapper.writer(pp).writeValueAsString(json);
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
