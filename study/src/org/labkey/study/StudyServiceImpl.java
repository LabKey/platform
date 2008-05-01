package org.labkey.study;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 */
public class StudyServiceImpl implements StudyService.Service
{
    public int getDatasetId(Container c, String datasetName)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetName);
        if (def == null)
            return -1;
        return def.getDataSetId();
    }

    public String updateDatasetRow(Container c, int datasetId, String lsid, Map<String, Object> data, List<String> errors)
            throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        String tsv = createTSV(data);
        // Start a transaction, so that we can rollback if our insert fails
        DbScope scope =  StudySchema.getInstance().getSchema().getScope();
        boolean startedTransaction = false;
        if (!scope.isTransactionActive())
        {
            scope.beginTransaction();
            startedTransaction = true;
        }
        try
        {
            deleteDatasetRow(c, datasetId, lsid);
            // TODO: switch from using a TSV to a map, so that strange characters like quotes don't throw off the data
            String[] result = StudyManager.getInstance().importDatasetTSV(study,def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (errors.size() > 0)
            {
                // Update failed
                return null;
            }
            // Successfully updated
            if (startedTransaction)
                scope.commitTransaction();
            return result[0];
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
        finally
        {
            if (startedTransaction && scope.isTransactionActive())
                scope.rollbackTransaction();
        }
    }

    public String insertDatasetRow(Container c, int datasetId, Map<String, Object> data, List<String> errors) throws SQLException
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        String tsv = createTSV(data);
        try
        {
            // TODO: switch from using a TSV to a map, so that strange characters like quotes don't throw off the data
            String[] result = StudyManager.getInstance().importDatasetTSV(study,def, tsv, System.currentTimeMillis(),
                Collections.<String,String>emptyMap(), errors, true);

            if (result.length > 0)
                return result[0];

            // Update failed
            return null;
        }
        catch (IOException ioe)
        {
            throw UnexpectedException.wrap(ioe);
        }
        catch (ServletException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    public void deleteDatasetRow(Container c, int datasetId, String lsid)
    {
        Study study = StudyManager.getInstance().getStudy(c);
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        StudyManager.getInstance().deleteDatasetRows(study, def, Collections.singletonList(lsid));
    }

    private String createTSV(Map<String,Object> data)
    {
        StringBuilder sb = new StringBuilder();

        // Need to hold the keys in an array list to preserve order
        List<String> keyList = new ArrayList<String>();
        for (Map.Entry<String,Object> entry : data.entrySet())
        {
            keyList.add(entry.getKey());
            sb.append(entry.getKey()).append('\t');
        }
        sb.append(System.getProperty("line.separator"));

        for (String key:keyList)
        {
            sb.append(data.get(key)).append('\t');
        }
        return sb.toString();
    }
}
