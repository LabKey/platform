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
package org.labkey.list.view;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.MemTracker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

        ProgressIndicator progress = new ProgressIndicator();
        BackgroundListImporter importer = new BackgroundListImporter(def, getDataLoader(), progress);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<String>> future = executor.submit(importer);
        // Make sure these go away
        MemTracker.put(executor);
        MemTracker.put(future);

        String errorMessage;

        try
        {
            // Infrastructure is in place for cancelling and reporting progress, however, DomainImporterServiceAsync
            // and implementations needs to be changed to support async import.  For now, block until complete.

            while (!future.isDone())
            {
                Thread.sleep(1000);
            }

            if (!future.isCancelled())
                return future.get();

            errorMessage = "I was cancelled";
        }
        catch (InterruptedException e)
        {
            errorMessage = e.getMessage();
        }
        catch (ExecutionException e)
        {
            errorMessage = e.getMessage();
        }

        List<String> errors = new LinkedList<String>();
        errors.add(errorMessage);

        return errors;
    }

    public class BackgroundListImporter implements Callable<List<String>>
    {
        private final ListDefinition _def;
        private final DataLoader<Map<String, Object>> _loader;
        private final ListImportProgress _progress;

        private List<String> errors;

        private BackgroundListImporter(ListDefinition def, DataLoader<Map<String, Object>> loader, ListImportProgress progress)
        {
            _def = def;
            _loader = loader;
            _progress = progress;
        }

        public List<String> call() throws ImportException
        {
            try
            {
                errors = _def.insertListItems(getUser(), _loader, null, _progress);
            }
            catch (IOException ioe)
            {
                throw new ImportException(ioe.getMessage());
            }
            finally
            {
                _loader.close();
            }

            if (errors.isEmpty())
            {
                deleteImportFile();
            }

            return errors;
        }
    }

    public static class ProgressIndicator implements ListImportProgress
    {
        private volatile int _rows = 0;
        private volatile int _currentRow = 0;

        public void setTotalRows(int rows)
        {
            _rows = rows;
        }

        public int getTotalRows()
        {
            return _rows;
        }

        public void setCurrentRow(int currentRow)
        {
            _currentRow = currentRow;
        }

        public int getCurrentRow()
        {
            return _currentRow;
        }
    }
}
