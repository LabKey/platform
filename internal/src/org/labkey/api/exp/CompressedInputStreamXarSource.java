package org.labkey.api.exp;

import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * XAR file sourced from a compressed input stream. This allows operations for either in-memory virtual file or a
 * file on disk so it can be used for operations like study publish or import from a folder template as well
 * as import from a zip file.
 */
public class CompressedInputStreamXarSource extends AbstractFileXarSource
{
    private final InputStream _xarInputStream;
    private final File _logFile;
    private String _xml;

    public CompressedInputStreamXarSource(InputStream xarInputStream, File xarFile, File logFile, PipelineJob job)
    {
        super(job.getDescription(), job.getContainer(), job.getUser(), job);
        _xarInputStream = xarInputStream;
        _xmlFile = xarFile;
        _logFile = logFile;
    }

    @Override
    public void init() throws IOException, ExperimentException
    {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (OutputStream stream = new BufferedOutputStream(byteStream))
        {
            byte[] zipBytes = _xarInputStream.readAllBytes();
            _xarInputStream.close();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(zipBytes); ZipInputStream zis = new ZipInputStream(bais))
            {
                ZipEntry entry;

                while (null != (entry = zis.getNextEntry()))
                {
                    // not interested in directories, only files
                    if (!entry.isDirectory())
                    {
                        if (entry.getName().endsWith(".xar.xml"))
                        {
                            BufferedInputStream bis = new BufferedInputStream(zis);
                            FileUtil.copyData(bis, stream);
                        }
                    }
                    zis.closeEntry();
                }
            }
        }
        _xml = byteStream.toString(StandardCharsets.UTF_8);
    }

    @Override
    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        if (_xml != null)
        {
            return ExperimentArchiveDocument.Factory.parse(_xml, XmlBeansUtil.getDefaultParseOptions());
        }
        else
            throw new RuntimeException("XML source not found.");
    }

    @Override
    public File getLogFile() throws IOException
    {
        return _logFile;
    }
}
