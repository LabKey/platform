/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.FileUtil;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Options for how exp.data URL references can be exported in a XAR.
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
                public String rewriteURL(Path path, ExpData data, String roleName, ExpRun experimentRun, User user) throws ExperimentException
                {
                    if (path == null)
                        return null;

                     return FileUtil.pathToString(path);
                }
            };
        }
    },
    /** Writes a relative path that's points to the file within the zip */
    ARCHIVE("Experiment archive")
    {
        public URLRewriter createURLRewriter()
        {
            return new ArchiveURLRewriter(true, null);
        }
    },
    /**
     * Tries to make a path relative to the run's location 
     */
    RUN_RELATIVE_LOCATION("Path relative to experiment run")
    {
        public URLRewriter createURLRewriter()
        {
            return new URLRewriter()
            {
                public String rewriteURL(Path path, ExpData data, String roleName, ExpRun expRun, User user) throws ExperimentException
                {
                    try
                    {
                        if (path == null)
                            return null;

                        if (expRun == null || expRun.getFilePathRoot() == null)
                        {
                            return FileUtil.pathToString(path);
                        }
                        return FileUtil.relativizeUnix(expRun.getFilePathRootPath(), path, false);
                    }
                    catch (IOException e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            };
        }
    },
    /** Gives out a URL for downloading the file directly from the web server */
    WEB_ADDRESSABLE("Web addressable")
    {
        public URLRewriter createURLRewriter()
        {
            return new URLRewriter()
            {
                public String rewriteURL(Path f, ExpData data, String roleName, ExpRun experimentRun, User user)
                {
                    if (data == null)
                        return null;

                    ActionURL dataURL = new ActionURL(ExperimentController.ShowFileAction.class, data.getContainer());
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
