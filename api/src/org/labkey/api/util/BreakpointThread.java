/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.util;

/**
 * Starts a thread that can be used as a place to set a breakpoint when other "normal" threads are all blocked
 * or otherwise occupied. This is useful because some debugger functionality, such as a invoking methods through the
 * watch window, is not available when the VM is paused separately from a breakpoint or step operation.
 *
 * User: jeckels
 * Date: Oct 27, 2006
 */
public class BreakpointThread extends Thread implements ShutdownListener
{
    private boolean _shutdown = false;

    public BreakpointThread()
    {
        setDaemon(true);
        setName("BreakpointThread");
        ContextListener.addShutdownListener(this);
    }

    public void run()
    {
        while (!_shutdown)
        {
            try
            {
                Thread.sleep(10000);
            }
            catch (InterruptedException ignored) {}
        }
    }


    public void shutdownPre()
    {
        _shutdown = true;
        interrupt();
    }

    public void shutdownStarted()
    {
        try
        {
            join(2000);
        }
        catch (InterruptedException e) {}
    }
}
