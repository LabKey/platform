/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.view.ViewContext;
import org.labkey.common.tools.DataLoader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 7, 2008
 */
public class ListImportServiceImpl extends DomainImporterServiceBase
{
    public ListImportServiceImpl(ViewContext context)
    {
        super(context);
    }

    public List<String> importData(GWTDomain gwtDomain, Map<String, String> mappedColumnNames) throws ImportException
    {
        Domain domain = PropertyService.get().getDomain(gwtDomain.getDomainId());
        if (domain == null)
            throw new IllegalArgumentException("Attempt to import data into a non-existent domain");

        if (!domain.getContainer().equals(getContainer()))
            throw new IllegalArgumentException("Attempt to import data outside of this container");

        ListDefinition def = ListService.get().getList(domain);
        if (def == null)
            throw new IllegalArgumentException("List definition not found");

        List<String> errors;
        DataLoader loader = getDataLoader();
        try
        {
            errors = def.insertListItems(getUser(), loader);
        }
        catch (IOException ioe)
        {
            throw new ImportException(ioe.getMessage());
        }
        finally
        {
            loader.close();
        }
        if (errors.isEmpty())
        {
            deleteImportFile();
        }

        return errors;        
    }
}
