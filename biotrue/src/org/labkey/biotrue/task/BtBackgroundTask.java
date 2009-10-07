/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.biotrue.task;

import org.apache.log4j.Logger;
import org.labkey.biotrue.objectmodel.BtServer;

public class BtBackgroundTask implements Runnable
{
    final private static Logger _log = Logger.getLogger(BtBackgroundTask.class);
    BtServer _server;

    public BtBackgroundTask(BtServer server)
    {
        _server = server;
    }

    private BtTask waitForTask()
    {
        BtTaskManager mgr = BtTaskManager.get();
        synchronized(mgr)
        {
            while (true)
            {
                BtTask ret = mgr.getNextTask(_server);
                if (ret != null)
                {
                    return ret;
                }
                try
                {
                    mgr.wait();
                }
                catch (InterruptedException ie)
                {
                    return null;
                }
            }
        }
    }

    public void run()
    {
        while (true)
        {
            BtTask task = waitForTask();
            if (task == null)
                return;

            try
            {
                try
                {
                    task.run();
                }
                finally
                {
                    BtTaskManager.get().taskComplete(task.getTask());
                }
            }
            catch (Throwable t)
            {
                _log.error("Error", t);
            }
        }
    }
}
