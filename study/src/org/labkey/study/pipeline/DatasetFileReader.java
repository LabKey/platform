/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.util.Pair;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.AbstractDatasetImportTask.Action;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* User: adam
* Date: Sep 2, 2009
* Time: 9:13:14 PM
*/
public class DatasetFileReader
{
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("^\\D*(\\d*).(?:tsv|txt)$");

    private final File _definitionFile;
    private final StudyImpl _study;
    private final StudyManager _studyManager = StudyManager.getInstance();
    private final AbstractDatasetImportTask _task;

    private ArrayList<DatasetImportRunnable> _runnables = null;

    public DatasetFileReader(File datasetFile, StudyImpl study)
    {
        this(datasetFile, study, null);
    }

    public DatasetFileReader(File datasetFile, StudyImpl study, AbstractDatasetImportTask task)
    {
        _definitionFile = datasetFile;
        _study = study;
        _task = task;
    }

    public File getDefinitionFile()
    {
        return _definitionFile;
    }

    public List<DatasetImportRunnable> getRunnables()
    {
        if (null == _runnables)
            return Collections.emptyList();
        return Collections.unmodifiableList(_runnables);
    }

    public void validate(List<String> errors) throws IOException
    {
        File dir = _definitionFile.getParentFile();
        InputStream is = null;
        Properties props;

        try
        {
            is = new FileInputStream(_definitionFile);
            props = new Properties();
            props.load(is);
        }
        finally
        {
            if (null != is)
                is.close();
        }

        if (null == _study)
        {
            errors.add("Study has not been created yet.");
            return;
        }

        DataSetDefinition[] dsArray = _studyManager.getDataSetDefinitions(_study);
        HashMap<String, DataSetDefinition> dsMap = new HashMap<String, DataSetDefinition>(dsArray.length * 3);
        // UNDONE: duplicate labels? dataset named participant?
        for (DataSetDefinition ds : dsArray)
        {
            // When mapping a dataset identifier to an acutal dataset object,
            // we first check to see if the identifier is a dataset ID, then a
            // dataset name, then a dataset label.  We add these values to our
            // lookup map in reverse order, so duplicates (i.e., if a dataset's
            // name is the same as another dataset's label) are overwritten by the
            // more important value.  This leaves us with a single map that handles
            // our lookup hierarchy.
            String label = StringUtils.trimToNull(ds.getLabel());
            if (null != label)
                dsMap.put(label.toLowerCase(), ds);
            String name = StringUtils.trimToNull(ds.getName());
            if (null != name)
                dsMap.put(name.toLowerCase(), ds);
            dsMap.put(String.valueOf(ds.getDataSetId()), ds);
        }

        // add fake DataSetDefinition for virtual Participant dataset
        DataSetDefinition dsParticipant = new DataSetDefinition(_study, -1, "Participant", "Participant", null, "StudyParticipant");
        dsMap.put("participant", dsParticipant);

        IdentityHashMap<DataSetDefinition, DatasetImportRunnable> jobMap = new IdentityHashMap<DataSetDefinition, DatasetImportRunnable>();

        //
        // load defaults
        //

        Action defaultAction = Action.REPLACE;
        Pattern filePattern = DEFAULT_PATTERN;
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
            DatasetImportRunnable runnable = jobMap.get(ds);
            if (null == runnable)
            {
                runnable = newImportJob(ds, null, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
                jobMap.put(ds, runnable);
            }
            if (propertyKey.equals("file"))
            {
                runnable._tsv = new File(dir, value);
            }
            else if (propertyKey.equals("action"))
            {
                runnable._action = actionForName(value);
            }
            else if (propertyKey.equals("deleteAfterImport"))
            {
                runnable._deleteAfterImport = "true".equals(value.trim().toLowerCase());
            }
            else if (propertyKey.equals("visitDatePropertyName"))
            {
                runnable.setVisitDatePropertyName(StringUtils.trimToNull(value));
            }
            else if (propertyKey.startsWith("property."))
            {
                String property = propertyKey.substring("property.".length()).trim();
                String column = value.trim();
                property = toStudyPropertyURI(property);
                runnable._columnMap.put(column, property);
            }
            else if (propertyKey.equals("sitelookup"))
            {
                if (runnable instanceof ParticipantImportRunnable)
                    ((ParticipantImportRunnable) runnable).setSiteLookup(value.trim());
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
            DatasetImportRunnable runnable = jobMap.get(ds);
            if (null == runnable)
            {
                if (!importAllMatches)
                    continue;
                runnable = newImportJob(ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
                jobMap.put(ds, runnable);
            }
            else if (runnable._tsv == null)
                runnable._tsv = tsv;
        }

        _runnables = new ArrayList<DatasetImportRunnable>(jobMap.values());
        Collections.sort(_runnables, new Comparator<DatasetImportRunnable>()
        {
            public int compare(DatasetImportRunnable j1, DatasetImportRunnable j2)
            {
                String name1 = j1.getFileName();
                String name2 = j2.getFileName();
                if (name1 != null && name2 != null)
                    return name1.compareTo(name2);
                if (name1 != null || name2 != null)
                    return name1 == null ? -1 : 1;
                return j1._datasetDefinition.getDataSetId() - j2._datasetDefinition.getDataSetId();
            }
        });
    }


    private DatasetImportRunnable newImportJob(DataSetDefinition ds, File tsv, Action defaultAction, boolean defaultDeleteAfterImport, OneToOneStringMap defaultColumnMap)
    {
        DatasetImportRunnable runnable;
        if (ds.getDataSetId() == -1 && "Participant".equals(ds.getLabel()))
            runnable = new ParticipantImportRunnable(_task, ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
        else
            runnable = new DatasetImportRunnable(_task, ds, tsv, defaultAction, defaultDeleteAfterImport, defaultColumnMap);
        return runnable;
    }


    private String normalizeIntegerString(String dsKey)
    {
        try
        {
            return String.valueOf(Integer.parseInt(dsKey));
        }
        catch (NumberFormatException x)
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


    /**
     * Key and value are case insensitive on search, but are returned in original case
     */
    public static class OneToOneStringMap extends AbstractMap<String,String>
    {
        private final CaseInsensitiveHashMap<Pair<String,String>> keyMap = new CaseInsensitiveHashMap<Pair<String,String>>();
        private final CaseInsensitiveHashMap<Pair<String,String>> valMap = new CaseInsensitiveHashMap<Pair<String,String>>();

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
}
