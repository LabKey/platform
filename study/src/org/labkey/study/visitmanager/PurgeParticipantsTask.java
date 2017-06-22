/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.study.visitmanager;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.TableNotFoundException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

/**
 * Background task to remove participants that are no longer referenced in datasets or specimen data.
 * Created by gktaylor on 4/29/14.
 */
public class PurgeParticipantsTask extends TimerTask
{
    private final Map<Container, Set<String>> _potentiallyDeletedParticipants;
    private static final Logger _logger = Logger.getLogger(PurgeParticipantsTask.class);

    public PurgeParticipantsTask(Map<Container, Set<String>> potentiallyDeletedParticipants)
    {
        _potentiallyDeletedParticipants = potentiallyDeletedParticipants;
    }

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                Container container;
                Set<String> potentiallyDeletedParticipants;

                synchronized (_potentiallyDeletedParticipants)
                {
                    if (_potentiallyDeletedParticipants.isEmpty())
                    {
                        return;
                    }
                    // Grab the first study to be purged, and exit the synchronized block quickly
                    Iterator<Map.Entry<Container, Set<String>>> i = _potentiallyDeletedParticipants.entrySet().iterator();
                    Map.Entry<Container, Set<String>> entry = i.next();
                    i.remove();
                    container = entry.getKey();
                    potentiallyDeletedParticipants = entry.getValue();
                }

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
                    }
                    catch (TableNotFoundException tnfe)
                    {
                        // Just move on if container went away
                        if (ContainerManager.exists(container))
                        {
                            // A dataset or specimen table might have been deleted out from under us, so retry
                            _logger.info(tnfe.getFullName() + " no longer exists. Requeuing another participant purge attempt.");
                            retry = true;
                        }
                    }
                    catch (Exception e)
                    {
                        if (ContainerManager.exists(container))
                        {
                            if (SqlDialect.isObjectNotFoundException(e))
                            {
                                _logger.info("Object not found exception (" + e.getMessage() + "). Requeuing another participant purge attempt.");
                                retry = true;
                            }
                            else
                            {
                                // Unexpected problem... log it and continue on
                                _logger.error("Failed to purge participants for " + container.getPath(), e);
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
            _logger.error("Failed to purge participants", e);
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }
}
