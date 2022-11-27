package org.labkey.study.visitmanager;

import datadog.trace.api.Trace;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.TableNotFoundException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PurgeParticipantsJob extends PipelineJob
{
    @SuppressWarnings("unused") // For deserialization
    public PurgeParticipantsJob()
    {
    }

    PurgeParticipantsJob(ViewBackgroundInfo info, PipeRoot pipeRoot)
    {
        super("StudyParticipantPurge", info, pipeRoot);
        setLogFile(new File(pipeRoot.getLogDirectory(), FileUtil.makeFileNameWithTimestamp("purge_participants", "log")).toPath());
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Participant Purge";
    }

    @Override
    public void run()
    {
        TaskStatus finalStatus = TaskStatus.complete;

        try
        {
            while (true)
            {
                String containerId;
                Set<String> potentiallyDeletedParticipants;

                synchronized (VisitManager.POTENTIALLY_DELETED_PARTICIPANTS)
                {
                    if (VisitManager.POTENTIALLY_DELETED_PARTICIPANTS.isEmpty())
                    {
                        break; // Exit while() and set status
                    }
                    // Grab the first study to be purged, and exit the synchronized block quickly
                    Iterator<Map.Entry<String, Set<String>>> i = VisitManager.POTENTIALLY_DELETED_PARTICIPANTS.entrySet().iterator();
                    Map.Entry<String, Set<String>> entry = i.next();
                    i.remove();
                    containerId = entry.getKey();
                    potentiallyDeletedParticipants = entry.getValue();
                }

                Container container = ContainerManager.getForId(containerId);

                if (null == container)
                {
                    info("Container not found: " + containerId);
                    continue;
                }

                // Now, outside the synchronization, do the actual purge
                new ParticipantPurger(container, potentiallyDeletedParticipants, this::info, this::error).purgeParticipants();
            }
        }
        catch (Exception e)
        {
            error("Failed to purge participants", e);
            finalStatus = TaskStatus.error;
        }
        finally
        {
            PurgeParticipantsTask.JOB_QUEUED = false;
        }

        setStatus(finalStatus);
    }

    public static class ParticipantPurger
    {
        private final Container _container;
        private final Set<String> _potentiallyDeletedParticipants;
        private final Consumer<String> _info;
        private final BiConsumer<String, Throwable> _error;

        public ParticipantPurger(Container container, Set<String> potentiallyDeletedParticipants, Consumer<String> info, BiConsumer<String, Throwable> error)
        {
            _container = container;
            _potentiallyDeletedParticipants = potentiallyDeletedParticipants;
            _info = info;
            _error = error;
        }

        @Trace
        public void purgeParticipants()
        {
            // TODO: Seems like this code block should be moved into VisitManager and called by PurgeParticipantsMaintenanceTask as well (it has no exception handling and doesn't call updateParticipantVisitTable()
            StudyImpl study = StudyManager.getInstance().getStudy(_container);
            if (study != null)
            {
                boolean retry = false;
                try
                {
                    _info.accept("Starting to purge participants in " + _container.getPath());
                    int deleted = VisitManager.performParticipantPurge(study, _potentiallyDeletedParticipants);
                    if (deleted > 0)
                    {
                        StudyManager.getInstance().getVisitManager(study).updateParticipantVisitTable(null, null);
                    }
                    _info.accept("Finished purging participants in " + _container.getPath());
                }
                catch (TableNotFoundException tnfe)
                {
                    // Log and retry if container still exists
                    if (ContainerManager.exists(_container))
                    {
                        // A dataset or specimen table might have been deleted out from under us, so retry
                        _info.accept(tnfe.getFullName() + " no longer exists. Requeuing another participant purge attempt.");
                        retry = true;
                    }
                }
                catch (Exception e)
                {
                    if (ContainerManager.exists(_container))
                    {
                        if (SqlDialect.isObjectNotFoundException(e))
                        {
                            _info.accept("Object not found exception (" + e.getMessage() + "). Requeuing another participant purge attempt.");
                            retry = true;
                        }
                        else if (SqlDialect.isTransactionException(e))
                        {
                            _info.accept("Transaction or deadlock exception (" + e.getMessage() + "). Requeuing another participant purge attempt.");
                            retry = true;
                        }
                        else
                        {
                            // Unexpected problem... log it and continue on
                            _error.accept("Failed to purge participants for " + _container.getPath(), e);
                        }
                    }
                }

                if (retry)
                {
                    // throw them back on the queue
                    VisitManager vm = StudyManager.getInstance().getVisitManager(study);
                    vm.scheduleParticipantPurge(_potentiallyDeletedParticipants);
                }
            }
        }
    }
}
