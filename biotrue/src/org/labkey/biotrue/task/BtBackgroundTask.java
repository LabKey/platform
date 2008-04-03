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
