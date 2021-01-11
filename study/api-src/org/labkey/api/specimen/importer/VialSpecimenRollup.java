package org.labkey.api.specimen.importer;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.specimen.importer.Rollup;

import java.util.Arrays;
import java.util.List;

public enum VialSpecimenRollup implements Rollup
{
    VialSpecimenCount
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("Count%", "%Count");
        }

        @Override
        public SQLFragment getRollupSql(String fromColName, String toColName)
        {
            SQLFragment sql = new SQLFragment("SUM(CASE ");
            sql.append(fromColName).append(" WHEN ? THEN 1 ELSE 0 END) AS ").append(toColName);
            sql.add(Boolean.TRUE);
            return sql;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return JdbcType.BOOLEAN.equals(from) && to.isInteger();
        }
    },
    VialSpecimenTotal
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("Total%", "%Total", "SumOf%");
        }

        @Override
        public SQLFragment getRollupSql(String fromColName, String toColName)
        {
            SQLFragment sql = new SQLFragment("SUM(");
            sql.append(fromColName).append(") AS ").append(toColName);
            return sql;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return from.isNumeric() && to.isNumeric();
        }
    },
    VialSpecimenMaximum
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("Max%", "%Max");
        }

        @Override
        public SQLFragment getRollupSql(String fromColName, String toColName)
        {
            SQLFragment sql = new SQLFragment("MAX(");
            sql.append(fromColName).append(") AS ").append(toColName);
            return sql;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return !JdbcType.BOOLEAN.equals(from) &&
                    (from.equals(to) || canPromoteNumeric(from, to));
        }
    },
    VialSpecimenMinimum
    {
        @Override
        public List<String> getPatterns()
        {
            return Arrays.asList("Min%", "%Min");
        }

        @Override
        public SQLFragment getRollupSql(String fromColName, String toColName)
        {
            SQLFragment sql = new SQLFragment("MIN(");
            sql.append(fromColName).append(") AS ").append(toColName);
            return sql;
        }

        @Override
        public boolean isTypeConstraintMet(JdbcType from, JdbcType to)
        {
            return !JdbcType.BOOLEAN.equals(from) &&
                    (from.equals(to) || canPromoteNumeric(from, to));
        }
    };

    // Gets SQL to calculate rollup (used for vial -> specimen rollups)
    public abstract SQLFragment getRollupSql(String fromColName, String toColName);

    @Override
    public boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismtach)
    {
        for (String pattern : getPatterns())
        {
            if (pattern.replace("%", from.getName()).equalsIgnoreCase(to.getName()) && (allowTypeMismtach || isTypeConstraintMet(from.getJdbcType(), to.getJdbcType())))
                return true;
        }
        return false;
    }
}
