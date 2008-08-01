/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    /** Uses the URL that's stored in the exp.data table */
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
    /** Writes a relative path that's points to the file within the zip */
    ARCHIVE("Experiment archive")
    {
        public URLRewriter createURLRewriter()
        {
            return new ArchiveURLRewriter();
        }
    },
    /** Gives out a URL for downloading the file directly from the web server */
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
