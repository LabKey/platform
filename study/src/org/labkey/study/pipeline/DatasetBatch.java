/*
 * Copyright (c) 2006-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.pipeline;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.model.QCState;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.common.util.Pair;
import org.labkey.common.util.CPUTimer;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */
public class DatasetBatch extends StudyBatch implements Serializable
{
    static Logger _log = getJobLogger(DatasetBatch.class);

    transient ArrayList<DatasetImportJob> jobs = null;
    boolean hasErrors = false;
//    boolean hasWarnings = false;

    public DatasetBatch(ViewBackgroundInfo info, File definitionFile) throws SQLException
    {
        super(info, definitionFile);
    }

    public DatasetBatch(ViewBackgroundInfo info) throws SQLException
    {
        this(info, null);
    }

    @Override
    public String getDescription()
    {
        String description = "Import datasets";
        if (_definitionFile != null)
            description += ": " + _definitionFile.getName();
        return description;
    }

    void _logError(String s)
    {
        _logError(s, null);
    }

    void _logError(String s, Exception x)
    {
        hasErrors = true;
        error(s,x);
    }

    void _logWarning(String s)
    {
        warn(s);
    }

    void _logInfo(String s)
    {
        info(s);
    }

    public Logger getClassLogger()
    {
        return _log;
    }

    static Pattern defaultPattern = Pattern.compile("^\\D*(\\d*).(?:tsv|txt)$");

