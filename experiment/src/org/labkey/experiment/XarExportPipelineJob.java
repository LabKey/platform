package org.labkey.experiment;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.exp.ExperimentPipelineProvider;
import org.labkey.api.exp.ExperimentException;
import org.labkey.experiment.xar.XarExportSelection;

import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Sep 12, 2006
 */
public class XarExportPipelineJob extends PipelineJob
{
    private final File _exportFile;
    private final String _fileName;
    private final LSIDRelativizer _lsidRelativizer;
    private XarExportSelection _selection;
    private final String _xarXmlFileName;

    public XarExportPipelineJob(ViewBackgroundInfo info, File pipelineDir, String fileName, LSIDRelativizer lsidRelativizer, XarExportSelection selection, String xarXmlFileName) throws SQLException
    {
        super(ExperimentPipelineProvider.NAME, info);
        _fileName = fileName;
        _lsidRelativizer = lsidRelativizer;
        _xarXmlFileName = xarXmlFileName;
        _selection = selection;

        File exportedXarsDir = new File(pipelineDir, "exportedXars");
        exportedXarsDir.mkdir();

        _exportFile = new File(exportedXarsDir, _fileName);

        setLogFile(new File(_exportFile.getPath() + ".log"));

        header("Experiment Import for " + _exportFile.getName());
        setStatus(WAITING_STATUS);
    }

    public ActionURL getStatusHref()
    {
        return null;
    }

    public String getDescription()
    {
        return "XAR export - " + _fileName;
    }

    public void run()
    {
        setStatus("EXPORTING");

        FileOutputStream fOut = null;
        try
        {
            getLogger().info("Starting to write XAR to " + _exportFile.getPath());
            XarExporter exporter = new XarExporter(_lsidRelativizer, DataURLRelativizer.ARCHIVE, _selection, _xarXmlFileName, getLogger());
            _exportFile.getParentFile().mkdirs();
            fOut = new FileOutputStream(_exportFile);
            exporter.write(fOut);
            getLogger().info("Export complete");
            setStatus(COMPLETE_STATUS);
        }
        catch (RuntimeException e)
        {
            logFailure(e);
        }
        catch (IOException e)
        {
            logFailure(e);
        }
        catch (SQLException e)
        {
            logFailure(e);
        }
        catch (ExperimentException e)
        {
            logFailure(e);
        }
        finally
        {
            if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
        }
    }

    private void logFailure(Throwable e)
    {
        getLogger().error("Failed when exporting XAR", e);
    }
}
