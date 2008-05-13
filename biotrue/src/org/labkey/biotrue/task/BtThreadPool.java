/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
