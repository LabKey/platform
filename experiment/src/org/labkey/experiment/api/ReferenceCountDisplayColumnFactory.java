package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.experiment.FileLinkFileListener;

public class ReferenceCountDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new ExpDataFileColumn(colInfo)
        {
            private Long getCount(ExpData data)
            {

                if (data == null || StringUtils.isEmpty(data.getDataFileUrl()) || data.getFile() == null)
                    return null;
                else
                {
                    FileLinkFileListener fileListener = new FileLinkFileListener();
                    SQLFragment unionSql = fileListener.listFilesQuery(true, data.getFile().getAbsolutePath());

                    return new SqlSelector(CoreSchema.getInstance().getSchema(), unionSql).getRowCount();
                }
            }

            @Override
            protected void renderData(HtmlWriter out, ExpData data)
            {
                Long val = getCount(data);
                if (val == null)
                    out.write("");
                else
                    out.write(val);
            }

            @Override
            protected Object getJsonValue(ExpData data)
            {
                return getCount(data);
            }
        };
    }
}
