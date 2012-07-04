package org.labkey.experiment;

import org.apache.commons.io.FileUtils;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.api.ExpData;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 7/3/12
 * Time: 7:09 AM
 */
public class DataFileSizeDisplayColumn extends SimpleDisplayColumn
{
    private ExpData _data;

    public DataFileSizeDisplayColumn(ExpData data, String name)
    {
        _data = data;
        setCaption(name);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_data == null)
        {
            out.write("");
        }
        else if (_data.getFile() == null || !_data.getFile().exists())
        {
            out.write("File not found");
        }
        else
        {
            long size = _data.getFile().length();
            out.write(FileUtils.byteCountToDisplaySize(size));
        }
    }

}
