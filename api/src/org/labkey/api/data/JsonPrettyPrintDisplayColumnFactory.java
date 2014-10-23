package org.labkey.api.data;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;

/**
 * User: tgaluhn
 * Date: 10/17/2014
 *
 */
public class JsonPrettyPrintDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JsonPrettyPrintDataColumn(colInfo);
    }

    static class JsonPrettyPrintDataColumn extends DataColumn
    {
        JsonPrettyPrintDataColumn(ColumnInfo col)
        {
            super(col,false);
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            Object value = ctx.get(getBoundColumn().getFieldKey());
            if (value == null)
            {
                // If we couldn't find it by FieldKey, check by alias as well
                value = getValue(ctx);
            }
            if (null == value)
                return "";

            ObjectMapper mapper = new ObjectMapper();
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            pp.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter());

            try
            {
                Object json = mapper.readValue(value.toString(), Object.class);
                String output = PageFlowUtil.filter(mapper.writer(pp).writeValueAsString(json));
                // Too bad there's no way to configure EOL characters for the Jackson pretty printer.
                // It seems to use system defaults.
                return output.replaceAll("\r\n|\r|\n","<br/>").replace("  ", "&nbsp;&nbsp;");
            }
            catch (IOException e)
            {
                return "Bad JSON object";
            }
        }
    }
}
