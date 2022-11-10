package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Enum that specifies the versions of Microsoft SQL Server that LabKey supports plus their properties
 */
public enum MicrosoftSqlServerVersion
{
    /*
        Good resources for past & current SQL Server version numbers:
        - http://www.sqlteam.com/article/sql-server-versions
        - http://sqlserverbuilds.blogspot.se/
     */

    // We support 2014 and higher as the primary data source, but allow 2012/2008/2008R2 as an external data source
    SQL_SERVER_UNSUPPORTED(Integer.MIN_VALUE, true, false, false, null),
    SQL_SERVER_2008(100, false, true, false, MicrosoftSqlServer2008R2Dialect::new),
    SQL_SERVER_2012(110, false, true, false, MicrosoftSqlServer2012Dialect::new),
    SQL_SERVER_2014(120, false, true, true, MicrosoftSqlServer2014Dialect::new),
    SQL_SERVER_2016(130, false, true, true, MicrosoftSqlServer2016Dialect::new),
    SQL_SERVER_2017(140, false, true, true, MicrosoftSqlServer2017Dialect::new),
    SQL_SERVER_2019(150, false, true, true, MicrosoftSqlServer2019Dialect::new),
    SQL_SERVER_2022(160, false, false, true, MicrosoftSqlServer2022Dialect::new),
    SQL_SERVER_FUTURE(170, false, false, true, MicrosoftSqlServer2022Dialect::new);

    private final int _version;
    private final boolean _deprecated;
    private final boolean _tested;
    private final boolean _allowedAsPrimaryDataSource;
    private final Supplier<? extends MicrosoftSqlServer2008R2Dialect> _dialectFactory;

    MicrosoftSqlServerVersion(int version, boolean deprecated, boolean tested, boolean allowedAsPrimaryDataSource, Supplier<? extends MicrosoftSqlServer2008R2Dialect> dialectFactory)
    {
        _version = version;
        _deprecated = deprecated;
        _tested = tested;
        _allowedAsPrimaryDataSource = allowedAsPrimaryDataSource;
        _dialectFactory = dialectFactory;
    }

    // Should LabKey warn administrators that support for this SQL Server version will be removed soon?
    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
    }

    public boolean isAllowedAsPrimaryDataSource()
    {
        return _allowedAsPrimaryDataSource;
    }

    public MicrosoftSqlServer2008R2Dialect getDialect()
    {
        return _dialectFactory.get();
    }

    static @NotNull MicrosoftSqlServerVersion get(final int version, boolean primaryDataSource)
    {
        Optional<MicrosoftSqlServerVersion> optional = Arrays.stream(values())
            .sorted(Comparator.reverseOrder())
            .filter(v -> version >= v._version)
            .findFirst();

        MicrosoftSqlServerVersion ssv = optional.orElseThrow(); // At least SQL_SERVER_UNSUPPORTED should match

        return primaryDataSource && !ssv.isAllowedAsPrimaryDataSource() ? SQL_SERVER_UNSUPPORTED : ssv;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(100, false, SQL_SERVER_2008);
            test(110, false, SQL_SERVER_2012);
            test(120, true, SQL_SERVER_2014);
            test(120, false, SQL_SERVER_2014);
            test(130, true, SQL_SERVER_2016);
            test(130, false, SQL_SERVER_2016);
            test(140, true, SQL_SERVER_2017);
            test(140, false, SQL_SERVER_2017);
            test(150, true, SQL_SERVER_2019);
            test(150, false, SQL_SERVER_2019);
            test(160, true, SQL_SERVER_2022);
            test(160, false, SQL_SERVER_2022);

            // Future
            test(170, true, SQL_SERVER_FUTURE);
            test(170, false, SQL_SERVER_FUTURE);
            test(180, true, SQL_SERVER_FUTURE);
            test(180, false, SQL_SERVER_FUTURE);

            // Bad
            test(80, true, SQL_SERVER_UNSUPPORTED);
            test(80, false, SQL_SERVER_UNSUPPORTED);
            test(85, true, SQL_SERVER_UNSUPPORTED);
            test(85, false, SQL_SERVER_UNSUPPORTED);
            test(90, true, SQL_SERVER_UNSUPPORTED);
            test(90, false, SQL_SERVER_UNSUPPORTED);
            test(95, true, SQL_SERVER_UNSUPPORTED);
            test(95, false, SQL_SERVER_UNSUPPORTED);
            test(99, true, SQL_SERVER_UNSUPPORTED);
            test(99, false, SQL_SERVER_UNSUPPORTED);
        }

        private void test(int version, boolean primary, MicrosoftSqlServerVersion expectedVersion)
        {
            Assert.assertEquals(get(version, primary), expectedVersion);
        }
    }
}
