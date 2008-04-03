package org.labkey.biotrue.task;

public class BtThreadPool
{
    static private BtThreadPool instance = null;
    Thread _defaultThread;
    static public BtThreadPool get()
    {
        if (instance != null)
            return instance;
        instance = new BtThreadPool();
        return instance;
    }

    private BtThreadPool()
    {
        _defaultThread = new Thread(new BtBackgroundTask(null), "Default BT Thread");
        _defaultThread.start();
    }
}
