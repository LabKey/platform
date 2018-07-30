package org.labkey.api.premium;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.JobRunner;

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
        ServiceRegistry.get().registerService(AntiVirusService.class, impl);
    }

    enum Result
    {
        OK,
        FAILED
    }

    class ScanResult
    {
        public ScanResult(Result r, String m)
        {
            result = r;
            message = m;
        }

        public final Result result;
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
    default <T> void queueScan(File f, T extra, Callback<T> callbackFn)
    {
        JobRunner.getDefault().submit(()-> callbackFn.call(f, extra, scan(f)));
    }

    ScanResult scan(File f);
}