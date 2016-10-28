/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.GWTImportException;
import org.labkey.api.gwt.client.ui.domain.ImportStatus;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ViewContext;
import org.labkey.list.controllers.ListController;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

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

    public ImportStatus importData(GWTDomain gwtDomain, Map<String, String> mappedColumnNames) throws GWTImportException
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

        // A one-shot, single-thread executor service to handle the background import while allowing cancelling,
        // a progress indicator, and return of errors.  Give the thread a name and mem track it.  Submit the importer
        // task and shut down immediately.
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "List Import Background Thread");
                MemTracker.getInstance().put(t);
                return t;
            }
        });
        Future<List<String>> future = executor.submit(importer);
        executor.shutdown();

        ImportContext context = new ImportContext(future, progress, def);
        setContext(context.getJobId(), context);

        // Make sure these go away
        MemTracker.getInstance().put(executor);
        MemTracker.getInstance().put(future);
        MemTracker.getInstance().put(context);
        MemTracker.getInstance().put(progress);

        return context.getImportStatus();
    }

    private void setContext(String jobId, ImportContext context)
    {
        HttpSession session = getViewContext().getSession();
        session.setAttribute("ListImportContext:" + jobId, context);
    }

    private void clearContext(String jobId)
    {
        HttpSession session = getViewContext().getSession();
        session.removeAttribute("ListImportContext:" + jobId);
    }

    private ImportContext getContext(String jobId)
    {
        HttpSession session = getViewContext().getSession();
        return (ImportContext)session.getAttribute("ListImportContext:" + jobId);
    }

    public ImportStatus getStatus(String jobId)
    {
        ImportContext context = getContext(jobId);
        ImportStatus status;

        if (null != context)
        {
            status = context.getImportStatus();

            if (status.isComplete())
                clearContext(jobId);
        }
        else
        {
            // Context is not stashed in the session... send back a dummy status that claims to be complete
            status = new ImportStatus();
            status.setComplete(true);
            status.setJobId(jobId);
        }

        return status;
    }


    private class BackgroundListImporter implements Callable<List<String>>
    {
        private final ListDefinition _def;
        private final DataLoader _loader;
        private final ListImportProgress _progress;

        private BackgroundListImporter(ListDefinition def, DataLoader loader, ListImportProgress progress)
        {
            _def = def;
            _loader = loader;
            _progress = progress;
        }

        public List<String> call() throws GWTImportException
        {
            List<String> messages = new ArrayList<>();
            BatchValidationException errors = new BatchValidationException();

            try
            {
                // TODO: Instead of throwing a CancellationException, this method should return a status object holding
                //  errors and cancellation status.  This will require reworking OntologyManager, so signal with an
                //  exception for now.
                _def.insertListItems(getUser(), getContainer(), _loader, errors, null, _progress, false, false);
            }
            catch (CancellationException ce)
            {
                return Collections.emptyList();
            }
            catch (IOException ioe)
            {
                throw new GWTImportException(ioe.getMessage());
            }
            finally
            {
                _loader.close();
            }

            if (errors.hasErrors())
            {
                for (ValidationException v : errors.getRowErrors())
                    messages.add(v.getMessage());
            }
            else
            {
                deleteImportFile();
            }

            return messages;
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


    private static class ImportContext
    {
        private final Future<List<String>> _future;
        private final ProgressIndicator _progress;
        private final String _jobId;
        private final ListDefinition _listDef;

        private ImportContext(Future<List<String>> future, ProgressIndicator progress, ListDefinition def)
        {
            _future = future;
            _progress = progress;
            _jobId = GUID.makeHash();   // Unique ID used by client to retrieve import status
            _listDef = def;
        }

        private String getJobId()
        {
            return _jobId;
        }

        private ImportStatus getImportStatus()
        {
            ImportStatus status = new ImportStatus();
            status.setJobId(_jobId);
            status.setTotalRows(_progress.getTotalRows());
            status.setCurrentRow(_progress.getCurrentRow());

            boolean done = _future.isDone();
            status.setComplete(done);

            if (done)
            {
                if (!_future.isCancelled())
                {
                    List<String> messages;

                    try
                    {
                        messages = _future.get();
                    }
                    catch (Exception e)
                    {
                        messages = new LinkedList<>();
                        messages.add(e.getMessage());
                    }

                    status.setMessages(messages);
                }
            }

            return status;
        }

        public Future<List<String>> getFuture()
        {
            return _future;
        }

        public ListDefinition getListDef()
        {
            return _listDef;
        }
    }

    public String cancelImport(String jobId) throws GWTImportException
    {
        ImportContext context = getContext(jobId);
        Container c;

        if (null == context)
        {
            c = getContainer();
        }
        else
        {
            Future<List<String>> future = context.getFuture();
            future.cancel(true);

            // CONSIDER: wait for canceled thread to finish?

            ListDefinition def = context.getListDef();

            try
            {
                def.delete(getUser());
                c = def.getContainer();
            }
            catch (Exception e)
            {
                throw new GWTImportException(e.getMessage());
            }
            finally
            {
                clearContext(jobId);
            }
        }

        return ListController.getBeginURL(c).getLocalURIString();
    }

    @Override
    @NotNull
    public List<String> updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        Domain domain = PropertyService.get().getDomain(orig.getDomainId());
        if (domain == null)
            throw new IllegalArgumentException("Attempt to import data into a non-existent domain");

        if (!domain.getContainer().equals(getContainer()))
            throw new IllegalArgumentException("Attempt to import data outside of this container");

        ListDefinition def = ListService.get().getList(domain);
        if (def == null)
            throw new IllegalArgumentException("List definition not found");

        if (!ListDefinition.KeyType.AutoIncrementInteger.equals(def.getKeyType()))
        {
            // File should define Key if not autoIncr
            boolean foundKeyField = false;
            for (GWTPropertyDescriptor field : update.getFields())
            {
                if (field.getName().equalsIgnoreCase(def.getKeyName()))
                {
                    foundKeyField = true;
                    break;
                }
            }
            if (!foundKeyField)
                return Collections.singletonList("Because key field is not an Auto-increment integer, the import file must contain the key field '" + def.getKeyName() + "'.");
        }

        return super.updateDomainDescriptor(orig, update);
    }

}
