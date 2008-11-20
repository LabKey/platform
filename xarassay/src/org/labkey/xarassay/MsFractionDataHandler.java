/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.security.User;
import org.labkey.api.study.*;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.sql.SQLException;

/**
 * User: peter@labkey.com
 * Date: Oct 23, 2008
 */
public class MsFractionDataHandler extends AbstractExperimentDataHandler
{
    public static final DataType FRACTION_DATA_TYPE = new DataType("FractionAssayData");

    public static final String FRACTION_DATA_LSID_PREFIX = "FractionAssayData";
    public static final String FRACTION_DATA_ROW_LSID_PREFIX = "FractionAssayDataRow";
    public static final String FRACTION_PROPERTY_LSID_PREFIX = "FractionProperty";
    public static final String FRACTION_INPUT_MATERIAL_DATA_PROPERTY = "SpecimenLsid";

    public static final String FRACTION_PROPERTY_NAME = "FractionName";

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        return;
    }

    public ActionURL getContentURL(Container container, ExpData data)
    {
        return null;
    }

    public static Lsid getDataRowLsid(String dataLsid, Position pos)
    {
        return getDataRowLsid(dataLsid, pos.getRow(), pos.getColumn());
    }

    public static Lsid getDataRowLsid(String dataLsid, int row, int col)
    {
        Lsid dataRowLsid = new Lsid(dataLsid);
        dataRowLsid.setNamespacePrefix(FRACTION_DATA_ROW_LSID_PREFIX);
        dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + row + ':' + col);

        return dataRowLsid;
    }

     public void deleteData(ExpData data, Container container, User user) throws ExperimentException
    {
        try {
            OntologyManager.deleteOntologyObject(data.getLSID(), container, true);
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException();
    }

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (FRACTION_DATA_LSID_PREFIX.equals(lsid.getNamespacePrefix()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}