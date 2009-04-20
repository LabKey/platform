package org.labkey.study.writer;

import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.TSVGridWriter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 3:10:37 PM
 */
public class DataSetWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx) throws SQLException, FileNotFoundException, UnsupportedEncodingException, ServletException
    {
        DataSetDefinition[] datasets = study.getDataSets();

        for (DataSetDefinition def : datasets)
        {
            TableInfo ti = def.getTableInfo(ctx.getUser());
            ResultSet rs = Table.select(ti, ti.getColumns(), null, null);
            TSVGridWriter tsvWriter = new TSVGridWriter(rs);
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            PrintWriter out = ctx.getPrintWriter(def.getFileName());
            tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
        }
    }
}
