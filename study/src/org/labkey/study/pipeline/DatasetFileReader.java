/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.AbstractDatasetImportTask.Action;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* User: adam
* Date: Sep 2, 2009
* Time: 9:13:14 PM
*/
public class DatasetFileReader
{
    public static final Pattern DEFAULT_PATTERN = Pattern.compile("^\\D*(\\d*).(?:tsv|txt|xls|xlsx)$");

    protected Pattern _filePattern = DEFAULT_PATTERN;
    protected final StudyImpl _study;
    protected final StudyManager _studyManager = StudyManager.getInstance();
    protected final StudyImportContext _studyImportContext;

    protected String _datasetsFileName;
    protected VirtualFile _datasetsDirectory;
    protected Set<String> _datasetsNotFound = new HashSet<>();

    protected ArrayList<DatasetImportRunnable> _runnables = null;

    public DatasetFileReader(VirtualFile datasetsDirectory, String datasetsFileName, StudyImpl study)
    {
        this(datasetsDirectory, datasetsFileName, study, null);
    }

    public DatasetFileReader(VirtualFile datasetsDirectory, String datasetsFileName, StudyImpl study, StudyImportContext studyImportContext)
    {
        _datasetsDirectory = datasetsDirectory;
        _datasetsFileName = datasetsFileName;
        _study = study;
        _studyImportContext = studyImportContext;
    }

    public String getDefinitionFileName()
    {
        return _datasetsFileName;
    }

    public Set<String> getDatasetsNotFound() { return _datasetsNotFound; }

    public List<DatasetImportRunnable> getRunnables()
    {
        if (null == _runnables)
            return Collections.emptyList();
        return Collections.unmodifiableList(_runnables);
    }

    @Nullable
    protected String getKeyFromDatasetName(String name)
    {
        Matcher m = _filePattern.matcher(name);
        if (m.matches())
        {
            String key = m.group(1);
            if (key != null)
                return normalizeIntegerString(key);
        }
        return null;
    }

    protected Map<String, DatasetDefinition> getDatasetDefinitionMap()
    {
        List<DatasetDefinition> dsArray = _studyManager.getDatasetDefinitions(_study);
        Map<String, DatasetDefinition> dsMap = new CaseInsensitiveHashMap<>(dsArray.size() * 3);
        // UNDONE: duplicate labels? dataset named participant?
        for (DatasetDefinition ds : dsArray)
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
            dsMap.put(String.valueOf(ds.getDatasetId()), ds);
        }

        // add fake DatasetDefinition for virtual Participant dataset
        DatasetDefinition dsParticipant = new DatasetDefinition(_study, -1, "Participant", "Participant", null, null, "StudyParticipant");
        dsMap.put("participant", dsParticipant);

