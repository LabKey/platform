package org.labkey.api.study.assay;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.URLHelper;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jan 3, 2008
 */
public abstract class AbstractAssayTsvDataHandler extends AbstractExperimentDataHandler
{
    protected static final Object ERROR_VALUE = new Object();

    protected abstract boolean allowEmptyData(); 

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        boolean transaction = false;
        try
        {
            ExpProtocolApplication sourceApplication = data.getSourceApplication();
            if (sourceApplication == null)
            {
                throw new ExperimentException("Cannot import a TSV without knowing its assay definition");
            }
            ExpRun run = sourceApplication.getRun();
            ExpProtocol protocol = run.getProtocol();

            Container container = data.getContainer();
            Integer id = OntologyManager.ensureObject(container.getId(), data.getLSID());
            AssayProvider provider = AssayService.get().getProvider(protocol);
            PropertyDescriptor[] columns = provider.getRunDataColumns(protocol);


            List<PropertyDescriptor> allProps = new ArrayList<PropertyDescriptor>();
            allProps.addAll(Arrays.asList(provider.getUploadSetColumns(protocol)));
            allProps.addAll(Arrays.asList(provider.getRunPropertyColumns(protocol)));

            Map<String, Object> runProps = OntologyManager.getProperties(info.getContainer().getId(), run.getLSID());
            ParticipantVisitResolver resolver = null;

            Container targetContainer = null;
            for (PropertyDescriptor runProp : allProps)
            {
                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equalsIgnoreCase(runProp.getName()))
                {
                    Object targetObject = runProps.get(runProp.getPropertyURI());
                    if (targetObject instanceof String)
                    {
                        targetContainer = ContainerManager.getForId((String)targetObject);
                        break;
                    }
                }
            }

