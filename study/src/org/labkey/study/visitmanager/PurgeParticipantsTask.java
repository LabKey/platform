/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

/**
* Created by gktaylor on 4/29/14.
*/
public class PurgeParticipantsTask extends TimerTask
{
    private Map<Container, Set<String>> _potentiallyDeletedParticipants;
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
                StudyImpl study = StudyManager.getInstance().getStudy(container);
                if (study != null)
                {
                    try
                    {
                        int deleted = VisitManager.performParticipantPurge(study, potentiallyDeletedParticipants);
                        if (deleted > 0)
                        {
                            StudyManager.getInstance().getVisitManager(study).updateParticipantVisitTable(null, null);
                        }
                    }
                    catch (RuntimeSQLException|ConcurrencyFailureException x)
                    {
                        if (SqlDialect.isTransactionException(x) || SqlDialect.isObjectNotFoundException(x))
                        {
                            // Might get an error a dataset has been deleted out from under us, so retry
                            _logger.warn("Unable to complete participant purge, requeuing for another attempt");
                            // throw them back on the queue
                            VisitManager vm = StudyManager.getInstance().getVisitManager(study);
                            vm.scheduleParticipantPurge(potentiallyDeletedParticipants);
                        }
                        else
                        {
                            _logger.error("Failed to purge participants for " + container.getPath(), x);
                        }
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
