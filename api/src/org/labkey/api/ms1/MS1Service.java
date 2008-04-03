package org.labkey.api.ms1;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * MS1 Module Service
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Oct 26, 2007
 * Time: 10:26:10 AM
 */
public class MS1Service
{
    public static final String DB_SCHEMA_NAME = "ms1";
    public static final String PUBLIC_SCHEMA_NAME = "ms1";

    public enum Tables
    {
        Features,
        Files,
        Scans,
        PeakFamilies,
        PeaksToFamilies,
        Peaks,
        Software,
        SoftwareParams,
        Calibrations;
        
        public String getFullName()
        {
            return DB_SCHEMA_NAME + "." + this.name();
        }
    }

    private static Service _serviceImpl = null;

    public interface Service
    {
        TableInfo createFeaturesTableInfo(User user, Container container);
        TableInfo createFeaturesTableInfo(User user, Container container, boolean includePepFk);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
