package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.data.Container;
import org.labkey.common.util.Pair;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jan 2, 2008
 */
public class PipelineDataCollector extends AbstractAssayDataCollector
{
    public static final String FILE_COLLECTION_ID_PARAMETER_NAME = ".fileCollectionId";

    public PipelineDataCollector()
    {
    }

    public String getHTML(AssayRunUploadContext context)
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (files.isEmpty() || files.get(0).isEmpty())
        {
            return "No files have been selected.";
        }

        StringBuilder sb = new StringBuilder("The following file");
        sb.append(files.get(0).size() > 1 ? "s have" : " has");
        sb.append(" already been selected:<ul>");
        for (File file : files.get(0).values())
        {
            sb.append("<li>");
            sb.append(PageFlowUtil.filter(file.getAbsolutePath()));
            sb.append("</li>");
        }
        sb.append("</ul>");
        if (files.size() > 1)
        {
            sb.append(" (");
            sb.append(files.size() - 1);
            sb.append(" more file set");
            sb.append(files.size() > 2 ? "s" : "");
            sb.append(" available after this run is complete.)");
        }
        return sb.toString();
    }

    public String getShortName()
    {
        return "Pipeline";
    }

    public String getDescription()
    {
        return "Use a file from the Data Pipeline";
    }

    public static synchronized void setFileCollection(HttpSession session, Container c, ExpProtocol protocol, List<Map<String, File>> files)
    {
        List<Map<String, File>> existingFiles = getFileCollection(session, c, protocol);
        existingFiles.clear();
        existingFiles.addAll(files);
    }

    private List<Map<String, File>> getFileCollection(AssayRunUploadContext context)
    {
        return getFileCollection(context.getRequest().getSession(true), context.getContainer(), context.getProtocol());
    }

    public static List<Map<String, File>> getFileCollection(HttpSession session, Container c, ExpProtocol protocol)
    {
        Map<Pair<Container, Integer>, List<Map<String, File>>> collections = (Map<Pair<Container, Integer>, List<Map<String, File>>>) session.getAttribute(PipelineDataCollector.class.getName());
        if (collections == null)
        {
            collections = new HashMap<Pair<Container, Integer>, List<Map<String, File>>>();
            session.setAttribute(PipelineDataCollector.class.getName(), collections);
        }
        Pair<Container, Integer> key = new Pair<Container, Integer>(c, protocol.getRowId());
        List<Map<String, File>> result = collections.get(key);
        if (result == null)
        {
            result = new ArrayList<Map<String, File>>();
            collections.put(key, result);
        }
        return result;
    }

    public Map<String, File> createData(AssayRunUploadContext context) throws IOException, ExperimentException
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (files.isEmpty())
        {
            throw new ExperimentException("No files have been selected");
        }
        return files.get(0);
    }

    public boolean isVisible()
    {
        return true;
    }

    public void uploadComplete(AssayRunUploadContext context)
    {
        List<Map<String, File>> files = getFileCollection(context);
        if (!files.isEmpty())
        {
            files.remove(0);
        }
    }

    public boolean allowAdditionalUpload(AssayRunUploadContext context)
    {
        return getFileCollection(context).size() > 1; 
    }
}
