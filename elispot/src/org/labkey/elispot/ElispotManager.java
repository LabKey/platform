package org.labkey.elispot;

public class ElispotManager
{
    private static ElispotManager _instance;

    private ElispotManager()
    {
        // prevent external construction with a private default constructor
    }

    public static synchronized ElispotManager get()
    {
        if (_instance == null)
            _instance = new ElispotManager();
        return _instance;
    }
}