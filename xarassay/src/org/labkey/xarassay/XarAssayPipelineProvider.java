package org.labkey.xarassay;

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewURLHelper;

import java.util.List;
import java.util.ListIterator;

/**
 * User: peter@labkey.com
 * Date: Aug 24, 2006
 * Time: 12:45:45 PM
 */
public class XarAssayPipelineProvider extends PipelineProvider
{
    public static String name = "XarAssay";

    public XarAssayPipelineProvider()
    {
        super(name);
    }

    public boolean isStatusViewableFile(String name, String basename)
    {
        return true;

    }

    public void updateFileProperties(ViewContext context, List<FileEntry> entries)
    {

        for (ListIterator<FileEntry> it = entries.listIterator(); it.hasNext();)
        {
            FileEntry entry = it.next();
            if (!entry.isDirectory())
            {
                continue;
            }
            ViewURLHelper actionUrl = entry.cloneHref();
            actionUrl.setPageFlow("XarAssay");
            actionUrl.setAction("chooseAssay");
            String protId = context.getRequest().getParameter("rowId");
            if (null != protId)
                actionUrl.addParameter("rowId", protId);

            //todo:  make it possible for xarAssayProvider extensions to each put up their own buttons
            FileAction fa = new FileAction("Create Assay Run", actionUrl, entry.listFiles(new XarAssayProvider.AnalyzeFileFilter()));
            entry.addAction(fa);

        }
    }


}
