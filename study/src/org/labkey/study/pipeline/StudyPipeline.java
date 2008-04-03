package org.labkey.study.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.ACL;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */

public class StudyPipeline extends PipelineProvider
{
    private static final Logger _log = Logger.getLogger(StudyPipeline.class);

    public StudyPipeline()
    {
        super("Study");
    }


    public void updateFileProperties(ViewContext context, List<FileEntry> entries)
    {
        if (!context.hasPermission(ACL.PERM_INSERT))
            return;

        try
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(context.getContainer());
            File rootDir = root.getRootPath();
            for (FileEntry entry : entries)
            {
                File[] files = entry.listFiles(new FileEntryFilter() {
                    public boolean accept(File f)
                    {
                        return f.getName().endsWith(".dataset");
                    }
                });
                if (files != null)
                    handleDatasetFiles(context, entry, rootDir, files);
                files = entry.listFiles(new FileEntryFilter()
                {
                    public boolean accept(File f)
                    {
                        return f.getName().endsWith(".specimens");
                    }
                });
                if (files != null)
                    handleSpecimenFiles(entry, rootDir, files);

            }
        }
        catch (Exception e)
        {
            _log.error("Exception", e);
        }
    }


    static long lastTime = 0;
    static final Object timeLock = new Object();
    private static long currentSeconds()
    {
        synchronized(timeLock)
        {
            while (true)
            {
                long sec = System.currentTimeMillis();
                sec -= sec % 1000;
                if (sec != lastTime)
                {
                    lastTime = sec;
                    return sec;
                }
                try {Thread.sleep(500);} catch(InterruptedException x){/* */}
            }
        }
    }


    public static File logForDataset(Study study, File f)
    {
        String path = f.getPath();
        String time = DateUtil.toISO(currentSeconds(),false);
        time = time.replace(":","-");
        time = time.replace(" ","_");
        return new File(path + "_" + time + ".log");
    }


    public static File lockForDataset(Study study, File f)
    {
        String path = f.getPath();
        return new File(path + "." + "_" + study.getContainer().getRowId() + ".lock");
    }


    private void handleDatasetFiles(ViewContext context, FileEntry entry, File rootDir, File[] files) throws IOException
    {
        Study study = StudyManager.getInstance().getStudy(context.getContainer());

        for (File f : files)
        {
            File lock = lockForDataset(study, f);
            if (lock.exists())
            {
                ActionURL urlReset = entry.cloneHref();
                urlReset.setPageFlow("Study");
                urlReset.setAction("resetPipeline");
                urlReset.replaceParameter("redirect", context.getActionURL().getLocalURIString());
                String path = FileUtil.relativize(rootDir, lock);
                urlReset.replaceParameter("path", path);
                if (lock.canRead() && lock.canWrite())
                    entry.addAction(new FileAction("Delete lock", urlReset, new File[]{lock}));
            }
            else
            {
                ActionURL urlImport = entry.cloneHref();
                urlImport.setPageFlow("Study");
                urlImport.setAction("importStudyBatch");
                String path = FileUtil.relativize(rootDir, f);
                urlImport.replaceParameter("path", path);
                entry.addAction(new FileAction("Import datasets", urlImport, new File[]{f}));
            }
        }
    }


    private void handleSpecimenFiles(FileEntry entry, File rootDir, File[] files) throws IOException
    {
        for (File f : files)
        {
            ActionURL urlImport = entry.cloneHref();
            urlImport.setPageFlow("Study-Samples");
            urlImport.setAction("importSpecimenData");
            String path = FileUtil.relativize(rootDir, f);
            urlImport.replaceParameter("path", path);
            entry.addAction(new FileAction("Import specimen data", urlImport, new File[]{f}));
        }
    }

}
