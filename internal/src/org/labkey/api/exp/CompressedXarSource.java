package org.labkey.api.exp;

import org.labkey.api.util.FileUtil;

import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public class CompressedXarSource extends AbstractFileXarSource
{
    private final int BUFFER_SIZE = 2048;

    private final File _xarFile;

    public CompressedXarSource(File xarFile)
    {
        _xarFile = xarFile;
    }

    public void init() throws IOException, ExperimentException
    {
        File outputDir = new File(_xarFile.getPath() + ".exploded");
        FileUtil.deleteDir(outputDir);
        if (outputDir.exists())
        {
            throw new ExperimentException("Failed to clean up old directory " + outputDir);
        }
        outputDir.mkdirs();
        if (!outputDir.isDirectory())
        {
            throw new ExperimentException("Failed to create directory " + outputDir);
        }
        FileInputStream fIn = null;
        try
        {
            fIn = new FileInputStream(_xarFile);
            ZipInputStream zIn = new ZipInputStream(new BufferedInputStream(fIn));
            ZipEntry entry;
            while ((entry = zIn.getNextEntry()) != null)
            {
                int i;
                OutputStream out = null;
                try
                {
                    byte data[] = new byte[BUFFER_SIZE];
                    File destFile = new File(outputDir, entry.getName());
                    if (entry.isDirectory())
                    {
                        destFile.mkdirs();
                        if (!destFile.isDirectory())
                        {
                            throw new ExperimentException("Failed to create directory " + destFile);
                        }
                    }
                    else
                    {
                        File destDir = destFile.getParentFile();
                        destDir.mkdirs();
                        if (!destDir.isDirectory())
                        {
                            throw new ExperimentException("Failed to create directory " + destDir);
                        }
                        out = new BufferedOutputStream(new FileOutputStream(destFile), BUFFER_SIZE);
                        while ((i= zIn.read(data, 0, BUFFER_SIZE)) != -1)
                        {
                            out.write(data, 0, i);
                        }
                        if (destFile.getName().toLowerCase().endsWith(".xar.xml"))
                        {
                            if (_xmlFile != null)
                            {
                                throw new XarFormatException("XAR file " + _xarFile + " contains more than one .xar.xml file");
                            }
                            _xmlFile = destFile;
                        }
                    }
                }
                finally
                {
                    if (out != null) { try { out.close(); } catch (IOException e) {} }
                }
            }

            if (_xmlFile == null)
            {
                throw new XarFormatException("XAR file " + _xarFile + " does not contain any .xar.xml files");
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }
    }

    public File getLogFile() throws IOException
    {
        return getLogFileFor(_xarFile);
    }

    public String toString()
    {
        return _xarFile.getPath();
    }
}
