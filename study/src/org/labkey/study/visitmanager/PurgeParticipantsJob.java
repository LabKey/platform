package org.labkey.study.visitmanager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

public class PurgeParticipantsJob extends PipelineJob
{
    private final Map<String, Set<String>> _potentiallyDeletedParticipants;

    @SuppressWarnings("unused")
    @JsonCreator
    public PurgeParticipantsJob(@JsonProperty("_potentiallyDeletedParticipants") Map<String, Set<String>> potentiallyDeletedParticipants)
    {
        _potentiallyDeletedParticipants = potentiallyDeletedParticipants;
    }

    PurgeParticipantsJob(ViewBackgroundInfo info, PipeRoot pipeRoot, Map<String, Set<String>> potentiallyDeletedParticipants)
    {
        super(null, info, pipeRoot);
        _potentiallyDeletedParticipants = potentiallyDeletedParticipants;
        setLogFile(new File(pipeRoot.getLogDirectory(), FileUtil.makeFileNameWithTimestamp("purge_participants", "log")));
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

                synchronized (_potentiallyDeletedParticipants)
                {
                    if (_potentiallyDeletedParticipants.isEmpty())
                    {
                        break; // Exit while() and set status
                    }
                    // Grab the first study to be purged, and exit the synchronized block quickly
                    Iterator<Map.Entry<String, Set<String>>> i = _potentiallyDeletedParticipants.entrySet().iterator();
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

                info("Starting to purge participants in " + container.getPath());

                // Now, outside the synchronization, do the actual purge
                // TODO: Seems like this code block should be moved into VisitManager and called by PurgeParticipantsMaintenanceTask as well (it has no exception handling and doesn't call updateParticipantVisitTable()
                StudyImpl study = StudyManager.getInstance().getStudy(container);
                if (study != null)
                {
                    boolean retry = false;
                    try
                    {
                        int deleted = VisitManager.performParticipantPurge(study, potentiallyDeletedParticipants);
                        if (deleted > 0)
                        {
                            StudyManager.getInstance().getVisitManager(study).updateParticipantVisitTable(null, null);
                        }
                        info("Finished purging participants in " + container.getPath());
                    }
                    catch (TableNotFoundException tnfe)
                    {
                        // Just move on if container went away
                        if (ContainerManager.exists(container))
                        {
                            // A dataset or specimen table might have been deleted out from under us, so retry
                            info(tnfe.getFullName() + " no longer exists. Requeuing another participant purge attempt.");
                            retry = true;
                        }
                    }
                    catch (Exception e)
                    {
                        if (ContainerManager.exists(container))
                        {
                            if (SqlDialect.isObjectNotFoundException(e))
                            {
                                info("Object not found exception (" + e.getMessage() + "). Requeuing another participant purge attempt.");
                                retry = true;
                            }
                            else if (SqlDialect.isTransactionException(e))
                            {
                                info("Transaction or deadlock exception (" + e.getMessage() + "). Requeuing another participant purge attempt.");
                                retry = true;
                            }
                            else
                            {
                                // Unexpected problem... log it and continue on
                                error("Failed to purge participants for " + container.getPath(), e);
                            }
                        }
                    }

                    if (retry)
                    {
                        // throw them back on the queue
                        VisitManager vm = StudyManager.getInstance().getVisitManager(study);
                        vm.scheduleParticipantPurge(potentiallyDeletedParticipants);
                    }
                }
            }
        }
        catch (Exception e)
        {
            error("Failed to purge participants", e);
            finalStatus = TaskStatus.error;
        }

        setStatus(finalStatus);
    }
}
