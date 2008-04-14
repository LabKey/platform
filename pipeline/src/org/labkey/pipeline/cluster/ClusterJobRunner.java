package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.PipelineJobStoreImpl;
import org.labkey.pipeline.api.WorkDirectoryLocal;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Apr 8, 2008
 */
public class ClusterJobRunner
{
    public void run(List<File> springConfigFiles, String[] args) throws IOException
    {
        PipelineJobServiceImpl pjs = new PipelineJobServiceImpl();
        pjs.setJobStore(new PipelineJobStoreImpl());
        pjs.setWorkDirFactory(new WorkDirectoryLocal.Factory());
        PipelineJobService.setInstance(pjs);

        List<String> configURIs = new ArrayList<String>();
        for (File file : springConfigFiles)
        {
            if (file.getName().indexOf("pipeline") != -1)
            {
                configURIs.add(0, file.getAbsoluteFile().toURI().toString());
            }
            else
            {
                configURIs.add(file.getAbsoluteFile().toURI().toString());
            }
        }

        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(configURIs.toArray(new String[configURIs.size()]));

        if (args.length < 1)
        {
            printUsage();
            System.exit(1);
        }

        File file = new File(args[0]);
        if (!file.isFile())
        {
            System.err.println("Could not find file " + file.getAbsolutePath());
            printUsage();
            System.exit(1);
        }

        InputStream fIn = null;
        StringBuilder xml = new StringBuilder();
        try
        {
            fIn = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
            String line;
            while ((line = reader.readLine()) != null)
            {
                xml.append(line);
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }

        PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xml.toString());
        System.out.println(job);
    }

    public static void printUsage()
    {
        System.out.println("java org.labkey.pipeline.cluster.ClusterJobRunner [JOB_DESCRIPTION_XML]");
    }
}
