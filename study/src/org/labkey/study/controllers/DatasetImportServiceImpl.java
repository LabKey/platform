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
package org.labkey.study.controllers;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.common.tools.DataLoader;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 6, 2008
 */
public class DatasetImportServiceImpl extends DomainImporterServiceBase
{
    public DatasetImportServiceImpl(ViewContext context)
    {
        super(context);
    }

    public List<String> importData(GWTDomain domain, Map<String, String> columnMap) throws ImportException
    {
        Container container = getContainer();
        Study study = StudyManager.getInstance().getStudy(container);

        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, domain.getName());

        if (def == null)
            throw new IllegalStateException("Could not find dataset: " + domain.getName());


        if (study.isDateBased())
        {
            // We've told the user to select their "Visit Date",
            // but it's really called "Date" in the database.
            String dateColName = null;
            for (Map.Entry<String,String> entry : columnMap.entrySet())
            {
                if (entry.getValue().equals("Visit Date"))
                {
                    dateColName = entry.getKey();
                }
            }
            columnMap.put(dateColName, "Date");
        }

        List<String> errors = new ArrayList<String>();

        DataLoader loader = getDataLoader();
        try
        {
            StudyManager.getInstance().importDatasetData(
            study,
            getUser(),
            def,
            loader,
            System.currentTimeMillis(),
            columnMap,
            errors,
            true,
            StudyManager.getInstance().getDefaultQCState(study)
            );
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (ServletException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
        finally
        {
            loader.close();
        }

        // On success, delete the import file
        if (errors.isEmpty())
        {
            deleteImportFile();
            StudyManager.getInstance().recomputeStudyDataVisitDate(study);
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(getUser());
        }

        return errors;
    }
}
