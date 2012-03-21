package org.labkey.api.study.assay;

import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Writer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: brittp
 * Created: Feb 27, 2008 3:45:13 PM
 */
public class RunListDetailsQueryView extends RunListQueryView
{
    private Class<? extends Controller> _detailsActionClass;
    private String _detailsIdColumn;
    private String _dataIdColumn;

    public RunListDetailsQueryView(ExpProtocol protocol, ViewContext context, Class<? extends Controller> detailsActionClass,
                                   String detailsIdColumn, String dataIdColumn)
    {
        super(protocol, context);
        _detailsActionClass = detailsActionClass;
        _detailsIdColumn = detailsIdColumn;
        _dataIdColumn = dataIdColumn;
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.addDisplayColumn(0, new SimpleDisplayColumn()
        {
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object runId = ctx.getRow().get(_dataIdColumn);
                if (runId != null)
                {
                    ActionURL url = new ActionURL(_detailsActionClass, ctx.getContainer()).addParameter(_detailsIdColumn, "" + runId);
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("title", "View run details");
                    out.write(PageFlowUtil.textLink("run details", url, null, null, map));
                }
            }
        });
        return view;
    }
}
