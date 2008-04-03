package org.labkey.experiment;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.api.ExperimentRun;

import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jul 28, 2006
 */
public enum DataURLRelativizer
{
    ORIGINAL_FILE_LOCATION("Original file location")
    {
        public URLRewriter createURLRewriter()
        {
            return new URLRewriter()
            {
                public String rewriteURL(File f, ExpData data, ExperimentRun experimentRun) throws ExperimentException
                {
                    try
                    {
                        if (f == null)
                        {
                            return null;
                        }
                        return f.getCanonicalPath();
                    }
                    catch (IOException e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            };
        }
    },
    ARCHIVE("Experiment archive")
    {
        public URLRewriter createURLRewriter()
        {
            return new ArchiveURLRewriter();
        }
    },
    WEB_ADDRESSABLE("Web addressable")
    {
        public URLRewriter createURLRewriter()
        {
            return new URLRewriter()
            {
                public String rewriteURL(File f, ExpData data, ExperimentRun experimentRun)
                {
                    if (data == null)
                    {
                        return null;
                    }
                    ActionURL dataURL = new ActionURL("Experiment", "showFile", data.getContainer());
                    dataURL.addParameter("rowId", Integer.toString(data.getRowId()));
                    return dataURL.getURIString();
                }
            };
        }
    };
    private String _description;

    public abstract URLRewriter createURLRewriter();

    private DataURLRelativizer(String description)
    {
        _description = description;
    }

    public String getDescription()
    {
        return _description;
    }
}
