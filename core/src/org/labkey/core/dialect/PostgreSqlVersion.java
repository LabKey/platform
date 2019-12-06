package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.PostgreSql91Dialect;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum PostgreSqlVersion
{
    POSTGRESQL_UNSUPPORTED(-1, () -> {
        throw new IllegalStateException();
    }, true, false),
    POSTGRESQL_9_4(94, PostgreSql94Dialect::new, true, true),
    POSTGRESQL_9_5(95, PostgreSql95Dialect::new, false, true),
    POSTGRESQL_9_6(96, PostgreSql96Dialect::new, false, true),
    POSTGRESQL_10(100, PostgreSql_10_Dialect::new, false, true),
    POSTGRESQL_11(110, PostgreSql_11_Dialect::new, false, true),
    POSTGRESQL_12(120, PostgreSql_12_Dialect::new, false, true),
    POSTGRESQL_FUTURE(Integer.MAX_VALUE, PostgreSql_12_Dialect::new, false, false);

    private final int _version;
    private final Supplier<? extends PostgreSql91Dialect> _factory;
    private final boolean _deprecated;
    private final boolean _tested;

    PostgreSqlVersion(int version, Supplier<? extends PostgreSql91Dialect> factory, boolean deprecated, boolean tested)
    {
        _version = version;
        _factory = factory;
        _deprecated = deprecated;
        _tested = tested;
    }

    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
    }

    public PostgreSql91Dialect getDialect()
    {
        return _factory.get();
    }

    private final static Map<Integer, PostgreSqlVersion> VERSION_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(pv->pv._version, pv->pv));

    private final static int MAX_KNOWN_VERSION = Arrays.stream(values())
        .filter(v->POSTGRESQL_FUTURE != v)
        .map(v->v._version)
        .max(Comparator.naturalOrder())
        .orElseThrow();

    static @NotNull PostgreSqlVersion get(int version)
    {
        // Starting with 10.0, PostgreSQL version format changed from x.y.z to x.y, making that last digit a minor version.
        // We don't care about the minor version, so round to the nearest 10.
        if (version >= 100)
            version = version / 10 * 10;

        if (version > MAX_KNOWN_VERSION)
        {
            return POSTGRESQL_FUTURE;
        }
        else
        {
            PostgreSqlVersion pv = VERSION_MAP.get(version);
            return null != pv ? pv : POSTGRESQL_UNSUPPORTED;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            test(80, POSTGRESQL_UNSUPPORTED);
            test(82, POSTGRESQL_UNSUPPORTED);
            test(85, POSTGRESQL_UNSUPPORTED);
            test(89, POSTGRESQL_UNSUPPORTED);
            test(90, POSTGRESQL_UNSUPPORTED);
            test(91, POSTGRESQL_UNSUPPORTED);
            test(92, POSTGRESQL_UNSUPPORTED);
            test(93, POSTGRESQL_UNSUPPORTED);
            test(94, POSTGRESQL_9_4);
            test(95, POSTGRESQL_9_5);
            test(96, POSTGRESQL_9_6);
            test(97, POSTGRESQL_UNSUPPORTED);  // 9.7 never existed
            test(98, POSTGRESQL_UNSUPPORTED);  // 9.8 never existed
            test(99, POSTGRESQL_UNSUPPORTED);  // 9.9 never existed
            test(100, POSTGRESQL_10);
            test(101, POSTGRESQL_10);
            test(109, POSTGRESQL_10);
            test(110, POSTGRESQL_11);
            test(111, POSTGRESQL_11);
            test(115, POSTGRESQL_11);
            test(120, POSTGRESQL_12);
            test(121, POSTGRESQL_12);
            test(125, POSTGRESQL_12);
            test(130, POSTGRESQL_FUTURE);
            test(131, POSTGRESQL_FUTURE);
            test(135, POSTGRESQL_FUTURE);
            test(140, POSTGRESQL_FUTURE);
            test(150, POSTGRESQL_FUTURE);
        }

        private void test(int version, PostgreSqlVersion expectedPostgreSqlVersion)
        {
            Assert.assertEquals(get(version), expectedPostgreSqlVersion);
        }
    }
}
