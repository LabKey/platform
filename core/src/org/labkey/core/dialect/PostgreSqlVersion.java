package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.PostgreSqlServerType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Enum that specifies the versions of PostgreSQL that LabKey supports plus their properties
 */
public enum PostgreSqlVersion
{
    POSTGRESQL_UNSUPPORTED(-1, true, false, null),
    POSTGRESQL_96(96, true, true, PostgreSql96Dialect::new),
    POSTGRESQL_10(100, false, true, PostgreSql_10_Dialect::new),
    POSTGRESQL_11(110, false, true, PostgreSql_11_Dialect::new),
    POSTGRESQL_12(120, false, true, PostgreSql_12_Dialect::new),
    POSTGRESQL_13(130, false, true, PostgreSql_13_Dialect::new),
    POSTGRESQL_14(140, false, false, PostgreSql_14_Dialect::new),
    POSTGRESQL_FUTURE(Integer.MAX_VALUE, true, false, PostgreSql_14_Dialect::new);

    private final int _version;
    private final boolean _deprecated;
    private final boolean _tested;
    private final Supplier<? extends PostgreSql96Dialect> _dialectFactory;

    PostgreSqlVersion(int version, boolean deprecated, boolean tested, Supplier<? extends PostgreSql96Dialect> dialectFactory)
    {
        _version = version;
        _deprecated = deprecated;
        _tested = tested;
        _dialectFactory = dialectFactory;
    }

    // Should LabKey warn administrators that support for this PostgreSQL version will be removed soon?
    public boolean isDeprecated()
    {
        return _deprecated;
    }

    public boolean isTested()
    {
        return _tested;
    }

    public PostgreSql96Dialect getDialect()
    {
        return _dialectFactory.get();
    }

    private final static Map<Integer, PostgreSqlVersion> VERSION_MAP = Arrays.stream(values())
        .collect(Collectors.toMap(jv->jv._version, jv->jv));

    private final static int MAX_KNOWN_VERSION = Arrays.stream(values())
        .filter(v->POSTGRESQL_FUTURE != v)
        .map(v->v._version)
        .max(Comparator.naturalOrder())
        .orElseThrow();

    static @NotNull PostgreSqlVersion get(int version, PostgreSqlServerType type)
    {
        return get(PostgreSqlServerType.LabKey == type ? MAX_KNOWN_VERSION : version);
    }

    static @NotNull PostgreSqlVersion get(int version)
    {
        version = version >= 100 ? version / 10 * 10 : version;

        if (version > MAX_KNOWN_VERSION)
        {
            return POSTGRESQL_FUTURE;
        }
        else
        {
            PostgreSqlVersion psv = VERSION_MAP.get(version);
            return null != psv ? psv : POSTGRESQL_UNSUPPORTED;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(96, POSTGRESQL_96);
            test(100, POSTGRESQL_10);
            test(110, POSTGRESQL_11);
            test(120, POSTGRESQL_12);
            test(130, POSTGRESQL_13);
            test(140, POSTGRESQL_14);

            // Future
            test(150, POSTGRESQL_FUTURE);
            test(160, POSTGRESQL_FUTURE);
            test(170, POSTGRESQL_FUTURE);

            // Bad
            test(83, POSTGRESQL_UNSUPPORTED);
            test(84, POSTGRESQL_UNSUPPORTED);
            test(85, POSTGRESQL_UNSUPPORTED);
            test(90, POSTGRESQL_UNSUPPORTED);
            test(91, POSTGRESQL_UNSUPPORTED);
            test(92, POSTGRESQL_UNSUPPORTED);
            test(93, POSTGRESQL_UNSUPPORTED);
            test(94, POSTGRESQL_UNSUPPORTED);
            test(95, POSTGRESQL_UNSUPPORTED);
            test(97, POSTGRESQL_UNSUPPORTED);
            test(98, POSTGRESQL_UNSUPPORTED);
        }

        private void test(int version, PostgreSqlVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
