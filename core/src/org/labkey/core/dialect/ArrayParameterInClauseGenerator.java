package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.InClauseGenerator;
import org.labkey.api.data.SQLFragment;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * User: adam
 * Date: 8/19/12
 * Time: 3:33 PM
 */

// TODO: Using JDBC arrays requires a JDBC4 Connection, which the Tomcat 5.5 pool doesn't dole out. At SQLDialect
// initialization time we need to enable this implementation only if the Connections are JDBC4.
public class ArrayParameterInClauseGenerator implements InClauseGenerator
{
    private final DbScope _scope;

    public ArrayParameterInClauseGenerator(DbScope scope)
    {
        _scope = scope;
    }

    @Override
    public boolean appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        if (!arrayCandidate(params))
            return false;

        Array array = null;
        Connection conn = null;

        try
        {
            conn = _scope.getConnection();

            Object[] ints = new Object[params.size()];
            int i = 0;

            for (Object param : params)
            {
                ints[i++] = param;
            }

            array = conn.createArrayOf("int4", ints);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            try
            {
                if (null != conn)
                    conn.close();
            }
            catch (SQLException e)
            {
            }
        }

        sql.append(" = ANY (?)");
        sql.add(array);

        return true;
    }

    private boolean arrayCandidate(Collection<?> params)
    {
        // TODO: Re-enable once we've added JDBC4 detection
        return false;
//        if (params.size() < 100)
//            return false;
//
//        Object firstParam = params.iterator().next();
//
//        if (!candidateType(firstParam))
//            return false;
//
//        Class firstParamClass = firstParam.getClass();
//
//        for (Object param : params)
//            if (param.getClass() != firstParamClass)
//                return false;
//
//        return true;
    }

    private boolean candidateType(Object param)
    {
        return (Integer.class.isAssignableFrom(param.getClass()));
    }
}
