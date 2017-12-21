/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.GWTImportException;
import org.labkey.api.gwt.client.ui.domain.ImportStatus;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

    public ImportStatus importData(GWTDomain domain, Map<String, String> columnMap) throws GWTImportException
    {
        Container container = getContainer();
        StudyImpl study = StudyManager.getInstance().getStudy(container);

        DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByName(study, domain.getName());

        if (def == null)
            throw new IllegalStateException("Could not find dataset: " + domain.getName());


        if (study.getTimepointType() != TimepointType.VISIT)
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

        List<String> errors = new ArrayList<>();

        try (DataLoader loader = getDataLoader())
        {
            StudyManager.getInstance().importDatasetData(
                getUser(),
                def,
                loader,
                columnMap,
                errors,
                DatasetDefinition.CheckForDuplicates.sourceAndDestination,
                StudyManager.getInstance().getDefaultQCState(study),
                null,
                null
            );
        }
        catch (IOException | ServletException e)
        {
            throw UnexpectedException.wrap(e);
        }

        // On success, delete the import file
        if (errors.isEmpty())
        {
            deleteImportFile();
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(getUser(), Collections.singleton(def));
        }

        ImportStatus status = new ImportStatus();

        status.setComplete(true);
        status.setMessages(errors);

        return status;
    }


    @Override
    @NotNull
    public List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        Container container = getContainer();
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByName(study, orig.getName());
        if (def == null)
            throw new IllegalStateException("Could not find dataset: " + orig.getName());

        try
        {
            return super.updateDomainDescriptor(orig, update);
        }
        finally
        {
            StudyManager.getInstance().uncache(def);
        }
    }


    public ImportStatus getStatus(String jobId) throws GWTImportException
    {
        throw new GWTImportException("Shouldn't be calling getStatus() -- datasets import synchronously");
    }

    public String cancelImport(String jobId)
    {
        return null;
    }
}
