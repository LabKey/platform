package org.labkey.api.util;

public class SqlUtil
{
    public static String extractSql(String text)
    {
        if (text.startsWith("SELECT "))
            return text;
        if (text.startsWith("WITH ") && text.contains("SELECT "))
            return text;
        if (text.startsWith("PARAMETERS ") && text.contains("SELECT "))
            return text;
        var sql = text.indexOf("```sql\n");
        if (sql >= 0)
        {
            var end = text.indexOf("```", sql+7);
            if (end >= 0)
                return text.substring(sql+7,end);
        }
        return null;
    }
}
