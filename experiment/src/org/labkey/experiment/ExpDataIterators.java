package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.AliasInsertHelper;
import org.labkey.experiment.api.SampleSetUpdateServiceDI;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class ExpDataIterators
{

    /**
     * Data iterator to handle aliases
     */
    public static class AliasDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final Container _container;
        private final User _user;
        private final TableInfo _expAliasTable;

        public AliasDataIteratorBuilder(@NotNull DataIteratorBuilder in, Container container, User user, TableInfo expTable)
        {
            _in = in;
            _container = container;
            _user = user;
            _expAliasTable = expTable;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AliasDataIterator(pre, context, _container, _user, _expAliasTable));
        }
    }

    private static class AliasDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _aliasCol;
        final Container _container;
        final User _user;
        Map<String, Object> _lsidAliasMap = new HashMap<>();
        private final TableInfo _expAliasTable;

        protected AliasDataIterator(DataIterator di, DataIteratorContext context, Container container,  User user, TableInfo expTable)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _aliasCol = map.get("alias");

            _container = container;
            _user = user;
            _expAliasTable = expTable;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            //noinspection ThrowableNotThrown
            if (getErrors().hasErrors())
                return hasNext;

            // after the last row, insert all of the aliases
            if (!hasNext)
            {
                final ExperimentService svc = ExperimentService.get();

                try (DbScope.Transaction transaction = svc.getTinfoDataClass().getSchema().getScope().ensureTransaction())
                {
                    for (Map.Entry<String, Object> entry : _lsidAliasMap.entrySet())
                    {
                        String lsid = entry.getKey();
                        Object aliases = entry.getValue();
                        AliasInsertHelper.handleInsertUpdate(_container, _user, lsid, _expAliasTable, aliases);
                    }
                    transaction.commit();
                }

                return false;
            }

            // For each iteration, collect the lsid and alias col values.
            if (_lsidCol != null && _aliasCol != null)
            {
                Object lsidValue = get(_lsidCol);
                Object aliasValue = get(_aliasCol);

                if (aliasValue != null && lsidValue instanceof String)
                {
                    _lsidAliasMap.put((String) lsidValue, aliasValue);
                }
            }
            return true;
        }
    }


    public static class FlagDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final User _user;
        private final boolean _isSample;

        public FlagDataIteratorBuilder(@NotNull DataIteratorBuilder in, User user, boolean isSample)
        {
            _in = in;
            _user = user;
            _isSample = isSample;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new FlagDataIterator(pre, context, _user, _isSample));
        }
    }

    private static class FlagDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final User _user;
        final Integer _lsidCol;
        final Integer _flagCol;
        final boolean _isSample;    // as oppsed to DataClass

        protected FlagDataIterator(DataIterator di, DataIteratorContext context, User user, boolean isSample)
        {
            super(di);
            _context = context;
            _user = user;
            _isSample = isSample;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _flagCol = map.containsKey("flag") ? map.get("flag") : map.get("comment");
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();
            if (!hasNext)
                return false;

            // skip processing if there are errors upstream
            //noinspection ThrowableNotThrown
            if (getErrors().hasErrors())
                return true;

            if (_lsidCol != null && _flagCol != null)
            {
                Object lsidValue = get(_lsidCol);
                Object flagValue = get(_flagCol);

                if (lsidValue instanceof String)
                {
                    String lsid = (String)lsidValue;
                    String flag = Objects.toString(flagValue, null);

                    try
                    {
                        if (_isSample)
                        {
                            ExpMaterial sample = ExperimentService.get().getExpMaterial(lsid);
                            sample.setComment(_user, flag);
                        }
                        else
                        {
                            ExpData data = ExperimentService.get().getExpData(lsid);
                            data.setComment(_user, flag);
                        }
                    }
                    catch (ValidationException e)
                    {
                        throw new BatchValidationException(e);
                    }
                }

            }
            return true;
        }
    }


    public static class DerivationDataIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        final Container _container;
        final User _user;
        final boolean _isSample;

        public DerivationDataIteratorBuilder(DataIteratorBuilder pre, Container container, User user, boolean isSample)
        {
            _pre = pre;
            _container = container;
            _user = user;
            _isSample = isSample;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            if (null!=context.getConfigParameters() && context.getConfigParameters().containsKey(SampleSetUpdateServiceDI.Options.SkipDerivation))
            {
                return pre;
            }
            return LoggingDataIterator.wrap(new DerivationDataIterator(pre, context, _container, _user, _isSample));
        }
    }

    static class DerivationDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Map<Integer, String> _parentCols;
        // Map from Data LSID to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;

        final Container _container;
        final User _user;
        final boolean _isSample;

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context, Container container, User user, boolean isSample)
        {
            super(di);
            _context = context;
            _isSample = isSample;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _parentNames = new LinkedHashMap<>();
            _parentCols = new HashMap<>();
            _container = container;
            _user = user;

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (UploadSamplesHelper.isInputOutputHeader(name) || _isSample && equalsIgnoreCase("parent",name))
                {
                    _parentCols.put(entry.getValue(), entry.getKey());
                }
            }
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            //noinspection ThrowableNotThrown
            if (getErrors().hasErrors())
                return hasNext;

            // For each iteration, collect the parent col values
            if (hasNext && !_parentCols.isEmpty())
            {
                String lsid = (String) get(_lsidCol);
                Set<Pair<String, String>> allParts = new HashSet<>();
                for (Integer parentCol : _parentCols.keySet())
                {
                    Object o = get(parentCol);
                    if (o != null)
                    {
                        Collection<String> parentNames;
                        if (o instanceof String)
                        {
                            parentNames = Arrays.asList(((String) o).split(","));
                        }
                        else if (o instanceof JSONArray)
                        {
                            parentNames = Arrays.stream(((JSONArray) o).toArray()).map(String::valueOf).collect(Collectors.toSet());
                        }
                        else if (o instanceof Collection)
                        {
                            Collection<?> c = ((Collection)o);
                            parentNames = c.stream().map(String::valueOf).collect(Collectors.toSet());
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected comma separated list or a JSONArray of parent names: " + o, _parentCols.get(parentCol)));
                            continue;
                        }

                        String parentColName = _parentCols.get(parentCol);
                        Set<Pair<String, String>> parts = parentNames.stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(s -> Pair.of(parentColName, s))
                                .collect(Collectors.toSet());

                        allParts.addAll(parts);
                    }
                }
                if (!allParts.isEmpty())
                    _parentNames.put(lsid, allParts);
            }

            //noinspection ThrowableNotThrown
            if (getErrors().hasErrors())
                return hasNext;

            if (!hasNext)
            {
                try
                {
                    RemapCache cache = new RemapCache();
                    Map<Integer, ExpMaterial> materialCache = new HashMap<>();
                    Map<Integer, ExpData> dataCache = new HashMap<>();

                    List<UploadSamplesHelper.UploadSampleRunRecord> runRecords = new ArrayList<>();
                    for (Map.Entry<String, Set<Pair<String, String>>> entry : _parentNames.entrySet())
                    {
                        String lsid = entry.getKey();
                        Set<Pair<String, String>> parentNames = entry.getValue();

                        Pair<RunInputOutputBean, RunInputOutputBean> pair =
                                UploadSamplesHelper.resolveInputsAndOutputs(_user, _container, parentNames, null, cache, materialCache, dataCache);

                        if (pair.first == null && pair.second == null)
                            continue;

                        ExpMaterial sample = null;
                        Map<ExpMaterial, String> currentMaterialMap = Collections.emptyMap();
                        ExpData data = null;
                        Map<ExpData, String> currentDataMap = Collections.emptyMap();
                        if (_isSample)
                        {
                            sample = ExperimentService.get().getExpMaterial(lsid);
                            if (null == sample)
                                continue;

                            if (_context.getInsertOption().mergeRows)
                            {
                                // TODO only call this for existing rows
                                // TODO always clear? or only when parentcols is in input? or only when new derivation is specified?
                                // Since this entry was (maybe) already in the database, we may need to delete old derivation info
                                UploadSamplesHelper.clearSampleSourceRun(_user, sample);
                            }
                            currentMaterialMap = new HashMap<>();
                            currentMaterialMap.put(sample, "Sample");
                        }
                        else
                        {
                            data = ExperimentService.get().getExpData(lsid);
                            if (null == data)
                                continue;
                            currentDataMap = Collections.singletonMap(data, "Data");
                        }

                        if (pair.first != null)
                        {
                            // Add parent derivation run
                            Map<ExpMaterial, String> parentMaterialMap = pair.first.getMaterials();
                            Map<ExpData, String> parentDataMap = pair.first.getDatas();

                            boolean merge = _isSample;
                            UploadSamplesHelper.record(merge, runRecords,
                                    parentMaterialMap, currentMaterialMap,
                                    parentDataMap, currentDataMap);
                        }

                        if (pair.second != null)
                        {
                            // Add child derivation run
                            Map<ExpMaterial, String> childMaterialMap = pair.second.getMaterials();
                            Map<ExpData, String> childDataMap = pair.second.getDatas();

                            UploadSamplesHelper.record(false, runRecords,
                                    currentMaterialMap, childMaterialMap,
                                    currentDataMap, childDataMap);
                        }
                    }

                    if (!runRecords.isEmpty())
                    {
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_container, _user, null), null);
                    }
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
                catch (ValidationException e)
                {
                    getErrors().addRowError(e);
                }
            }
            return hasNext;
        }
    }

    public static class SearchIndexIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        final Function<List<String>, Runnable> _indexFunction;

        public SearchIndexIteratorBuilder(DataIteratorBuilder pre, Function<List<String>, Runnable> indexFunction)
        {
            _pre = pre;
            _indexFunction = indexFunction;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new SearchIndexIterator(pre, context, _indexFunction));
        }
    }

    private static class SearchIndexIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final ArrayList<String> _lsids;
        final Function<List<String>, Runnable> _indexFunction;

        protected SearchIndexIterator(DataIterator di, DataIteratorContext context, Function<List<String>, Runnable> indexFunction)
        {
            super(di);
            _context = context;
            _indexFunction = indexFunction;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _lsids = new ArrayList<>(100);
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (hasNext)
            {
                String lsid = (String) get(_lsidCol);
                if (null != lsid)
                    _lsids.add(lsid);
            }
            else
            {
                final SearchService ss = SearchService.get();
                if (null != ss)
                {
                    final ArrayList<String> lsids = new ArrayList<>(_lsids);
                    final Runnable indexTask = _indexFunction.apply(lsids);
                    Runnable commitTask = () -> ss.defaultTask().addRunnable(indexTask, SearchService.PRIORITY.bulk);
                    if (null != DbScope.getLabKeyScope() && null != DbScope.getLabKeyScope().getCurrentTransaction())
                        DbScope.getLabKeyScope().getCurrentTransaction().addCommitTask(commitTask, DbScope.CommitTaskOption.POSTCOMMIT);
                    else
                        commitTask.run();
                }
            }
            return hasNext;
        }
    }


    // This should be used AFTER StandardDataIteratorBuilder, say at the beginning of PersistDataIteratorBuilder (below)
    // The incoming dataiterator should bound to target table and have complete ColumnInfo metadata
    // see SimpleQueryUpdateService.convertTypes() for similar handling of FILE_LINK columns
    public static class FileLinkDataIterator extends WrapperDataIterator
    {
        Supplier[] suppliers;
        String[] savedFileName;

        FileLinkDataIterator(final DataIterator in,  final DataIteratorContext context, Container c, String file_link_dir_name)
        {
            super(in);
            suppliers = new Supplier[in.getColumnCount() + 1];
            savedFileName = new String[in.getColumnCount() + 1];

            for (int i = 0; i < suppliers.length; i++)
            {
                ColumnInfo col = in.getColumnInfo(i);
                if (PropertyType.FILE_LINK != col.getPropertyType())
                {
                    suppliers[i] = in.getSupplier(i);
                }
                else
                {
                    final int index = i;
                    suppliers[i] = () -> {
                        if (savedFileName[index] != null)
                            return savedFileName[index];
                        Object value = in.get(index);
                        if (value instanceof MultipartFile || value instanceof AttachmentFile)
                        {
                            try
                            {
                                Object file = AbstractQueryUpdateService.saveFile(c, col.getName(), value, file_link_dir_name);
                                assert file instanceof File;
                                value = ((File)file).getPath();
                                savedFileName[index] = (String)value;
                            }
                            catch (QueryUpdateServiceException ex)
                            {
                                context.getErrors().addRowError(new ValidationException(ex.getMessage()));
                                value = null;
                            }
                            catch (ValidationException vex)
                            {
                                context.getErrors().addRowError(vex);
                                value = null;
                            }
                        }
                        return value;
                    };
                }
            }
        }

        @Override
        public Object get(int i)
        {
            return suppliers[i].get();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            for (int i=0 ; i<savedFileName.length ; i++)
                savedFileName[i] = null;
            return super.next();
        }
    }

    public static class PersistDataIteratorBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder _in;
        private final TableInfo _expTable;
        private final TableInfo _propertiesTable;
        private final Container _container;
        private final User _user;
        private String _fileLinkDirectory = null;
        Function<List<String>, Runnable> _indexFunction;


        // expTable is the shared experiment table e.g. exp.Data or exp.Materials
        public PersistDataIteratorBuilder(@NotNull DataIteratorBuilder in, TableInfo expTable, TableInfo propsTable, Container container, User user)
        {
            _in = in;
            _expTable = expTable;
            _propertiesTable = propsTable;
            _container = container;
            _user = user;
        }

        public PersistDataIteratorBuilder setIndexFunction(Function<List<String>, Runnable> indexFunction)
        {
            _indexFunction = indexFunction;
            return this;
        }

        public PersistDataIteratorBuilder setFileLinkDirectory(String dir)
        {
            _fileLinkDirectory = dir;
            return this;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            // add FileLink DataIterator if any input columns are of type FILE_LINK
            if (null != _fileLinkDirectory)
            {
                boolean hasFileLink = false;
                for (int i = 0; i < input.getColumnCount(); i++)
                    hasFileLink |= PropertyType.FILE_LINK == input.getColumnInfo(i).getPropertyType();
                if (hasFileLink)
                    input = new FileLinkDataIterator(input, context, _container, _fileLinkDirectory);
            }

            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("alias"));
            step0.setDebugName("drop alias");

            // Insert into exp.data then the provisioned table
            // Temporary work around for Issue 26082 (row at a time, reselect rowid)
            // mucking with the context like this is not UL approved
            context.setSelectIds(true);
            // The PK is rowid, switch to use lsid
            DataIteratorBuilder step2 = TableInsertDataIterator.create(DataIteratorBuilder.wrap(step0), _expTable, _container, context,
                    Collections.singleton("lsid"), null, null);
            context.setSelectIds(false);
            // TODO the lsid is a unique key, but is not recognized as the PK in the schema table
            DataIteratorBuilder step3 = TableInsertDataIterator.create(step2, _propertiesTable, _container, context,
                Collections.singleton("lsid"), null, null);

            boolean isSample = "Material".equalsIgnoreCase(_expTable.getName());

            DataIteratorBuilder step4 = step3;
            if (colNameMap.containsKey("flag") || colNameMap.containsKey("comment"))
            {
                step4 = new ExpDataIterators.FlagDataIteratorBuilder(step3, _user, isSample);
            }

            // Wire up derived parent/child data and materials
            DataIteratorBuilder step5 = new ExpDataIterators.DerivationDataIteratorBuilder(step4, _container, _user, isSample);

            // Hack: add the alias and lsid values back into the input so we can process them in the chained data iterator
            DataIteratorBuilder step6 = step5;
            if (colNameMap.containsKey("alias"))
            {
                SimpleTranslator st = new SimpleTranslator(step5.getDataIterator(context), context);
                st.selectAll();
                //ColumnInfo aliasCol = getColumn(FieldKey.fromParts("alias"));
                final DataIterator _aliasDI = input;
                st.addColumn(new BaseColumnInfo("alias", JdbcType.VARCHAR), (Supplier) () -> _aliasDI.get(colNameMap.get("alias")));
                step6 = DataIteratorBuilder.wrap(st);
            }

            DataIteratorBuilder step7 = step6;
            if (null != _indexFunction)
                step7 = new ExpDataIterators.SearchIndexIteratorBuilder(step6, _indexFunction); // may need to add this after the aliases are set

            return LoggingDataIterator.wrap(step7.getDataIterator(context));
        }
    }
}
