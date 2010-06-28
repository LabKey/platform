package org.labkey.api.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
* User: adam
* Date: Jun 22, 2010
* Time: 12:28:48 AM
*/
public class Stats
{
    public AtomicLong gets = new AtomicLong(0);
    public AtomicLong misses = new AtomicLong(0);
    public AtomicLong puts = new AtomicLong(0);
    public AtomicLong expirations = new AtomicLong(0);
    public AtomicLong removes = new AtomicLong(0);
    public AtomicLong clears = new AtomicLong(0);
    public AtomicLong max_size = new AtomicLong(0);
}
