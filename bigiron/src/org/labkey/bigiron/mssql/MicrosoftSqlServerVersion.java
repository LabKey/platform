package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum MicrosoftSqlServerVersion
{
    // Good resources for past & current SQL Server version numbers:
    // - http://www.sqlteam.com/article/sql-server-versions
    // - http://sqlserverbuilds.blogspot.se/

    // Currently, we support 2012 and higher as the primary data source, plus 2008/2008R2 as an external data source only

    MICROSOFT_SQL_SERVER_UNSUPPORTED(-1, () -> {
        throw new IllegalStateException();
    }, true, false, false),
    MICROSOFT_SQL_SERVER_2008(100, MicrosoftSqlServer2008R2Dialect::new, true, true, false),
    MICROSOFT_SQL_SERVER_2008R2(105, MicrosoftSqlServer2008R2Dialect::new, true, true, false),
    MICROSOFT_SQL_SERVER_2012(110, MicrosoftSqlServer2012Dialect::new, false, true, true),
    MICROSOFT_SQL_SERVER_2014(120, MicrosoftSqlServer2014Dialect::new, false, true, true),
    MICROSOFT_SQL_SERVER_2016(130, MicrosoftSqlServer2016Dialect::new, false, true, true),
    MICROSOFT_SQL_SERVER_2017(140, MicrosoftSqlServer2017Dialect::new, false, true, true),
    MICROSOFT_SQL_SERVER_2019(150, MicrosoftSqlServer2019Dialect::new, false, true, true),
    MICROSOFT_SQL_SERVER_FUTURE(Integer.MAX_VALUE, MicrosoftSqlServer2019Dialect::new, false, false, true);

    private final int _version;
    private final Supplier<? extends BaseMicrosoftSqlServerDialect> _factory;
    private final boolean _deprecated;
    private final boolean _tested;
    private final boolean _primary;

    MicrosoftSqlServerVersion(int version, Supplier<? extends BaseMicrosoftSqlServerDialect> factory, boolean deprecated, boolean tested, boolean primary)
    {
        _version = version;
        _factory = factory;
        _deprecated = deprecated;
        _tested = tested;
        _primary = primary;
    }

    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
    }

    public boolean isPrimaryAllowed()
    {
        return _primary;
    }

    public BaseMicrosoftSqlServerDialect getDialect()
    {
        return _factory.get();
    }

    private final static Map<Integer, MicrosoftSqlServerVersion> VERSION_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(mssv->mssv._version, mssv->mssv));

    private final static int MAX_KNOWN_VERSION = Arrays.stream(values())
        .filter(v->MICROSOFT_SQL_SERVER_FUTURE != v)
        .map(v->v._version)
        .max(Comparator.naturalOrder())
        .orElseThrow();

    static @NotNull MicrosoftSqlServerVersion get(int version)
    {
        if (version > MAX_KNOWN_VERSION)
        {
            return MICROSOFT_SQL_SERVER_FUTURE;
        }
        else
        {
            MicrosoftSqlServerVersion mssv = VERSION_MAP.get(version);
            return null != mssv ? mssv : MICROSOFT_SQL_SERVER_UNSUPPORTED;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(100, MICROSOFT_SQL_SERVER_2008);
            test(105, MICROSOFT_SQL_SERVER_2008R2);
            test(110, MICROSOFT_SQL_SERVER_2012);
            test(120, MICROSOFT_SQL_SERVER_2014);
            test(130, MICROSOFT_SQL_SERVER_2016);
            test(140, MICROSOFT_SQL_SERVER_2017);
            test(150, MICROSOFT_SQL_SERVER_2019);

            // Future
            test(160, MICROSOFT_SQL_SERVER_FUTURE);
            test(170, MICROSOFT_SQL_SERVER_FUTURE);

            // Bad
            test(80, MICROSOFT_SQL_SERVER_UNSUPPORTED);
            test(82, MICROSOFT_SQL_SERVER_UNSUPPORTED);
            test(90, MICROSOFT_SQL_SERVER_UNSUPPORTED);
            test(109, MICROSOFT_SQL_SERVER_UNSUPPORTED);
            test(125, MICROSOFT_SQL_SERVER_UNSUPPORTED);
            test(135, MICROSOFT_SQL_SERVER_UNSUPPORTED);
        }

        private void test(int version, MicrosoftSqlServerVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
