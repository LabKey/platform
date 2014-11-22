/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
package org.labkey.api.exp;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

/**
 * Responsible for storing whatever is desired from a data file in the database. May be the full
 * file's content, a subset, just summary, etc.
 * User: jeckels
 * Date: Sep 23, 2005
 */
public interface ExperimentDataHandler extends Handler<ExpData>
{
    @Nullable
    public DataType getDataType();

    /**
     * Import whatever content from the file is destined for storage in the database. Typically persisted in a schema
     * owned by the module that holds the implementation of the ExperimentDataHandler.
     */
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException;

    /**
     * Stream the content of this data object. Typically this just streams the bytes of the file from disk, but could
     * create something based exclusively on what's in the database.
     */
    public void exportFile(ExpData data, File dataFile, OutputStream out) throws ExperimentException;

    public ActionURL getContentURL(ExpData data);

    public void beforeDeleteData(List<ExpData> datas) throws ExperimentException;

    public void deleteData(ExpData data, Container container, User user);

    public boolean hasContentToExport(ExpData data, File file);

    void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException;

    void beforeMove(ExpData oldData, Container container, User user) throws ExperimentException;
}
