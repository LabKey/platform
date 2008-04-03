package org.labkey.experiment;

import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Oct 4, 2007
 */
public class SampleSetDisplayColumn extends SimpleDisplayColumn
{
    private final ExpMaterial _material;

    public SampleSetDisplayColumn(ExpMaterial material)
    {
        _material = material;
        setCaption("Sample Set");
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ExpSampleSet ss = _material.getSampleSet();

        if (ss == null)
        {
            out.write("Not a member of a sample set");
        }
        else
        {
            ActionURL url = new ActionURL("Experiment", "showMaterialSource", ss.getContainer());
            url.addParameter("rowId", Integer.toString(ss.getRowId()));
            out.write("<a href=\"" + url.toString() + "\">" + PageFlowUtil.filter(ss.getName()) + "</a>");
        }
    }
}
