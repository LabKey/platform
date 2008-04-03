package org.labkey.biotrue.task;

import org.apache.log4j.Logger;
import org.labkey.biotrue.soapmodel.Browse_response;
import org.labkey.biotrue.soapmodel.Download_response;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.objectmodel.BtEntity;
import org.labkey.biotrue.objectmodel.BtServer;
import org.labkey.api.util.FileUtil;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

import com.ice.tar.TarArchive;

public class DownloadTask extends BtTask
{
    static final private Logger _log = Logger.getLogger(DownloadTask.class);
    public DownloadTask(Task task)
    {
        super(task);
    }

    private List<File> findFiles(File directory)
    {
        List<File> ret = new ArrayList();
        File[] files = directory.listFiles();
        for (File file : files)
        {
            if (file.isDirectory())
            {
                ret.addAll(findFiles(file));
            }
            else
            {
                ret.add(file);
            }
        }
        return ret;
    }

    public void doRun() throws Exception
    {
        BtEntity entity = getEntity();
        BtServer server = getServer();
        Browse_response login = loginBrowse(null);
        Download_response download = getServer().download(login.getData().getSession_id(), entity);
        File targetFile = new File(server.getTempDirectory(), "dir" + getTask().getRowId());

        FileUtil.deleteDir(targetFile);
        targetFile.mkdir();
        URL url = new URL(download.getData().getUrl());
        TarArchive tar = new TarArchive(url.openStream());
        tar.extractContents(targetFile);
        List<File> files = findFiles(targetFile);
        if (files.isEmpty())
        {
            _log.error("No files found after extracting.");
        }
        else
        {
            entity.ensurePhysicalFiles(files);
        }
        FileUtil.deleteDir(targetFile);
    }
}
