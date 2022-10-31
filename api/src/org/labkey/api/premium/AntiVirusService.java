/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.premium;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.JobRunner;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;

public interface AntiVirusService
{
    // NOTE purposefully this is not the same as the standard test file: ...EICAR-STANDARD-ANTIVIRUS-TEST-FILE...
    String TEST_VIRUS_CONTENT="X5O!P%@AP[4\\PZX54(P^)7CC)7}$LABKEY-ANTIVIRUS-TEST-FILE!$H+H*";

    static AntiVirusService get()
    {
        return ServiceRegistry.get().getService(AntiVirusService.class);
    }

    static void setInstance(AntiVirusService impl)
    {
        if (ServiceRegistry.get().getService(AntiVirusService.class) == impl)
            return;
        if (null == impl)
            ServiceRegistry.get().unregisterService(AntiVirusService.class);
        else
            ServiceRegistry.get().registerService(AntiVirusService.class, impl, false);
    }

    enum Result
    {
        OK,
        CONFIGURATION_ERROR,
        FAILED
    }

    class ScanResult
    {
        public ScanResult(Result r, String m)
        {
            this.result = r;
            this.message = m;
            filename = null;
        }
        public ScanResult(@Nullable String name, Result r, String m)
        {
            this.result = r;
            this.filename = name;
            this.message = m;
        }

        public final Result result;
        public final String filename;
        public final String message;
    }

    interface Callback<T>
    {
        void call(File f, T t, ScanResult result);
    }

    /*
     * CONSIDER: how do make sure the file is locked while it is being scanned?  use linux file locks and MD5 identifier?
     * CONSIDER: AV scanner can have lots of options, we have user type in command? use common defaults?
     */

    /* The caller of this method should not put the file in it's final location until _after_ it is scanned.
     * Alternately, the caller can keep some sort of 'not safe' flag.  In the event of a server crash or restart,
     * we don't want to assume a file has been scanned and clear if it has not.
     *
     * The caller should not expect the result callbacks to be called synchronously or on the same thread.
     */
    default <T> void queueScan(@NotNull File f, @Nullable String originalName, ViewBackgroundInfo info, T extra, Callback<T> callbackFn)
    {
        JobRunner.getDefault().submit(()-> callbackFn.call(f, extra, scan(f, originalName, info)));
    }

    default void warnAndAuditLog(Logger log, String logmessage, ViewBackgroundInfo info, @Nullable String originalName)
    {
        log.warn((null != info.getUser() ? info.getUser().getEmail() + " " : "") + logmessage);
        FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(
                info.getContainer().getId(), logmessage
        );
        if (null != info.getURL())
            event.setDirectory(info.getURL().getPath());
        event.setFile(originalName);
        AuditLogService.get().addEvent(info.getUser(), event);
    }

    /** originalName and user are used for error reporting and logging */
    ScanResult scan(@NotNull File f, @Nullable String originalName, ViewBackgroundInfo info);
}