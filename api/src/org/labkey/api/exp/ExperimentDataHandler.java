/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.util.List;

/**
 * User: jeckels
 * Date: Sep 23, 2005
 */
public interface ExperimentDataHandler extends Handler<ExpData>
{
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException;

    public void exportFile(ExpData data, File dataFile, OutputStream out) throws ExperimentException;

    public ActionURL getContentURL(Container container, ExpData data);

    public void beforeDeleteData(List<ExpData> datas) throws ExperimentException;

    public void deleteData(ExpData data, Container container, User user) throws ExperimentException;

    public boolean hasContentToExport(ExpData data, File file);

    void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException;

    void beforeMove(ExpData oldData, Container container, User user) throws ExperimentException;
}
