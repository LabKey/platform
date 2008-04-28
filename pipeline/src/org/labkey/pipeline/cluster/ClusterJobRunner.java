package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.PipelineJobStoreImpl;
import org.labkey.pipeline.api.WorkDirectoryLocal;
import org.labkey.pipeline.xstream.PathMapper;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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

        // Initialize the Spring context
        new FileSystemXmlApplicationContext(configURIs.toArray(new String[configURIs.size()]));

        // Hack up the PathMapper for now
        PathMapper.getInstance().setPathMap(Collections.singletonMap("file:/Z:/", "file:///home/"));

        if (args.length < 1)
        {
            throw new IllegalArgumentException("First arg should be XML file path");
        }

        File file = new File(args[0]);
        if (!file.isFile())
        {
            throw new IllegalArgumentException("Could not find file " + file.getAbsolutePath());
        }

        StringBuilder xml = readFile(file);

        PipelineJob job = PipelineJobService.get().getJobStore().fromXML(xml.toString());
        System.out.println("Starting to run job " + job);
        job.runActiveTask();
        System.out.println("Finished running job");
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