        return dsMap;
    }

    public void validate(List<String> errors) throws IOException
    {
        Properties props = new Properties();

        if (_datasetsFileName != null)
        {
            try (InputStream is = _datasetsDirectory.getInputStream(_datasetsFileName))
            {
                if (is != null)
                    props.load(is);
            }
        }

        if (null == _study)
        {
            errors.add("Study has not been created yet.");
            return;
        }

        Map<String, DatasetDefinition> dsMap = getDatasetDefinitionMap();
        IdentityHashMap<DatasetDefinition, DatasetImportRunnable> jobMap = new IdentityHashMap<>();

        //
        // load defaults
        //

        Action defaultAction = Action.REPLACE;
        boolean importAllMatches = true;
        boolean defaultDeleteAfterImport = false;
        Date defaultReplaceCutoff = null;

        OneToOneStringMap defaultColumnMap = new OneToOneStringMap();

        for (Map.Entry e : props.entrySet())
        {
            String key = StringUtils.trimToEmpty((String) e.getKey()).toLowerCase();
            String value = StringUtils.trimToEmpty((String) e.getValue());
            if (!key.startsWith("default."))
                continue;
            if (key.equals("default.action"))
            {
                defaultAction = actionForName(value);
            }
            else if (key.equals("default.filepattern"))
            {
                _filePattern = Pattern.compile("^" + value + "$");
            }
            else if (key.equals("default.deleteafterimport"))
            {
                defaultDeleteAfterImport = "true".equals(value.trim().toLowerCase());
            }
            else if (key.equals("default.importallmatches"))
            {
                importAllMatches = "true".equals(value.trim().toLowerCase());
            }
            else if (key.equals("default.replacenewerthandate"))
            {
                if ("false".equalsIgnoreCase(value))
                    defaultReplaceCutoff = null;
                else
                    defaultReplaceCutoff = parseDate(value, errors);
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
            String key = StringUtils.trimToEmpty((String) e.getKey()).toLowerCase();
            String value = StringUtils.trimToEmpty((String) e.getValue());
            int period = key.indexOf('.');
            if (key.startsWith("default.") || -1 == period)
                continue;
            String datasetKey = key.substring(0, period);
            String propertyKey = key.substring(period + 1);
            DatasetDefinition ds = dsMap.get(datasetKey);
            if (null == ds)
            {
                errors.add("Could not find dataset for '" + datasetKey + "'");
                continue;
            }
            DatasetImportRunnable runnable = jobMap.get(ds);
            if (null == runnable)
            {
                runnable = newImportJob(ds, _datasetsDirectory, null, defaultAction, defaultDeleteAfterImport, defaultReplaceCutoff, defaultColumnMap);
                jobMap.put(ds, runnable);
            }
            if (propertyKey.equals("file"))
            {
                runnable._fileName = value;
            }
            else if (propertyKey.equals("action"))
            {
                runnable._action = actionForName(value);
            }
            else if (propertyKey.equals("deleteafterimport"))
            {
                runnable._deleteAfterImport = "true".equals(value.trim().toLowerCase());
            }
            else if (propertyKey.equals("visitdatepropertyname"))
            {
                runnable.setVisitDatePropertyName(StringUtils.trimToNull(value));
            }
            else if (propertyKey.equals("replacenewerthandate"))
            {
                if ("false".equalsIgnoreCase(value))
                    runnable._replaceCutoff = null;
                else
                    runnable._replaceCutoff = parseDate(value, errors);
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

        for (String name : getDatasetFileNames())
        {
            String dsKey = getKeyFromDatasetName(name);
            if (dsKey == null)
                continue;

            DatasetDefinition ds = dsMap.get(dsKey.toLowerCase());
            if (null == ds)
            {
                _datasetsNotFound.add(name);
                continue;
            }
            else if (ds.isAssayData())
            {
                errors.add("Unable to import data for assay dataset '" + ds.getLabel() + "'.");
                continue;
            }
            DatasetImportRunnable runnable = jobMap.get(ds);
            if (null == runnable)
            {
                if (!importAllMatches)
                    continue;
                runnable = newImportJob(ds, _datasetsDirectory, name, defaultAction, defaultDeleteAfterImport, defaultReplaceCutoff, defaultColumnMap);
                jobMap.put(ds, runnable);
            }
            else if (runnable._fileName == null)
                runnable._fileName = name;
        }

        _runnables = new ArrayList<>(jobMap.values());
        _runnables.sort((j1, j2) ->
        {
            String name1 = j1.getFileName();
            String name2 = j2.getFileName();
            if (name1 != null && name2 != null)
                return name1.compareTo(name2);
            if (name1 != null || name2 != null)
                return name1 == null ? -1 : 1;
            return j1._datasetDefinition.getDatasetId() - j2._datasetDefinition.getDatasetId();
        });
    }

    protected List<String> getDatasetFileNames()
    {
        List<String> names = new ArrayList<>();
        for (String name : _datasetsDirectory.list())
        {
            if (name.startsWith("."))
                continue;
            Matcher m = _filePattern.matcher(name);
            if (!m.find())
                continue;

            names.add(name);
        }
        return names;
    }

    private Date parseDate(String value, List<String> errors)
    {
        if (value == null)
            return null;

        try
        {
            Date d = DateUtil.parseDateTime(value, "yyyy-MM-dd");
            d.setHours(0);
            d.setMinutes(0);
            d.setSeconds(0);
            return d;
        }
        catch (ParseException pe)
        {
            errors.add("Failed to parse replaceNewerThanDate '" + value + "': " + pe.getMessage());
            return null;
        }
    }

    private DatasetImportRunnable newImportJob(DatasetDefinition ds, VirtualFile root, String fileName, Action defaultAction, boolean defaultDeleteAfterImport, Date defaultReplaceCutoff, OneToOneStringMap defaultColumnMap)
    {
        DatasetImportRunnable runnable;
        if (ds.getDatasetId() == -1 && "Participant".equals(ds.getName()))
            runnable = new ParticipantImportRunnable(_studyImportContext != null ? _studyImportContext.getLogger() : null, _study, ds, root, fileName, defaultAction, defaultDeleteAfterImport, defaultReplaceCutoff, defaultColumnMap);
        else
            runnable = new DatasetImportRunnable(_studyImportContext != null ? _studyImportContext.getLogger() : null, _study, ds, root, fileName, defaultAction, defaultDeleteAfterImport, defaultReplaceCutoff, defaultColumnMap, _studyImportContext);
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
        PropertyDescriptor pd = DatasetDefinition.getStandardPropertiesMap().get(property);
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
    public static class OneToOneStringMap extends AbstractMap<String, String>
    {
        private final CaseInsensitiveHashMap<Pair<String, String>> keyMap = new CaseInsensitiveHashMap<>();
        private final CaseInsensitiveHashMap<Pair<String, String>> valMap = new CaseInsensitiveHashMap<>();

        @Override
        public String get(Object key)
        {
            Pair<String, String> p = keyMap.get(key);
            return p == null ? null : p.getValue();
        }


        public String getKey(Object value)
        {
            Pair<String, String> p = valMap.get(value);
            return p == null ? null : p.getKey();
        }


        @Override
        public String put(String key, String value)
        {
            Pair<String, String> p = new Pair<>(key, value);
            String ret = _remove(p).getValue();
            _put(p);
            return ret;
        }

        /*  returns <old mapping for new value, old mapping for new key> */
        private Pair<String, String> _remove(Pair<String, String> p)
        {
            String oldKey = null;
            String oldValue = null;

            Pair<String, String> t = keyMap.get(p.getKey());
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

            return new Pair<>(oldKey, oldValue);
        }


        private void _put(Pair<String, String> p)
        {
            keyMap.put(p.getKey(), p);
            valMap.put(p.getValue(), p);
        }


        @Override
        public String remove(Object key)
        {
            Pair<String, String> p = keyMap.get(key);
            if (null == p)
                return null;
            return _remove(p).getValue();
        }

        public String removeValue(Object value)
        {
            Pair<String, String> p = valMap.get(value);
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
            Set<Entry<String, String>> set = new HashSet<>();
            set.addAll(keyMap.values());
            return Collections.unmodifiableSet(set);
        }
    }
}