            for (PropertyDescriptor runProp : allProps)
            {
                if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(runProp.getName()))
                {
                    Object targetObject = runProps.get(runProp.getPropertyURI());
                    if (targetObject instanceof String)
                    {
                        ParticipantVisitResolverType resolverType = AbstractAssayProvider.findType((String)targetObject, provider.getParticipantVisitResolverTypes());
                        resolver = resolverType.createResolver(ExperimentService.get().getExpRun(run.getRowId()), targetContainer, info.getUser());
                        break;
                    }
                }
            }

            if (resolver == null)
            {
                resolver = new StudyParticipantVisitResolver(container, targetContainer);
            }

            Map<String, Object>[] rawData = loadFileData(columns, dataFile, resolver);

            if (rawData.length == 0)
            {
                if (allowEmptyData())
                {
                    return;
                }
                else
                {
                    throw new ExperimentException("Data file contained zero data rows");
                }
            }

            checkData(columns, rawData, resolver);
            Map<String, String> propertyNameToURIMap = new HashMap<String, String>();
            for (PropertyDescriptor pd : columns)
                propertyNameToURIMap.put(pd.getName().toLowerCase(), pd.getPropertyURI());
            Map<String, Object>[] fileData = convertPropertyNamesToURIs(rawData, propertyNameToURIMap);

            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }

            OntologyManager.insertTabDelimited(container, id,
                    new SimpleAssayDataImportHelper(data.getLSID()), columns, fileData, false);

            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        finally
        {
            if (transaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
    }

    protected abstract Map<String, Object>[] loadFileData(PropertyDescriptor[] columns, File dataFile, ParticipantVisitResolver resolver)  throws IOException, ExperimentException;

    private void checkColumns(PropertyDescriptor[] expected, Set<String> actual, List<String> missing, List<String> unexpected, Map<String, Object>[] rawData, boolean strict)
    {
        Set<String> checkSet = new HashSet<String>();
        for (PropertyDescriptor pd : expected)
            checkSet.add(pd.getName().toLowerCase());
        for (String col : actual)
        {
            if (!checkSet.contains(col.toLowerCase()))
                unexpected.add(col);
        }
        checkSet.clear();
        if (!strict)
        {
            if (unexpected.size() > 0)
                filterColumns(expected, actual, rawData);
            unexpected.clear();
        }

        for (String col : actual)
            checkSet.add(col.toLowerCase());

        for (PropertyDescriptor pd : expected)
        {
            if ((pd.isRequired() || strict) && !checkSet.contains(pd.getName().toLowerCase()))
                missing.add(pd.getName());
        }
    }

    private void filterColumns(PropertyDescriptor[] expected, Set<String> actual, Map<String, Object>[] rawData)
    {
        List<String> presentAndExpectedKeys = new ArrayList<String>();
        for (PropertyDescriptor col : expected)
        {
            for (String key : actual)
            {
                if (key.equalsIgnoreCase(col.getName()))
                {
                    presentAndExpectedKeys.add(key);
                }
            }
        }
        for (int i = 0; i < rawData.length; i++)
        {
            Map<String, Object> filteredMap = new HashMap<String, Object>();
            for (String key : presentAndExpectedKeys)
            {
                filteredMap.put(key, rawData[i].get(key));
            }
            rawData[i] = filteredMap;
        }
    }
    
    private void checkData(PropertyDescriptor[] columns, Map<String, Object>[] rawData, ParticipantVisitResolver resolver) throws IOException, ExperimentException
    {
        List<String> missing = new ArrayList<String>();
        List<String> unexpected = new ArrayList<String>();

        Set<String> columnNames = rawData[0].keySet();
        // For now, we'll only enforce that required columns are present.  In the future, we'd like to
        // do a strict check first, and then present ignorable warnings.
        checkColumns(columns, columnNames, missing, unexpected, rawData, false);
        if (!missing.isEmpty() || !unexpected.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            if (!missing.isEmpty())
            {
                builder.append("Expected columns were not found: ");
                for (java.util.Iterator<String> it = missing.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                    else
                        builder.append(".  ");
                }
            }
            if (!unexpected.isEmpty())
            {
                builder.append("Unexpected columns were found: ");
                for (java.util.Iterator<String> it = unexpected.iterator(); it.hasNext();)
                {
                    builder.append(it.next());
                    if (it.hasNext())
                        builder.append(", ");
                }
            }
            throw new ExperimentException(builder.toString());
        }

        PropertyDescriptor participantPD = null;
        PropertyDescriptor specimenPD = null;
        PropertyDescriptor visitPD = null;
        PropertyDescriptor datePD = null;

        for (PropertyDescriptor pd : columns)
        {
            if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) && pd.getPropertyType() == PropertyType.STRING)
            {
                participantPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) && pd.getPropertyType() == PropertyType.STRING)
            {
                specimenPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.VISITID_PROPERTY_NAME) && pd.getPropertyType() == PropertyType.DOUBLE)
            {
                visitPD = pd;
            }
            else if (pd.getName().equalsIgnoreCase(AbstractAssayProvider.DATE_PROPERTY_NAME) && pd.getPropertyType() == PropertyType.DATE_TIME)
            {
                datePD = pd;
            }
        }

        Set<String> missingValues = new HashSet<String>();
        Set<String> wrongTypes = new HashSet<String>();

        StringBuilder errorSB = new StringBuilder();

        for (int i = 0; i < rawData.length; i++)
        {
            Map<String, Object> map = new CaseInsensitiveHashMap<Object>(rawData[i]);
            String participantID = null;
            String specimenID = null;
            Double visitID = null;
            Date date = null;
            for (PropertyDescriptor pd : columns)
            {
                Object o = map.get(pd.getName());
                if (participantPD == pd)
                {
                    participantID = o instanceof String ? (String)o : null;
                }
                else if (specimenPD == pd)
                {
                    specimenID = o instanceof String ? (String)o : null;
                }
                else if (visitPD == pd && o != null)
                {
                    visitID = o instanceof Number ? ((Number)o).doubleValue() : null;
                }
                else if (datePD == pd & o != null)
                {
                    date = o instanceof Date ? (Date) o : null;
                }
                String value = o == null ? null : o.toString();
                boolean valueMissing = (value == null || value.length() == 0);
                if (pd.isRequired() && valueMissing && !missingValues.contains(pd.getName()))
                {
                    missingValues.add(pd.getName());
                    errorSB.append(pd.getName()).append(" is required. ");
                }
                else if (!valueMissing && o == ERROR_VALUE && !wrongTypes.contains(pd.getName()))
                {
                    wrongTypes.add(pd.getName());
                    errorSB.append(pd.getName()).append(" must be of type ").append(ColumnInfo.getFriendlyTypeName(pd.getPropertyType().getJavaType())).append(". ");
                }
            }
            ParticipantVisit participantVisit = resolver.resolve(specimenID, participantID, visitID, date);
            if (participantPD != null)
            {
                map.put(participantPD.getName(), participantVisit.getParticipantID());
                rawData[i] = map;
            }
            if (visitPD != null)
            {
                map.put(visitPD.getName(), participantVisit.getVisitID());
                rawData[i] = map;
            }
            if (datePD != null)
            {
                map.put(datePD.getName(), participantVisit.getDate());
                rawData[i] = map;
            }
        }
        if (errorSB.length() != 0)
        {
            throw new ExperimentException("There are errors in the uploaded data: " + errorSB.toString());
        }
    }

    private Map<String, Object>[] convertPropertyNamesToURIs(Map<String, Object>[] dataMaps, Map<String, String> propertyNamesToUris)
    {
        Map<String, Object>[] ret = new Map[dataMaps.length];
        for (int i = 0; i < dataMaps.length; i++)
        {
            ret[i] = new CaseInsensitiveHashMap<Object>(dataMaps[i].size());
            for (Map.Entry<String,Object> entry : dataMaps[i].entrySet())
            {
                String uri = propertyNamesToUris.get(entry.getKey().toLowerCase());
                if (uri == null)
                    throw new RuntimeException("Expected uri for datamap property '" + entry.getKey() + "'.");
                ret[i].put(uri, entry.getValue());
            }
        }
        return ret;
    }

    public void deleteData(ExpData data, Container container, User user) throws ExperimentException
    {
        try
        {
            OntologyManager.deleteOntologyObject(container.getId(), data.getLSID());
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
    }

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }

    public URLHelper getContentURL(Container container, ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            return AssayService.get().getAssayDataURL(container, protocol, run.getRowId());
        }
        return null;
    }
}
