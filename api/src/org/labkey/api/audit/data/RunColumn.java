package org.labkey.api.audit.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 15, 2012
 */
public class RunColumn extends ExperimentAuditColumn
{
    public RunColumn(ColumnInfo col, ColumnInfo containerId, @Nullable ColumnInfo defaultName)
    {
        super(col, containerId, defaultName);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String runLsid = (String)getBoundColumn().getValue(ctx);
        String cId = (String)ctx.get("ContainerId");
        if (runLsid != null && cId != null)
        {
            Container c = ContainerManager.getForId(cId);
            if (c != null)
            {
                ExpRun run = ExperimentService.get().getExpRun(runLsid);
                ExpProtocol protocol = null;
                if (run != null)
                    protocol = run.getProtocol();
                AssayProvider provider = null;
                if (protocol != null)
                    provider = AssayService.get().getProvider(protocol);

                ActionURL url = null;
                if (provider != null)
                    url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, protocol, run.getRowId());
                else if (run != null)
                    url = PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphURL(run);

                if (url != null)
                {
                    out.write("<a href=\"" + url.getLocalURIString() + "\">" + PageFlowUtil.filter(run.getName()) + "</a>");
                    return;
                }
            }
        }

        if (_defaultName != null)
        {
            Pair<String, String> key3 = splitKey3(_defaultName.getValue(ctx));
            out.write(key3 != null && key3.getValue() != null ? PageFlowUtil.filter(key3.getValue()) : "&nbsp;");
        }
        else
            out.write("&nbsp;");
    }
}