    @Override
    public void prepareImport(List<String> errors) throws IOException, SQLException
    {
        File dir = _definitionFile.getParentFile();
        InputStream is = new FileInputStream(_definitionFile);
        Properties props = new Properties();
        props.load(is);

        Study study = getStudy();
        if (null == getStudy())
        {
            errors.add("Study has not been created yet.");
            return;
        }
        DataSetDefinition[] dsArray = getStudyManager().getDataSetDefinitions(getStudy());
        HashMap<String,DataSetDefinition> dsMap = new HashMap<String,DataSetDefinition>(dsArray.length * 3);
        // UNDONE: duplicate labels? dataset named participant?
        for (DataSetDefinition ds : dsArray)
        {
            String label = StringUtils.trimToNull(ds.getLabel());
            if (null != label)
                dsMap.put(label.toLowerCase(), ds);
            dsMap.put(String.valueOf(ds.getDataSetId()), ds);
        }

        // add fake DataSetDefinition for virtual Participant dataset
        DataSetDefinition dsParticipant = new DataSetDefinition(study,-1,"Participant", "Participant",null,"StudyParticipant");
        dsMap.put("participant", dsParticipant);

        IdentityHashMap<DataSetDefinition, DatasetImportJob> jobMap = new IdentityHashMap<DataSetDefinition, DatasetImportJob>();

        //
        // load defaults
        //

        Action defaultAction = Action.REPLACE;
        Pattern filePattern = defaultPattern;
        boolean importAllMatches = true;
        boolean defaultDeleteAfterImport = false;
        OneToOneStringMap defaultColumnMap = new OneToOneStringMap();
        // see also StudyController.handleImportDataset()
        // UNDONE: share code
        defaultColumnMap.put("ptid", DataSetDefinition.getParticipantIdURI());
        defaultColumnMap.put("visit", DataSetDefinition.getSequenceNumURI());
        defaultColumnMap.put("dfcreate", DataSetDefinition.getCreatedURI());     // datafax field name
        defaultColumnMap.put("dfmodify", DataSetDefinition.getModifiedURI());    // datafax field name

        for (Map.Entry e : props.entrySet())
        {
            String key = StringUtils.trimToEmpty((String)e.getKey()).toLowerCase();
            String value = StringUtils.trimToEmpty((String)e.getValue());
            if (!key.startsWith("default."))
                continue;
            if (key.equals("default.action"))
            {
                defaultAction = actionForName(value);
            }
            else if (key.equals("default.filepattern"))
            {
                filePattern = Pattern.compile("^" + value + "$");
            }
            else if (key.equals("default.deleteafterimport"))
            {
                defaultDeleteAfterImport = "true".equals(value.trim().toLowerCase());
            }
            else if (key.equals("default.importallmatches"))
            {
                importAllMatches = "true".equals(value.trim().toLowerCase());                 
            }
            else if (key.startsWith("default.property."))
            {
                String property = key.substring("default.property.".length()).trim();
                String column = value.trim();
                property = toStudyPropertyURI(property);
                defaultColumnMap.put(column, property);
            }
        }

        //
        // load explicit definitions
        //

        for (Map.Entry e : props.entrySet())
        {
            String key = StringUtils.trimToEmpty((String)e.getKey()).toLowerCase();
            String value = StringUtils.trimToEmpty((String)e.getValue());
            int period = key.indexOf('.');
            if (key.startsWith("default.") || -1 == period)
                continue;
            String datasetKey = key.substring(0, period);
            String propertyKey = key.substring(period+1);
            DataSetDefinition ds = dsMap.get(datasetKey);
            if (null == ds)
            {
                errors.add("Could not find dataset for '" + datasetKey + "'");
                continue;
            }
            DatasetImportJob job = jobMap.get(ds);
            if (null == job)
            {
                job = newImportJob(ds, null, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
                jobMap.put(ds, job);
            }
            if (propertyKey.equals("file"))
            {
                job.tsv = new File(dir, value);
            }
            else if (propertyKey.equals("action"))
            {
                job.action = actionForName(value);
            }
            else if (propertyKey.equals("deleteAfterImport"))
            {
                job.deleteAfterImport = "true".equals(value.trim().toLowerCase());
            }
            else if (propertyKey.equals("visitDatePropertyName"))
            {
                job.setVisitDatePropertyName(StringUtils.trimToNull(value));
            }
            else if (propertyKey.startsWith("property."))
            {
                String property = propertyKey.substring("property.".length()).trim();
                String column = value.trim();
                property = toStudyPropertyURI(property);
                job.columnMap.put(column, property);
            }
            else if (propertyKey.equals("sitelookup"))
            {
                if (job instanceof ParticipantImportJob)
                    ((ParticipantImportJob)job).setSiteLookup(value.trim());
            }
        }

        File[] files = dir.listFiles();
        for (File tsv : files)
        {
            String name = tsv.getName();
            if (!tsv.isFile() || tsv.isHidden() || name.startsWith("."))
                continue;
            Matcher m = filePattern.matcher(name);
            if (!m.find())
                continue;
            String dsKey = m.group(1);
            dsKey = normalizeIntegerString(dsKey);
            DataSetDefinition ds = dsMap.get(dsKey);
            if (null == ds)
                continue;
            DatasetImportJob job = jobMap.get(ds);
            if (null == job)
            {
                if (!importAllMatches)
                    continue;
                job = newImportJob(ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
                jobMap.put(ds, job);
            }
            else if (job.tsv == null)
                job.tsv = tsv;
        }

        DatasetImportJob[] a = jobMap.values().toArray(new DatasetImportJob[0]);
        Arrays.sort(a,new Comparator<DatasetImportJob>()
        {
            public int compare(DatasetImportJob j1, DatasetImportJob j2)
            {
                String name1 = j1.getFileName();
                String name2 = j2.getFileName();
                if (name1 != null && name2 != null)
                    return name1.compareTo(name2);
                if (name1 != null || name2 != null)
                    return name1 == null ? -1 : 1;
                return j1.datasetDefinition.getDataSetId() - j2.datasetDefinition.getDataSetId();
            }
        });

        jobs = new ArrayList<DatasetImportJob>();
        jobs.addAll(Arrays.asList(a));
    }


    private DatasetImportJob newImportJob(DataSetDefinition ds, File tsv, Action defaultAction, boolean defaultDeleteAfterImport, OneToOneStringMap defaultColumnMap)
    {
        DatasetImportJob job;
        if (ds.getDataSetId() == -1 && "Participant".equals(ds.getLabel()))
            job = new ParticipantImportJob(ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
        else
            job = new DatasetImportJob(ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
        return job;
    }


    private String normalizeIntegerString(String dsKey)
    {
        try
        {
            return String.valueOf(Integer.parseInt(dsKey));
        } catch (NumberFormatException x)
        {
            return dsKey;
        }
    }


    private String toStudyPropertyURI(String property)
    {
        PropertyDescriptor pd = DataSetDefinition.getStandardPropertiesMap().get(property);
        if (null == pd)
            return property;
        else
            return pd.getPropertyURI();
    }


    private Action actionForName(String value)
    {
        Action defaultAction;
        try
        {
            defaultAction = Action.valueOf(value.toUpperCase());
        }
        catch (IllegalArgumentException x)
        {
            defaultAction = null;
        }
        return defaultAction;
    }


    public List<DatasetImportJob> getJobs()
    {
        if (null == jobs)
            return Collections.emptyList();
        return Collections.unmodifiableList(jobs);
    }


    public void addJob(DatasetImportJob job)
    {
        jobs.add(job);
    }


    @Override
    public void submit() throws IOException
    {
// NOTE: We're not using the locking file marker
//        File lockFile = StudyPipeline.lockForDataset(getStudy(), _definitionFile);
//        lockFile.createNewFile();
        super.submit();
    }

    public void run()
    {
        try
        {
            if (null == jobs)
            {
                List<String> errors = new ArrayList<String>();
                try
                {
                prepareImport(errors);
                for (String error : errors)
                    _logError(error);
                }
                catch (Exception x)
                {
                    _logError("Parse failed: " + _definitionFile.getPath(), x);
                    return;
                }
            }

            _logInfo("Start batch " + (null == _definitionFile ? "" : _definitionFile.getName()));
            for (DatasetImportJob job : jobs)
            {
                String validate = job.validate();
                if (validate != null)
                {
                    setStatus(validate);
                    _logError(validate);
                    continue;
                }
                String statusMsg = "" + job.action + " " + job.datasetDefinition.getLabel();
                if (job.tsv != null)
                    statusMsg += " using file " + job.tsv.getName();
                setStatus(statusMsg);

                try
                {
                    job.run();
                }
                catch (Exception x)
                {
                    _logError("Unexpected error loading " + job.tsv.getName(), x);
                    assert hasErrors;
                }
            }

            _logInfo("Finish batch " + (null == _definitionFile ? "" : _definitionFile.getName()));

            getStudyManager().getVisitManager(getStudy()).updateParticipantVisits(getUser());
            try
            {
                getStudyManager().updateParticipantCohorts(getUser(), getStudy());
            }
            catch (SQLException e)
            {
                // rethrow and catch below for central logging
                throw new RuntimeException(e);
            }

            // materialize datasets only AFTER all other work has been completed; otherwise the background thread
            // materializing datasets will fight with other operations that may try to clear the materialized cache.
            // (updateParticipantVisits does this, for example)
            for (DatasetImportJob job : jobs)
            {
                if (!(job instanceof ParticipantImportJob))
                    job.getDatasetDefinition().materializeInBackground(getUser());
            }
        }
        catch (RuntimeException x)
        {
            _logError("Unexpected error", x);
            assert hasErrors;
            throw x;
        }
        finally
        {
            setStatus(hasErrors ? PipelineJob.ERROR_STATUS : PipelineJob.COMPLETE_STATUS);
            File lock = StudyPipeline.lockForDataset(getStudy(), getDefinitionFile());
            if (lock.exists() && lock.canRead() && lock.canWrite())
                lock.delete();
            _study = null;
        }
    }


    enum Action
    {
        REPLACE,
        APPEND,
        DELETE,
//            MERGE
    }


    /**
     * Key and value are case insensitive on search, but are returned in original case
     */
    public static class OneToOneStringMap extends AbstractMap<String,String>
    {
        CaseInsensitiveHashMap<Pair<String,String>> keyMap = new CaseInsensitiveHashMap<Pair<String,String>>();
        CaseInsensitiveHashMap<Pair<String,String>> valMap = new CaseInsensitiveHashMap<Pair<String,String>>();

        @Override
        public String get(Object key)
        {
            Pair<String,String> p = keyMap.get(key);
            return p == null ? null : p.getValue();
        }


        public String getKey(Object value)
        {
            Pair<String,String> p = valMap.get(value);
            return p == null ? null : p.getKey();
        }


        @Override
        public String put(String key, String value)
        {
            Pair<String,String> p = new Pair<String,String>(key,value);
            String ret = _remove(p).getValue();
            _put(p);
            return ret;
        }

        /*  returns <old mapping for new value, old mapping for new key> */
        private Pair<String,String> _remove(Pair<String,String> p)
        {
            String oldKey = null;
            String oldValue = null;

            Pair<String,String> t = keyMap.get(p.getKey());
            if (null != t)
            {
                oldValue = t.getValue();
                if (oldValue != null)
                    keyMap.remove(oldValue);
            }

            t = valMap.get(p.getValue());
            if (null != t)
            {
                oldKey = t.getKey();
                if (oldKey != null)
                    valMap.remove(oldKey);
            }

            return new Pair<String,String>(oldKey,oldValue);
        }


        private void _put(Pair<String,String> p)
        {
            keyMap.put(p.getKey(), p);
            valMap.put(p.getValue(), p);
        }


        @Override
        public String remove(Object key)
        {
            Pair<String,String> p = keyMap.get(key);
            if (null == p)
                return null;
            return _remove(p).getValue();
        }

        public String removeValue(Object value)
        {
            Pair<String,String> p = valMap.get(value);
            if (null == p)
                return null;
            return _remove(p).getValue();
        }

        @Override
        public boolean containsValue(Object value)
        {
            return valMap.containsKey(value);
        }

        @Override
        public boolean containsKey(Object key)
        {
            return keyMap.containsKey(key);
        }

        public Set<Entry<String, String>> entrySet()
        {
            Set<Entry<String,String>> set = new HashSet<Entry<String,String>>();
            set.addAll(keyMap.values());
            return Collections.unmodifiableSet(set);
        }
    }


    public class DatasetImportJob implements Runnable
    {
        protected Action action = null;
        protected DataSetDefinition datasetDefinition;
        protected File tsv;
        protected boolean deleteAfterImport = false;
        protected Map<String,String> columnMap = new OneToOneStringMap();
        protected String visitDatePropertyName = null;


        DatasetImportJob(DataSetDefinition ds, File tsv, Action action, boolean deleteAfterImport, Map<String,String> columnMap)
        {
            datasetDefinition = ds;
            this.action = action;
            this.deleteAfterImport = deleteAfterImport;
            this.columnMap.putAll(columnMap);
            this.tsv = tsv;
        }

        public String validate()
        {
            List<String> errors = new ArrayList<String>(5);
            validate(errors);
            return errors.isEmpty() ? null : errors.get(0);
        }

        public void validate(List<String> errors)
        {
            if (action == null)
                errors.add("No action specified");
            if (datasetDefinition == null)
                errors.add("Dataset not defined");
            if (datasetDefinition.getTypeURI() == null)
                errors.add("Dataset type is not defined");
            if (action == Action.DELETE)
                return;
            if (null == tsv)
                errors.add("No file specified");
            if (!tsv.exists())
                errors.add("File does not exist: " + tsv.getName());
            if (!tsv.canRead())
                errors.add("Cannot read tsv: " + tsv.getName());
        }


        public void run()
        {
            String name = getDatasetDefinition().getName();
            CPUTimer cpuReadFile = new CPUTimer(name + ": readFile");
            CPUTimer cpuDelete = new CPUTimer(name + ": delete");
            CPUTimer cpuImport = new CPUTimer(name + ": import");
            CPUTimer cpuCommit = new CPUTimer(name + ": commit");

            DbSchema schema  = StudyManager.getSchema();
            DbScope scope = schema.getScope();
            Container c = getInfo().getContainer();
            Study study = getStudyManager().getStudy(c);
            QCState defaultQCState = study.getDefaultPipelineQCState() != null ?
                    StudyManager.getInstance().getQCStateForRowId(c, study.getDefaultPipelineQCState().intValue()) : null;

            List<String> errors = new ArrayList<String>();
            validate(errors);
            if (!errors.isEmpty())
            {
                for (String e : errors)
                    _logError(tsv.getName() + " -- " + e);
                return;
            }


            try
            {
                StringBuilder text = null;

                if (action == Action.APPEND || action == Action.REPLACE)
                {
                    assert cpuReadFile.start();
                    // UNDONE: there's a helper for this somewhere
                    text = new StringBuilder((int)tsv.length());
                    FileReader fr = new FileReader(tsv);
                    BufferedReader reader = new BufferedReader(fr);
                    String s;
                    try
                    {
                        while (null != (s = reader.readLine()))
                            text.append(s).append("\n");
                    }
                    finally
                    {
                        reader.close();
                        fr.close();
                    }
                    assert cpuReadFile.stop();
                }

                scope.beginTransaction();

                if (action == Action.REPLACE || action == Action.DELETE)
                {
                    assert cpuDelete.start();
                    int rows = getStudyManager().purgeDataset(study, datasetDefinition);
                    if (action == Action.DELETE)
                        _logInfo(datasetDefinition.getLabel() + ": Deleted " + rows + " rows");
                    assert cpuDelete.stop();
                }

                if (action == Action.APPEND || action == Action.REPLACE)
                {
                    assert text != null;
                    assert cpuImport.start();

                    String[] imported = getStudyManager().importDatasetTSV(
                            study,
                            getInfo().getUser(),
                            datasetDefinition,
                            text.toString(),
                            tsv.lastModified(),
                            columnMap,
                            errors,
                            false, //Set to TRUE if/when MERGE is implemented
                            defaultQCState);
                    if (errors.size() == 0)
                    {
                        assert cpuCommit.start();
                        scope.commitTransaction();
                        _logInfo(datasetDefinition.getLabel() + ": Successfully imported " + imported.length + " rows from " + tsv);
                        assert cpuCommit.stop();
                        getDatasetDefinition().unmaterialize();
                    }

                    for (String err : errors)
                        _logError(tsv.getName() + " -- " + err);

                    if (deleteAfterImport)
                    {
                        boolean success = tsv.delete();
                        if (success)
                            _logInfo("Deleted file " + tsv.getPath());
                        else
                            _logError("Could not delete file " + tsv.getPath());
                    }
                    assert cpuImport.stop();
                }
            }
            catch (Exception x)
            {
                // If we have an active transaction, we need to roll it back
                // before we log the error or the logging will take place inside the transaction
                if (scope.isTransactionActive())
                    scope.rollbackTransaction();
                
                _logError("Exception while importing file: " + tsv, x);
            }
            finally
            {
                if (scope.isTransactionActive())
                    scope.rollbackTransaction();
                boolean debug = false;
                assert debug = true;
                if (debug)
                {
                    _log.debug(cpuReadFile);
                    _log.debug(cpuDelete);
                    _log.debug(cpuImport);
                    _log.debug(cpuCommit);
                }
            }
        }

        public Action getAction()
        {
            return action;
        }

        public File getFile()
        {
            return tsv;
        }

        public String getFileName()
        {
            return null == tsv ? null : tsv.getName();
        }

        public DataSetDefinition getDatasetDefinition()
        {
            return datasetDefinition;
        }

        public String getVisitDatePropertyName()
        {
            if (visitDatePropertyName == null && getDatasetDefinition() != null)
                return getDatasetDefinition().getVisitDatePropertyName();
            return visitDatePropertyName;
        }

        public void setVisitDatePropertyName(String visitDatePropertyName)
        {
            this.visitDatePropertyName = visitDatePropertyName;
        }
    }


    public class ParticipantImportJob extends DatasetImportJob
    {
        String _siteLookup = "RowId";

        ParticipantImportJob(DataSetDefinition ds, File tsv, Action action, boolean deleteAfterImport, Map<String,String> columnMap)
        {
            super(ds,tsv,action,deleteAfterImport,columnMap);
        }


        public void setSiteLookup(String lookup)
        {
            if (lookup.toLowerCase().equals("siteid"))
                lookup = "RowId";
            _siteLookup = lookup;
        }


        @Override
        public void run()
        {
            try
            {
                _run();
            }
            catch (Exception x)
            {
                _logError("Unexpected error importing file: " + tsv.getName(), x);
            }
        }


        @Override
        public String validate()
        {
            String error = super.validate();
            if (error != null)
                return error;

            TableInfo site = StudySchema.getInstance().getTableInfoSite();
            ColumnInfo col = site.getColumn(_siteLookup);
            if (col == null || _siteLookup.toLowerCase().startsWith("is"))
                return "No such column in Site table: " + _siteLookup;

            return null;
        }


        public void _run() throws IOException, SQLException
        {
            TempTableLoader loader = new TempTableLoader(tsv, true);
            CaseInsensitiveHashMap<ColumnDescriptor> columnMap = new CaseInsensitiveHashMap<ColumnDescriptor>();
            for (ColumnDescriptor c : loader.getColumns())
                columnMap.put(c.name, c);

            if (!columnMap.containsKey("ParticipantId"))
            {
                _logError("Dataset does not contain column ParticipantId.");
                return;
            }

            StudySchema schema = StudySchema.getInstance();

            Table.TempTableInfo tinfoTemp = loader.loadTempTable(schema.getSchema());
            TableInfo site = StudySchema.getInstance().getTableInfoSite();
            ColumnInfo siteLookup = site.getColumn(_siteLookup);

            // Merge uploaded data with Study tables

            Table.execute(schema.getSchema(),
                    "INSERT INTO " + schema.getTableInfoParticipant() + " (ParticipantId)\n" +
                    "SELECT ParticipantId FROM " + tinfoTemp + " WHERE ParticipantId NOT IN (SELECT ParticipantId FROM " + schema.getTableInfoParticipant() + ")", null);

            if (columnMap.containsKey("EnrollmentSiteId"))
            {
                Table.execute(schema.getSchema(),
                    "UPDATE " + schema.getTableInfoParticipant() + " SET EnrollmentSiteId=study.Site.RowId\n" +
                    "FROM " + tinfoTemp + " JOIN study.Site ON " + tinfoTemp.toString() + ".EnrollmentSiteId=study.Site." + siteLookup.getSelectName() + "\n" +
                    "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
            }
            if (columnMap.containsKey("CurrentSiteId"))
            {
                Table.execute(schema.getSchema(),
                    "UPDATE " + schema.getTableInfoParticipant() + " SET CurrentSiteId=study.Site.RowId\n" +
                    "FROM " + tinfoTemp + " JOIN study.Site ON " + tinfoTemp.toString() + ".CurrentSiteId=study.Site." + siteLookup.getSelectName() + "\n" +
                    "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
            }

            if (columnMap.containsKey("StartDate"))
            {
                Table.execute(schema.getSchema(),
                    "UPDATE " + schema.getTableInfoParticipant() + " SET StartDate=" + tinfoTemp.toString() + ".StartDate\n" +
                    "FROM " + tinfoTemp + " \n" +
                    "WHERE " + schema.getTableInfoParticipant() + ".ParticipantId = " + tinfoTemp.toString() + ".ParticipantId", null);
            }
            tinfoTemp.delete();
        }
    }
}
