package org.labkey.api.util;

import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockWithName extends ReentrantLock
{
    private final String _name;

    public ReentrantLockWithName(String name)
    {
        _name = name;
    }

    public ReentrantLockWithName(Class<?> clz, String name)
    {
        _name = clz.getName() + ":" + name;
    }

    @Override
    public String toString()
    {
        return super.toString() + " (name=" + _name + ")";
    }
}
