package org.labkey.api.exp.api;

import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractExperimentDataHandler implements ExperimentDataHandler
{
    public void exportFile(ExpData data, File dataFile, OutputStream out) throws ExperimentException
    {
        if (dataFile != null)
        {
            FileInputStream fIn = null;
            try
            {
                fIn = new FileInputStream(dataFile);
                byte[] b = new byte[4096];
                int i;
                while((i = fIn.read(b)) != -1)
                {
                    out.write(b, 0, i);
                }
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
            finally
            {
                if (fIn != null) { try { fIn.close(); } catch (IOException e) {}}
            }
        }
    }

    public void beforeDeleteData(List<ExpData> data) throws ExperimentException
    {
    }

    public boolean hasContentToExport(ExpData data, File file)
    {
        return NetworkDrive.exists(file) && file.isFile();
    }

    public void beforeMove(ExpData oldData, Container container, User user) throws ExperimentException
    {
        
    }
}
