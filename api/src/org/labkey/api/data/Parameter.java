package org.labkey.api.data;

import org.apache.struts.upload.FormFile;
import org.labkey.api.attachments.AttachmentFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.GregorianCalendar;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 13, 2007
 * Time: 9:01:58 AM
 *
 * parameter bound to a specific sql type, usually this is not necessary as
 * type can be inferred.  However, this can be useful for NULL parameters and
 * for distiguishing unicode, non-unicode types
 *
 * NOTE: jdbc does not have separate Type values for varchar nvarchar
 * NOTE: does not do implicit conversion, just sets the parameter type
 */

public class Parameter
{
    int _sqlType = Types.JAVA_OBJECT;
    String _charset = null; // null for unicode
    Object _value = null;
    SqlDialect _dialect = null;

    public Parameter(Object value)
    {
        _value = value;
    }

    public Parameter(Object value, int sqlType)
    {
        _value = value;
        _sqlType = sqlType;
    }


    /**
     * For the case of SQL Server VARCHAR (as opposed to NVARCHAR) useUnicode=false
     * will convert the parameter (NVARCHAR parameter -> VARCHAR) so the database won't do the reverse
     * conversion (VARCHAR column -> NVARCHAR).
     *
     * @param value
     * @param useUnicode
     */
    public Parameter(Object value, boolean useUnicode)
    {
        _value = value;
        _sqlType = Types.VARCHAR;
        _charset = useUnicode ? null : "ISO-8859-1";
    }

    public static Parameter nullParameter(int sqlType)
    {
        return new Parameter(null, sqlType);
    }

    public void bind(PreparedStatement stmt, int index) throws SQLException
    {
        _bind(stmt, index, _value, _sqlType, _charset, _dialect);
    }

    public static void bindObject(PreparedStatement stmt, int index, Object value) throws SQLException
    {
        if (value instanceof Parameter)
            ((Parameter)value).bind(stmt, index);
        else
            _bind(stmt, index, value, Types.JAVA_OBJECT, null, null);
    }

    private static void _bind(PreparedStatement stmt, int index, Object value, int sqlType, String charset, SqlDialect dialect)
            throws SQLException

    {
        if (null == value)
        {
            stmt.setNull(index, sqlType == Types.JAVA_OBJECT ? Types.VARCHAR : sqlType);
            return;
        }

        // Use AttachmentFile instead
        assert !(value instanceof FormFile || value instanceof File || value instanceof MultipartFile);

        if (value instanceof AttachmentFile)
        {
            try
            {
                InputStream is = ((AttachmentFile) value).openInputStream();
                long len = ((AttachmentFile) value).getSize();

                if (len > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
                stmt.setBinaryStream(index, is, (int)len);
                return;
            }
            catch (Exception x)
            {
                SQLException sqlx = new SQLException();
                sqlx.initCause(x);
                throw sqlx;
            }
        }
        

        if (value instanceof java.util.Date)
        {
            if (!(value instanceof java.sql.Date) && !(value instanceof java.sql.Time) && !(value instanceof java.sql.Timestamp))
                value = new java.sql.Timestamp(((java.util.Date) value).getTime());
        }
        else if (value instanceof GregorianCalendar)
            value = new java.sql.Timestamp(((java.util.GregorianCalendar) value).getTimeInMillis());
        else if (value.getClass() == java.lang.Character.class || value instanceof CharSequence)
            value = value.toString();
        else if (value.getClass() == Container.class)
            value = ((Container) value).getId();
        else if (value instanceof Enum)
            value = ((Enum)value).name();

        if (sqlType == Types.VARCHAR && null != charset)
        {
            if (dialect == null)
            {
                Connection conn = stmt.getConnection();
                if (conn instanceof ConnectionWrapper)
                    dialect = ((ConnectionWrapper)conn).getDialect();
            }

            if (dialect instanceof SqlDialectMicrosoftSQLServer)
            {
                try
                {
                    value = String.valueOf(value).getBytes(charset);
                    sqlType = Types.VARBINARY;
                }
                catch (UnsupportedEncodingException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        if (sqlType == Types.JAVA_OBJECT)
            stmt.setObject(index, value);
        else
            stmt.setObject(index, value, sqlType);
    }


    public String toString()
    {
        return String.valueOf(_value);
    }
}
