package org.labkey.bigiron.oracle;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Enum that specifies the versions of Oracle that LabKey supports plus their properties
 */
public enum OracleVersion
{
    /*
        https://en.wikipedia.org/wiki/Oracle_Database provides some info about Oracle version numbers
     */

    // LabKey supports 10g and higher
    ORACLE_UNSUPPORTED(Integer.MIN_VALUE, null),
    // Piggyback on Oracle11gDialect until incompatibilities are discovered
    ORACLE_10g(100, Oracle11gR1Dialect::new),
    ORACLE_11gR1(110, Oracle11gR1Dialect::new),
    ORACLE_11gR2(112, Oracle11gR2Dialect::new),
    ORACLE_12c(120, Oracle12cDialect::new),
    ORACLE_18c(180, Oracle18cDialect::new),
    ORACLE_19c(190, Oracle19cDialect::new),
    ORACLE_21c(210, Oracle21cDialect::new),
    ORACLE_23c(230, Oracle23cDialect::new),
    ORACLE_FUTURE(240, Oracle23cDialect::new);

    private final int _version;
    private final Supplier<? extends OracleDialect> _dialectFactory;

    OracleVersion(int version, Supplier<? extends OracleDialect> dialectFactory)
    {
        _version = version;
        _dialectFactory = dialectFactory;
    }

    public OracleDialect getDialect()
    {
        return _dialectFactory.get();
    }

    static @NotNull OracleVersion get(final int version)
    {
        Optional<OracleVersion> optional = Arrays.stream(values())
            .sorted(Comparator.reverseOrder())
            .filter(v -> version >= v._version)
            .findFirst();

        return optional.orElseThrow();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            // Good
            test(100, ORACLE_10g);
            test(101, ORACLE_10g);
            test(102, ORACLE_10g);
            test(110, ORACLE_11gR1);
            test(111, ORACLE_11gR1);
            test(112, ORACLE_11gR2);
            test(113, ORACLE_11gR2);
            test(120, ORACLE_12c);
            test(121, ORACLE_12c);
            test(122, ORACLE_12c);
            test(180, ORACLE_18c);
            test(181, ORACLE_18c);
            test(182, ORACLE_18c);
            test(190, ORACLE_19c);
            test(191, ORACLE_19c);
            test(192, ORACLE_19c);
            test(210, ORACLE_21c);
            test(211, ORACLE_21c);
            test(212, ORACLE_21c);
            test(230, ORACLE_23c);
            test(231, ORACLE_23c);
            test(232, ORACLE_23c);

            // Future
            test(240, ORACLE_FUTURE);
            test(245, ORACLE_FUTURE);
            test(250, ORACLE_FUTURE);
            test(260, ORACLE_FUTURE);

            // Bad
            test(80, ORACLE_UNSUPPORTED);
            test(80, ORACLE_UNSUPPORTED);
            test(85, ORACLE_UNSUPPORTED);
            test(85, ORACLE_UNSUPPORTED);
            test(90, ORACLE_UNSUPPORTED);
            test(90, ORACLE_UNSUPPORTED);
            test(95, ORACLE_UNSUPPORTED);
            test(95, ORACLE_UNSUPPORTED);
            test(99, ORACLE_UNSUPPORTED);
            test(99, ORACLE_UNSUPPORTED);
        }

        private void test(int version, OracleVersion expectedVersion)
        {
            Assert.assertEquals(get(version), expectedVersion);
        }
    }
}
