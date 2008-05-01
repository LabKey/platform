package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.xstream.PathMapper;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.beans.factory.ListableBeanFactory;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: jeckels
 * Date: Apr 8, 2008
 */
public class ClusterJobRunner
{
    public void run(List<File> springConfigFiles, String[] args) throws IOException, URISyntaxException
    {
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

        // Set up the PipelineJobService so that Spring can configure it
        PipelineJobServiceImpl.initDefaults();

        // Initialize the Spring context
        FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(configURIs.toArray(new String[configURIs.size()]));

        if (args.length < 1)
        {
            throw new IllegalArgumentException("First arg should be URI to XML file, based on the web server's file system");
        }

        String localFile = PathMapper.getInstance().remoteToLocal(args[0]);

        File file = new File(new URI(localFile));
        if (!file.isFile())
        {
            throw new IllegalArgumentException("Could not find file " + file.getAbsolutePath());
        }

        StringBuilder xml = readFile(file);

        PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xml.toString());
        System.out.println("Starting to run task for job " + job);
        job.runActiveTask();
        System.out.println("Finished running task for job " + job);

        if (job.getActiveTaskStatus() == PipelineJob.TaskStatus.error)
        {
            job.error("Task failed");
            System.exit(1);
        }
        else if (job.getActiveTaskStatus() != PipelineJob.TaskStatus.complete)
        {
            job.error("Task finished running but was not marked as complete - it was in state " + job.getActiveTaskStatus());
            System.exit(1);
        }
    }

    private StringBuilder readFile(File file) throws IOException
    {
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
        return xml;
    }
}
