package org.labkey.experiment.api.property;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.dialect.PostgreSqlService;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.util.StringUtilsLabKey;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Truncates names based on dialect-specific rules and guarantees case-insensitive uniqueness among all claimed and
 * generated names. Similar to AliasManager except it doesn't attempt to "sanitize" any characters. Callers are
 * responsible for quoting these names if necessary.
 */
public class StorageNameGenerator
{
    // Leave room for MV suffix in case the column is changed to MV later and leave room for uniquifying suffix
    private static final int COLUMN_RESERVED_LENGTH = OntologyManager.MV_INDICATOR_SUFFIX.length() + 1 + 3;
    // Reserve three characters for "_pk"
    private static final int TABLE_RESERVED_LENGTH = 3;

    private final SqlDialect _dialect;
    private final Set<String> _names = new CaseInsensitiveHashSet();

    public StorageNameGenerator(@NotNull SqlDialect dialect)
    {
        // GitHub Issue 869: Create SQL Server storage names using PostgreSQL's rules to ensure all tables and columns can migrate
        _dialect = dialect.isSqlServer() ? PostgreSqlService.get().getDialect() : dialect;
    }

    public String claimName(String name)
    {
        // Consider: Warn/error on duplicates? Note that provisioned domains currently register "RowId" and "Label" repeatedly.
        _names.add(name);
        return name;
    }

    /**
     * Generate a table storage name based on the provided candidate name, reserving room for the "_pk" suffix.
     */
    public String generateTableName(String candidateName)
    {
        // TODO: Switch to calling generateName(), but that requires downstream work to make the name "legal"
        String tableName = _dialect.makeLegalName(candidateName.toLowerCase(), true, TABLE_RESERVED_LENGTH);
        tableName = tableName.replaceAll("_+", "_");
        return tableName;
    }

    /**
     * Generate a column storage name based on the provided candidate name, reserving room for MV and uniquifying suffixes.
     */
    public String generateColumnName(String candidateName)
    {
        return generateName(candidateName, COLUMN_RESERVED_LENGTH);
    }

    /**
     * Generate a storage name based on the provided candidate name. If needed, the name is truncated using
     * dialect-specific rules and uniquified with an incrementing suffix (1, 2, 3, ...).
     */
    private String generateName(String candidateName, int reserved)
    {
        String legalName = _dialect.truncate(candidateName, reserved);
        String ret = legalName;

        for (int i = 1; _names.contains(ret); i++)
        {
            ret = legalName + i;
        }

        if (isIdentifierTooLong(ret))
            throw new IllegalStateException("generateName() produced a name that was too long for " + _dialect.getProductName() + ": \"" + ret + "\" was generated from \"" + candidateName + "\"");

        if (ret.length() > 255)
            throw new IllegalStateException("generateName() produced a name that was > 255 characters: \"" + ret + "\" was generated from \"" + candidateName + "\"");

        return claimName(ret);
    }

    private boolean isIdentifierTooLong(String candidate)
    {
        return _dialect.isIdentifierTooLong(candidate);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGenerateName()
        {
            SqlDialect dialect = DbScope.getLabKeyScope().getSqlDialect();

            // Test progressively larger substrings of a single, large, randomly generated string
            List<String> longRandomCharacters = StringUtilsLabKey.generateSpecialCharacterList(255);
            testCandidates(255, dialect, i -> longRandomCharacters.stream().limit(i).collect(Collectors.joining()));

            // Test progressively larger randomly generated strings
            testCandidates(255, dialect, StringUtilsLabKey::generateSpecialCharacterString);

            // Test that the same string over and over again generates a unique name
            testCandidates(255, dialect, _ -> "kumquat");
            // Same, but test case sensitivity
            testCandidates(255, dialect, i -> i % 2 == 0 ? "kumquat" : "KUMQUAT");
            String randomString = StringUtilsLabKey.generateSpecialCharacterString(255);
            testCandidates(255, dialect, _ -> randomString);
        }

        private void testCandidates(int count, SqlDialect dialect, Function<Integer, String> candidateSupplier)
        {
            StorageNameGenerator generator = new StorageNameGenerator(dialect);
            Set<String> uniqueNames = new CaseInsensitiveHashSet();

            for (int i = 1; i < count; i++)
            {
                String candidate = candidateSupplier.apply(i);
                String generated = generator.generateColumnName(candidate);
                boolean exists = uniqueNames.contains(candidate);

                if (exists || generator.isIdentifierTooLong(candidate + StringUtils.repeat("x", COLUMN_RESERVED_LENGTH)))
                    assertNotEquals(candidate, generated);
                else
                    assertEquals(candidate, generated);

                // No matter what, the generated name must be unique
                assertTrue(uniqueNames.add(generated));
            }
        }
    }
}
