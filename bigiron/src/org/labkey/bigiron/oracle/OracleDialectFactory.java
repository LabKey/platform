package org.labkey.bigiron.oracle;

import org.apache.log4j.Logger;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.util.VersionNumber;

import java.util.Collection;
import java.util.Collections;

/**
 * User: trent
 * Date: 6/10/11
 * Time: 3:40 PM
 */
public class OracleDialectFactory extends SqlDialectFactory
{
    private static final Logger _log = Logger.getLogger(OracleDialectFactory.class);

    private String getProductName()
    {
        return "Oracle";
    }

    @Override
    public SqlDialect createFromDriverClassName(String driverClassName)
    {
        return null;   // Only used to create a new database, which we never do on Oracle
    }

    private final static String PRE_VERSION_CLAUSE = "Release ";
    private final static String POST_VERSION_CLAUSE = "-";

    @Override
    public SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException
    {
        if (!dataBaseProductName.equals(getProductName()))
            return null;

        /*
            Parse the product version from the metadata, to return only the version number (i.e. remove text)
            For the jdbcdriver I have, version is returned like:

            Oracle Database 11g Enterprise Edition Release 11.2.0.2.0 - 64bit Production
            With the Partitioning, OLAP, Data Mining and Real Application Testing options
        */

        int startIndex = databaseProductVersion.indexOf(PRE_VERSION_CLAUSE) + PRE_VERSION_CLAUSE.length();
        int endIndex = databaseProductVersion.indexOf(POST_VERSION_CLAUSE) - 1;

        VersionNumber versionNumber = new VersionNumber(databaseProductVersion.substring(startIndex, endIndex));

        // Restrict to 11g
        if (versionNumber.getMajor() >= 11)
        {
            if (versionNumber.getVersionInt() == 111)
                return new Oracle11gR1Dialect();

            if (versionNumber.getVersionInt() == 112)
                return new Oracle11gR2Dialect();
        }

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported. You must upgrade your database server installation to " + getProductName() + " version 11g or greater.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }
}
