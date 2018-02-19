/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.assay.dilution;

import org.apache.commons.lang3.EnumUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.nab.NabSpecimen;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.AbstractExperimentDataHandler;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.Position;
import org.labkey.api.study.Well;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 5/8/13
 */
public abstract class DilutionDataHandler extends AbstractExperimentDataHandler
{
    public static final Logger LOG = Logger.getLogger(DilutionDataHandler.class);

    public static final String NAB_PROPERTY_LSID_PREFIX = "NabProperty";

    public static final String DILUTION_INPUT_MATERIAL_DATA_PROPERTY = "SpecimenLsid";
    public static final String WELLGROUP_NAME_PROPERTY = "WellgroupName";
    public static final String FIT_ERROR_PROPERTY = "Fit Error";
    public static final String CURVE_IC_PREFIX = "Curve IC";
    public static final String POINT_IC_PREFIX = "Point IC";
    public static final String AUC_PREFIX = "AUC";
    public static final String pAUC_PREFIX = "PositiveAUC";
    public static final String DATA_ROW_LSID_PROPERTY = "Data Row LSID";
    public static final String AUC_PROPERTY_FORMAT = "0.000";
    public static final String STD_DEV_PROPERTY_NAME = "StandardDeviation";

    /** Lock object to only allow one thread to be populating well data at a time */
    public static final Object WELL_DATA_LOCK_OBJECT = new Object();

    private String _dataRowLsidPrefix;

    public DilutionDataHandler(String dataLsidPrefix)
    {
        _dataRowLsidPrefix = dataLsidPrefix;
    }

    protected class DilutionDataFileParser
    {
        private ExpData _data;
        private File _dataFile;
        private ViewBackgroundInfo _info;

        public DilutionDataFileParser(ExpData data, File dataFile, ViewBackgroundInfo info)
        {
            _data = data;
            _dataFile = dataFile;
            _info = info;
        }

        public List<Map<String, Object>> getResults() throws ExperimentException
        {
            return calculateDilutionStats(_data.getRun(), _info.getUser(), _dataFile, false, false);
        }
    }

    /**
     * Parses either the data file (or run data) to calculate cutoff and AUC information.
     *
     * @param dataFile the NAb data file, can be null if useRunForPlates is set to true
     * @param useRunForPlates create the plates from the saved run information
     * @param recalculateStats if true calculates dilution stats (mean, stdDev, max, min, percent neutralization) from the
     *                         plate data versus the saved table data. If use run for plates is false (use dataFile) then
     *                         stats are always recalculated.
     */
    public List<Map<String, Object>> calculateDilutionStats(ExpRun run, User user, @Nullable File dataFile, boolean useRunForPlates, boolean recalculateStats) throws ExperimentException
    {
        try {
            DilutionAssayRun assayResults = getAssayResults(run, user, dataFile, null, useRunForPlates, recalculateStats);
            List<Map<String, Object>> results = new ArrayList<>();

            for (int summaryIndex = 0; summaryIndex < assayResults.getSummaries().length; summaryIndex++)
            {
                DilutionSummary dilution = assayResults.getSummaries()[summaryIndex];
                WellGroup group = dilution.getFirstWellGroup();
                ExpMaterial sampleInput = assayResults.getMaterial(group);

                Map<String, Object> props = new HashMap<>();
                results.add(props);

                // generate curve ICs and AUCs for each curve fit type
                if (assayResults.getSavedCurveFitType() != StatsService.CurveFitType.NONE)
                {
                    for (StatsService.CurveFitType type : StatsService.CurveFitType.values())
                    {
                        for (Integer cutoff : assayResults.getCutoffs())
                        {
                            double value = dilution.getCutoffDilution(cutoff / 100.0, type);
                            saveICValue(getPropertyName(CURVE_IC_PREFIX, cutoff, type), value,
                                    dilution, props, type);

                            if (type == assayResults.getRenderedCurveFitType())
                            {
                                saveICValue(CURVE_IC_PREFIX + cutoff, value,
                                        dilution, props, type);
                            }
                        }
                        // compute both normal and positive AUC values
                        double auc = dilution.getAUC(type, StatsService.AUCType.NORMAL);
                        if (!Double.isNaN(auc))
                        {
                            props.put(getPropertyName(AUC_PREFIX, type), auc);
                            if (type == assayResults.getRenderedCurveFitType())
                                props.put(AUC_PREFIX, auc);
                        }

                        double pAuc = dilution.getAUC(type, StatsService.AUCType.POSITIVE);
                        if (!Double.isNaN(pAuc))
                        {
                            props.put(getPropertyName(pAUC_PREFIX, type), pAuc);
                            if (type == assayResults.getRenderedCurveFitType())
                                props.put(pAUC_PREFIX, pAuc);
                        }
                    }
                }

                // only need one set of interpolated ICs as they would be identical for all fit types
                for (Integer cutoff : assayResults.getCutoffs())
                {
                    saveICValue(POINT_IC_PREFIX + cutoff, dilution.getInterpolatedCutoffDilution(cutoff / 100.0, assayResults.getRenderedCurveFitType()),
                            dilution, props, assayResults.getRenderedCurveFitType());
                }
                props.put(FIT_ERROR_PROPERTY, dilution.getFitError());
                props.put(DILUTION_INPUT_MATERIAL_DATA_PROPERTY, sampleInput.getLSID());
                props.put(WELLGROUP_NAME_PROPERTY, group.getName());
                props.put(STD_DEV_PROPERTY_NAME, dilution.getStdDev(group));

                // TODO: factor this out in the nab data handlers
                props.put("VirusWellGroupName", group.getProperty("VirusWellGroupName"));
            }
            return results;
        }
        catch (FitFailedException e)
        {
            throw new ExperimentException(e.getMessage(), e);
        }
    }

    protected void saveICValue(String name, double icValue, DilutionSummary dilution,
                               Map<String, Object> results, StatsService.CurveFitType type) throws FitFailedException
    {
        String outOfRange = null;
        if (Double.NEGATIVE_INFINITY == icValue)
        {
            outOfRange = "<";
            icValue = dilution.getMinDilution(type);
        }
        else if (Double.POSITIVE_INFINITY == icValue)
        {
            outOfRange = ">";
            icValue = dilution.getMaxDilution(type);
        }

        // Issue 15590: don't attempt to store values that are NaN
        if (Double.isNaN(icValue))
            return;

        results.put(name, icValue);
        results.put(name + OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX, outOfRange);
    }

    protected ExperimentException createParseError(File dataFile, String msg)
    {
        return createParseError(dataFile, msg, null);
    }

    protected ExperimentException createParseError(File dataFile, String msg, @Nullable Exception cause)
    {
        StringBuilder fullMessage = new StringBuilder("There was an error parsing ");
        fullMessage.append(dataFile.getName()).append(".\n");
        if (!dataFile.getName().toLowerCase().endsWith(getPreferredDataFileExtension().toLowerCase()))
        {
            fullMessage.append("Your data file may not be in ").append(getPreferredDataFileExtension()).append(" format.\nError details: ");
        }
        if (msg != null)
        {
            fullMessage.append(msg);
            fullMessage.append("\n");
        }
        if (cause != null)
            return new ExperimentException(fullMessage.toString(), cause);
        else
            return new ExperimentException(fullMessage.toString());
    }

    protected abstract String getPreferredDataFileExtension();

    public static class MissingDataFileException extends ExperimentException
    {
        public MissingDataFileException(String message)
        {
            super(message);
        }
    }

    public static class MissingMaterialException extends ExperimentException
    {
        public MissingMaterialException(String message)
        {
            super(message);
        }
    }

    public DilutionAssayRun getAssayResults(ExpRun run, User user) throws ExperimentException
    {
        return getAssayResults(run, user, null);
    }

    public DilutionAssayRun getAssayResults(ExpRun run, User user, @Nullable StatsService.CurveFitType fit) throws ExperimentException
    {
        return getAssayResults(run, user, getDataFile(run), fit, true, false);
    }

    public File getDataFile(ExpRun run)
    {
        if (run == null)
            return null;
        List<? extends ExpData> outputDatas = run.getOutputDatas(getDataType());
        if (outputDatas == null || outputDatas.size() != 1)
            throw new IllegalStateException(getResourceName(run) + " runs should have a single data output.");
        File dataFile = outputDatas.get(0).getFile();
        if (!dataFile.exists())
            return null;
        return dataFile;
    }

    // public for upgrade
    public String getResourceName(ExpRun run)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        AssayProvider provider = AssayService.get().getProvider(protocol);

        return provider != null ? provider.getResourceName() : "Assay";
    }

    protected List<Plate> createPlates(File dataFile, PlateTemplate template) throws ExperimentException
    {
        return Collections.singletonList(PlateService.get().createPlate(template, getCellValues(dataFile, template), null, PlateService.NO_RUNID, 1));
    }

    protected List<Plate> createPlates(ExpRun run, PlateTemplate template, boolean recalcStats) throws ExperimentException
    {
        double[][] cellValues = new double[template.getRows()][template.getColumns()];
        boolean[][] excluded = new boolean[template.getRows()][template.getColumns()];
        List<WellDataRow> wellDataRows = DilutionManager.getWellDataRows(run);
        if (wellDataRows.isEmpty())
            throw new ExperimentException("Well data could not be found for run " + run.getName() + ". Run details are not available.");

        for (WellDataRow wellDataRow : wellDataRows)
        {
            cellValues[wellDataRow.getRow()][wellDataRow.getColumn()] = wellDataRow.getValue();
            excluded[wellDataRow.getRow()][wellDataRow.getColumn()] = wellDataRow.isExcluded();
        }

        Plate plate = PlateService.get().createPlate(template, cellValues, excluded, recalcStats ? PlateService.NO_RUNID : run.getRowId(), 1);
        return Collections.singletonList(plate);
    }

    protected abstract double[][] getCellValues(final File dataFile, PlateTemplate nabTemplate) throws ExperimentException;

    protected DilutionAssayRun getAssayResults(ExpRun run, User user, @Nullable File dataFile, @Nullable StatsService.CurveFitType fit,
                                               boolean useRunForPlates, boolean recalcStats) throws ExperimentException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        Container container = run.getContainer();
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(protocol);
        PlateTemplate nabTemplate = provider.getPlateTemplate(container, protocol);

        Map<String, DomainProperty> runProperties = getRunProperties(provider, protocol);

        Map<Integer, String> cutoffs = getCutoffFormats(protocol, run);

        // Attempt to populate the well data for dataFileUrls that may have been fixed since the new
        // WellData and DilutionData tables were added.
        synchronized (WELL_DATA_LOCK_OBJECT)
        {
            if (useRunForPlates && !isWellDataPopulated(run) && getDataFile(run) != null)
            {
                populateWellData(protocol, run, user);
            }
        }

        List<Plate> plates;
        if (useRunForPlates && isWellDataPopulated(run))
        {
            plates = createPlates(run, nabTemplate, recalcStats);
        }
        else
        {
            if (null == dataFile)
            {   // datafile wasn't passed in; try to get it
                dataFile = getDataFile(run);
                if (dataFile == null)
                    throw new MissingDataFileException(getResourceName(run) + " data file could not be found for run " +
                                                       run.getName() + ".  Deleted from file system?");
            }
            plates = createPlates(dataFile, nabTemplate);
        }

        // Copy all properties from the input materials on the appropriate sample wellgroups; the NAb data processing
        // code uses well-group properties internally.
        Map<ExpMaterial, List<WellGroup>> inputs = getMaterialWellGroupMapping(provider, plates, run.getMaterialInputs());

        DilutionAssayRun assay = createDilutionAssayRun(provider, run, plates, user, cutoffs, fit, runProperties, inputs);
        assay.setDataFile(dataFile);

        return assay;
    }

    /**
     * If specimens get more dilute as you move down or right on the plate, return true, else
     * it is assumed that specimens get more dilute as you move up or left on the plate.
     * @return
     */
    protected boolean isDilutionDownOrRight()
    {
        return false;
    }

    protected void applyDilution(List<? extends WellData> wells, ExpMaterial sampleInput, Map<String, DomainProperty> sampleProperties, boolean reverseDirection) throws ExperimentException
    {
        boolean first = true;
        Double dilution = (Double) sampleInput.getProperty(sampleProperties.get(DilutionAssayProvider.SAMPLE_INITIAL_DILUTION_PROPERTY_NAME));
        Double factor = (Double) sampleInput.getProperty(sampleProperties.get(DilutionAssayProvider.SAMPLE_DILUTION_FACTOR_PROPERTY_NAME));
        String methodString = (String) sampleInput.getProperty(sampleProperties.get(DilutionAssayProvider.SAMPLE_METHOD_PROPERTY_NAME));

        if (null == dilution || null == factor || null == methodString)
        {
            throw new ExperimentException("Initial Dilution, Dilution Factor and Method must all be specified.");
        }

        // Issue 21452: catch values for Method not in Enum
        if (!EnumUtils.isValidEnum(SampleInfoMethod.class, methodString))
        {
            throw new ExperimentException("Method value \"" + methodString + "\" is not an accepted value.");
        }
        SampleInfoMethod method = SampleInfoMethod.valueOf(methodString);

        // Single plate NAb run specimens get more dilute as you move up or left on the plate, while
        // high-throughput layouts get more dilute as you move down through the plates:
        boolean diluteDown = isDilutionDownOrRight();
        // The plate template may override the data handler's default dilution direction on a per-well group basis:
        if (reverseDirection)
            diluteDown = !diluteDown;
        // If we're diluting up, we start at the end of our list.  If down, we start at the beginning.
        int firstGroup = diluteDown ? 0 : wells.size() - 1;
        int incrementor = diluteDown ? 1 : -1;
        for (int wellIndex = firstGroup; wellIndex >= 0 && wellIndex < wells.size(); wellIndex = wellIndex + incrementor)
        {
            WellData well = wells.get(wellIndex);
            if (!first && factor != 0)
            {
                if (method == SampleInfoMethod.Dilution)
                    dilution *= factor;
                else if (method == SampleInfoMethod.Concentration)
                    dilution /= factor;
            }
            else
                first = false;
            well.setDilution(dilution);
        }
    }

    protected abstract void prepareWellGroups(List<WellGroup> wellgroups, ExpMaterial material, Map<String, DomainProperty> samplePropertyMap) throws ExperimentException;

    protected Map<ExpMaterial, List<WellGroup>> getMaterialWellGroupMapping(DilutionAssayProvider provider, List<Plate> plates, Map<ExpMaterial,String> sampleInputs)throws ExperimentException
    {
        Plate plate = plates.get(0);
        List<? extends WellGroup> wellgroups = plate.getWellGroups(WellGroup.Type.SPECIMEN);
        Map<String, ExpMaterial> nameToMaterial = new HashMap<>();
        for (Map.Entry<ExpMaterial,String> e : sampleInputs.entrySet())
        {
            ExpMaterial material = e.getKey();
            String wellGroup = e.getValue();
            nameToMaterial.put(wellGroup,material);
        }

        Map<ExpMaterial, List<WellGroup>> mapping = new HashMap<>();
        for (WellGroup wellgroup : wellgroups)
        {
            ExpMaterial material = nameToMaterial.get(wellgroup.getName());
            if (material == null)
                throw new MissingMaterialException("Wellgroup " + wellgroup.getName() + " does not have a matching input material. Was the plate template edited?");
            mapping.put(material, Collections.singletonList(wellgroup));
        }
        return mapping;
    }

    protected abstract DilutionAssayRun createDilutionAssayRun(DilutionAssayProvider provider, ExpRun run, List<Plate> plates, User user,
                                                               List<Integer> sortedCutoffs, StatsService.CurveFitType fit);

    public abstract Map<DilutionSummary, DilutionAssayRun> getDilutionSummaries(User user, StatsService.CurveFitType fit, int... dataObjectIds) throws ExperimentException, SQLException;

    protected DilutionDataFileParser getDataFileParser(ExpData data, File dataFile, ViewBackgroundInfo info)
    {
        return new DilutionDataFileParser(data, dataFile, info);
    }

    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        ExpRun run = data.getRun();
        ExpProtocol protocol = run.getProtocol();
        DilutionDataFileParser parser = getDataFileParser(data, dataFile, info);

        importRows(data, run, protocol, parser.getResults(), context.getUser());
    }

    public static final String POLY_SUFFIX = "_poly";
    public static final String OOR_SUFFIX = "OORIndicator";
    public static final String PL4_SUFFIX = "_4pl";
    public static final String PL5_SUFFIX = "_5pl";

    protected abstract void importRows(ExpData data, ExpRun run, ExpProtocol protocol, List<Map<String, Object>> rawData, User user) throws ExperimentException;

    protected ObjectProperty getObjectProperty(Container container, ExpProtocol protocol, String objectURI, String propertyName, Object value, Map<Integer, String> cutoffFormats)
    {
        if (isValidDataProperty(propertyName))
        {
            Pair<PropertyType, String> typeAndFormat = determinePropertyTypeAndFormat(propertyName, cutoffFormats);
            return getResultObjectProperty(container, protocol, objectURI, propertyName, value, typeAndFormat.getKey(), typeAndFormat.getValue());
        }
        return null;
    }

    public PropertyDescriptor getPropertyDescriptor(Container container, ExpProtocol protocol, String propertyName, Map<Integer, String> cutoffFormats)
    {
        if (isValidDataProperty(propertyName))
        {
            Pair<PropertyType, String> typeAndFormat = determinePropertyTypeAndFormat(propertyName, cutoffFormats);
            Lsid propertyURI = new Lsid(NAB_PROPERTY_LSID_PREFIX, protocol.getName(), propertyName);
            PropertyDescriptor pd = new PropertyDescriptor(propertyURI.toString(), typeAndFormat.getKey(), propertyName, propertyName, container);
            pd.setFormat(typeAndFormat.getValue());
            return pd;
        }
        return null;
    }

    public PropertyDescriptor getStringPropertyDescriptor(Container container, ExpProtocol protocol, String propertyName)
    {
        Lsid propertyURI = new Lsid(NAB_PROPERTY_LSID_PREFIX, protocol.getName(), propertyName);
        PropertyDescriptor pd = new PropertyDescriptor(propertyURI.toString(), PropertyType.STRING, propertyName, propertyName, container);
        pd.setFormat(null);
        return pd;
    }

    public boolean isValidDataProperty(String propertyName)
    {
        return DATA_ROW_LSID_PROPERTY.equals(propertyName) ||
                DILUTION_INPUT_MATERIAL_DATA_PROPERTY.equals(propertyName) ||
                WELLGROUP_NAME_PROPERTY.equals(propertyName) ||
                FIT_ERROR_PROPERTY.equals(propertyName) ||
                propertyName.startsWith(AUC_PREFIX) ||
                propertyName.startsWith(pAUC_PREFIX) ||
                propertyName.startsWith(CURVE_IC_PREFIX) ||
                propertyName.startsWith(POINT_IC_PREFIX);
    }

    public static Pair<PropertyType, String> determinePropertyTypeAndFormat(String propertyName, Map<Integer, String> cutoffFormats)
    {
        PropertyType type = PropertyType.STRING;
        String format = null;

        if (propertyName.equals(FIT_ERROR_PROPERTY) || propertyName.startsWith(STD_DEV_PROPERTY_NAME))
        {
            type = PropertyType.DOUBLE;
            format = "0.0";
        }
        else if (propertyName.startsWith(AUC_PREFIX) || propertyName.startsWith(pAUC_PREFIX))
        {
            type = PropertyType.DOUBLE;
            format = AUC_PROPERTY_FORMAT;
        }
        else if (propertyName.startsWith(CURVE_IC_PREFIX))
        {
            Integer cutoff = getCutoffFromPropertyName(propertyName);
            if (cutoff != null)
            {
                format = cutoffFormats.get(cutoff);
                if (null == format)
                    format = "0.000";
                type = PropertyType.DOUBLE;
            }
        }
        else if (propertyName.startsWith(POINT_IC_PREFIX))
        {
            Integer cutoff = getCutoffFromPropertyName(propertyName);
            if (cutoff != null)
            {
                format = cutoffFormats.get(cutoff);
                if (null == format)
                    format = "0.000";
                type = PropertyType.DOUBLE;
            }
        }
        return new Pair<>(type, format);
    }

    public Lsid getDataRowLSID(ExpData data, String wellGroupName, Map<PropertyDescriptor, Object> sampleProperties)
    {
        Lsid.LsidBuilder dataRowLsid = new Lsid.LsidBuilder(data.getLSID());
        dataRowLsid.setNamespacePrefix(_dataRowLsidPrefix);
        dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + wellGroupName);
        return dataRowLsid.build();
    }


    protected ObjectProperty getResultObjectProperty(Container container, ExpProtocol protocol, String objectURI,
                                                   String propertyName, Object value, PropertyType type, String format)
    {
        Lsid propertyURI = new Lsid(NAB_PROPERTY_LSID_PREFIX, protocol.getName(), propertyName);
        ObjectProperty prop = new ObjectProperty(objectURI, container, propertyURI.toString(), value, type, propertyName);
        prop.setFormat(format);
        return prop;
    }

    public static Map<Integer, String> getCutoffFormats(ExpProtocol protocol, ExpRun run)
    {
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(protocol);

        Map<String, DomainProperty> runProperties = new HashMap<>();
        for (DomainProperty column : provider.getRunDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);
        for (DomainProperty column : provider.getBatchDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);

        Map<Integer, String> cutoffs = new HashMap<>();
        for (String cutoffPropName : DilutionAssayProvider.CUTOFF_PROPERTIES)
        {
            DomainProperty cutoffProp = runProperties.get(cutoffPropName);
            if (cutoffProp != null)
            {
                Integer cutoff = (Integer) run.getProperty(cutoffProp);
                if (cutoff != null)
                    cutoffs.put(cutoff, cutoffProp.getPropertyDescriptor().getFormat());
            }
        }

        if (cutoffs.isEmpty())
        {
            cutoffs.put(50, "0.000");
            cutoffs.put(80, "0.000");
        }
        return cutoffs;
    }

    public ActionURL getContentURL(ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            ExpProtocol p = ExperimentService.get().getExpProtocol(protocol.getRowId());
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(data.getContainer(), p, run.getRowId());
        }
        return null;
    }

    public void deleteData(ExpData data, Container container, User user)
    {
        OntologyManager.deleteOntologyObject(data.getLSID(), container, true);
    }

    public String getPropertyName(String prefix, int cutoff, StatsService.CurveFitType type)
    {
        return getPropertyName(prefix + cutoff, type);
    }

    public String getPropertyName(String prefix, StatsService.CurveFitType type)
    {
        return prefix + "_" + type.getColSuffix();
    }

    public static Integer getCutoffFromPropertyName(String propertyName)
    {
        if (propertyName.startsWith(CURVE_IC_PREFIX) && !propertyName.endsWith(OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX))
        {
            // parse out the cutoff number
            int idx = propertyName.indexOf('_');
            String num;
            if (idx != -1)
                num = propertyName.substring(CURVE_IC_PREFIX.length(), propertyName.indexOf('_'));
            else
                num = propertyName.substring(CURVE_IC_PREFIX.length());

            return Integer.valueOf(num);
        }
        else if (propertyName.startsWith(POINT_IC_PREFIX) && !propertyName.endsWith(OORDisplayColumnFactory.OOR_INDICATOR_COLUMN_SUFFIX))
        {
            return Integer.valueOf(propertyName.substring(POINT_IC_PREFIX.length()));
        }
        return null;
    }

    /**
     * Helper to create the Lsid for the run->virusWellGroup relationship
     */
    public static Lsid createVirusWellGroupLsid(ExpData outputData, @Nullable String virusWellGroupName)
    {
        if (virusWellGroupName == null)
            virusWellGroupName = AbstractPlateBasedAssayProvider.VIRUS_NAME_PROPERTY_NAME;

        return new Lsid(outputData.getLSID() + "-" + virusWellGroupName);
    }

    protected DilutionAssayRun createDilutionAssayRun(DilutionAssayProvider provider, ExpRun run, List<Plate> plates, User user,
                                          Map<Integer, String> cutoffs, StatsService.CurveFitType fit,
                                          Map<String, DomainProperty> runProperties, Map<ExpMaterial, List<WellGroup>> inputs)
                                          throws ExperimentException
    {
        List<? extends DomainProperty> sampleProperties = provider.getSampleWellGroupDomain(run.getProtocol()).getProperties();
        Map<String, DomainProperty> samplePropertyMap = new HashMap<>();
        for (DomainProperty sampleProperty : sampleProperties)
            samplePropertyMap.put(sampleProperty.getName(), sampleProperty);
        for (Map.Entry<ExpMaterial, List<WellGroup>> entry : inputs.entrySet())
            prepareWellGroups(entry.getValue(), entry.getKey(), samplePropertyMap);

        List<Integer> sortedCutoffs = new ArrayList<>(cutoffs.keySet());
        Collections.sort(sortedCutoffs);

        DomainProperty curveFitPd = runProperties.get(DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME);
        if (fit == null)
        {
            fit = StatsService.CurveFitType.FIVE_PARAMETER;
            if (curveFitPd != null)
            {
                Object value = run.getProperty(curveFitPd);
                if (value != null)
                    fit = StatsService.CurveFitType.fromLabel((String) value);
            }
        }
        boolean lockAxes = false;
        DomainProperty lockAxesProperty = runProperties.get(DilutionAssayProvider.LOCK_AXES_PROPERTY_NAME);
        if (lockAxesProperty != null)
        {
            Boolean lock = (Boolean) run.getProperty(lockAxesProperty);
            if (lock != null)
                lockAxes = lock;
        }

        DilutionAssayRun assay = createDilutionAssayRun(provider, run, plates, user, sortedCutoffs, fit);
        assay.setCutoffFormats(cutoffs);
        assay.setMaterialWellGroupMapping(inputs);
        assay.setLockAxes(lockAxes);
        return assay;
    }

    private void populateWellData(ExpProtocol protocol, ExpRun run, User user) throws ExperimentException
    {
        try
        {
            Map<String, Pair<Integer, String>> wellGroupNameToNabSpecimen = new HashMap<>();
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("RunId"), run.getRowId());
            new TableSelector(DilutionManager.getTableInfoNAbSpecimen(), filter, null).forEach((NabSpecimen nabSpecimen) ->
                    wellGroupNameToNabSpecimen.put(nabSpecimen.getWellgroupName(), new Pair<>(nabSpecimen.getRowId(), nabSpecimen.getSpecimenLsid())), NabSpecimen.class);

            populateWellData(protocol, run, user, getCutoffFormats(protocol, run), wellGroupNameToNabSpecimen);
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
    }

    /**
     * Public for upgrade code
     *
     * @throws ExperimentException
     * @throws SQLException
     */
    public void populateWellData(ExpProtocol protocol, ExpRun run, User user, Map<Integer, String> cutoffs,
                                 Map<String, Pair<Integer, String>> wellgroupNameToNabSpecimen) throws ExperimentException, SQLException
    {
        _populateWellData(protocol, run, user, cutoffs, wellgroupNameToNabSpecimen, true, true, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Recalculate dilution and well data using the updated well exclusions and return the calculated data
     * through the passed in collections.
     */
    public void recalculateWellData(ExpProtocol protocol, ExpRun run, User user, List<Map<String, Object>> dilutionData, List<Map<String, Object>> wellData) throws ExperimentException, SQLException
    {
        Map<String, Pair<Integer, String>> wellGroupNameToNabSpecimen = new HashMap<>();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("RunId"), run.getRowId());
        new TableSelector(DilutionManager.getTableInfoNAbSpecimen(), filter, null).forEach((NabSpecimen nabSpecimen) ->
                wellGroupNameToNabSpecimen.put(nabSpecimen.getWellgroupName(), new Pair<>(nabSpecimen.getRowId(), nabSpecimen.getSpecimenLsid())), NabSpecimen.class);

        _populateWellData(protocol, run, user, getCutoffFormats(protocol, run), wellGroupNameToNabSpecimen, false, false, dilutionData, wellData);
    }

    /**
     * @param populatePlatesFromFile true to populate plate data from the data file associated with the run, otherwise will populate
     *                               from the well data table
     * @param commitData true to persist dilution and well level data
     * @param dilutionRows if commitData is false, then dilution data will be returned in this collection
     * @param wellRows if commitData is false, then well data will be returned in this collection
     * @throws ExperimentException
     * @throws SQLException
     */
    private void _populateWellData(ExpProtocol protocol, ExpRun run, User user, Map<Integer, String> cutoffs,
                                   Map<String, Pair<Integer, String>> wellgroupNameToNabSpecimen, boolean populatePlatesFromFile, boolean commitData,
                                   List<Map<String, Object>> dilutionRows, List<Map<String, Object>> wellRows) throws ExperimentException, SQLException
    {
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(protocol);
        if (null == provider)
            throw new IllegalStateException("Assay provider not found.");

        try (DbScope.Transaction transaction = DilutionManager.getSchema().getScope().ensureTransaction())
        {
            Map<String, DomainProperty> runProperties = getRunProperties(provider, protocol);
            StatsService.CurveFitType fit = StatsService.CurveFitType.FIVE_PARAMETER;
            DomainProperty curveFitPd = runProperties.get(DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME);
            if (curveFitPd != null)
            {
                Object value = run.getProperty(curveFitPd);
                if (value != null)
                    fit = StatsService.CurveFitType.fromLabel((String) value);
            }
            PlateTemplate nabTemplate = provider.getPlateTemplate(run.getContainer(), protocol);
            List<Plate> plates;

            if (populatePlatesFromFile)
            {
                File dataFile = getDataFile(run);
                if (null == dataFile)
                    throw new MissingDataFileException("Data file not found.");
                plates = createPlates(dataFile, nabTemplate);
            }
            else
            {
                plates = createPlates(run, nabTemplate, true);
            }
            Map<String, Plate> virusNameToPlateMap = new HashMap<>();
            for (Plate plate : plates)
            {
                virusNameToPlateMap.put((String) plate.getProperty(DilutionAssayProvider.VIRUS_NAME_PROPERTY_NAME), plate);
            }

            Map<String, Map<WellData, Integer>> virusToWellDataToDilutionDataMap = new HashMap<>();     // Map actual WellDatas (not groups)

            Map<ExpMaterial, List<WellGroup>> inputs = getMaterialWellGroupMapping(provider, plates, run.getMaterialInputs());

            DilutionAssayRun assay = createDilutionAssayRun(provider, run, plates, user, cutoffs, fit, runProperties, inputs);

            for (DilutionAssayRun.SampleResult result : assay.getSampleResults())
            {
                DilutionSummary summary = result.getDilutionSummary();
                String virusName = (String) summary.getFirstWellGroup().getProperty(DilutionAssayProvider.VIRUS_NAME_PROPERTY_NAME);
                if (!virusToWellDataToDilutionDataMap.containsKey(virusName))
                    virusToWellDataToDilutionDataMap.put(virusName, new HashMap<>());
                Map<WellData, Integer> wellDataToDilutionDataMap = virusToWellDataToDilutionDataMap.get(virusName);
                Plate plate = virusNameToPlateMap.get(virusName);

                Map<String, Object> dilutionRow = new HashMap<>();
                setDilutionDataFields(run, summary.getFirstWellGroup().getName(), plate.getPlateNumber(), dilutionRow);

                Pair<Integer, String> pair = wellgroupNameToNabSpecimen.get(summary.getFirstWellGroup().getName());
                dilutionRow.put("runDataId", pair.first);

                try
                {
                    dilutionRow.put("minDilution", summary.getMinDilution(fit));
                    dilutionRow.put("maxDilution", summary.getMaxDilution(fit));

                    Map<WellData, Integer> wellDataOrder = new HashMap<>();
                    List<WellData> dataList = summary.getWellData();        // use this to establish the order
                    for (int dataIndex = dataList.size() - 1; dataIndex >= 0; dataIndex--)
                    {
                        wellDataOrder.put(dataList.get(dataIndex), dataList.size() - dataIndex);
                    }

                    for (WellGroup sampleGroup : summary.getWellGroups())
                    {
                        for (WellGroup wellGroup : sampleGroup.getOverlappingGroups(WellGroup.Type.REPLICATE))
                        {
                            dilutionRow.put("dilution", wellGroup.getDilution());
                            dilutionRow.put("percentNeutralization", summary.getPercent(wellGroup));
                            dilutionRow.put("neutralizationPlusMinus", summary.getPlusMinus(wellGroup));
                            dilutionRow.put("plateNumber", wellGroup.getPlate().getPlateNumber());
                            setGroupStats(wellGroup, dilutionRow);
                            dilutionRow.put("replicateName", wellGroup.getName());

                            Integer order = wellDataOrder.get(wellGroup);
                            dilutionRow.put("dilutionOrder", null != order ? order : 0);
                            insertDilutionData(user, dilutionRow, wellGroup, wellDataToDilutionDataMap, commitData, dilutionRows);
                        }
                    }
                }
                catch (FitFailedException e)
                {
                    throw new RuntimeException(e);
                }
            }

            Map<String, Object> virusNames = assay.getVirusNames();
            for (Plate plate : plates)
            {
                String plateVirusName = (String) plate.getProperty(PlateBasedAssayProvider.VIRUS_NAME_PROPERTY_NAME);
                if (!virusToWellDataToDilutionDataMap.containsKey(plateVirusName))
                    virusToWellDataToDilutionDataMap.put(plateVirusName, new HashMap<>());
                Map<WellData, Integer> wellDataToDilutionDataMap = virusToWellDataToDilutionDataMap.get(plateVirusName);

                if (virusNames.isEmpty())
                {
                    WellGroup cellControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.CELL_CONTROL_SAMPLE);
                    Map<String, Object> cellControlRow = new HashMap<>();
                    setDilutionDataFields(run, cellControl.getName(), plate.getPlateNumber(), cellControlRow);
                    setGroupStats(cellControl, cellControlRow);
                    insertDilutionData(user, cellControlRow, cellControl, wellDataToDilutionDataMap, commitData, dilutionRows);

                    WellGroup virusControl = plate.getWellGroup(WellGroup.Type.CONTROL, DilutionManager.VIRUS_CONTROL_SAMPLE);
                    Map<String, Object> virusControlRow = new HashMap<>();
                    setDilutionDataFields(run, virusControl.getName(), plate.getPlateNumber(), virusControlRow);
                    setGroupStats(virusControl, virusControlRow);
                    insertDilutionData(user, virusControlRow, virusControl, wellDataToDilutionDataMap, commitData, dilutionRows);
                }
                else
                {
                    for (Map.Entry<String, Object> virusEntry : virusNames.entrySet())
                    {
                        WellGroup cellControl = assay.getCellControlWellGroup(plate, virusEntry.getKey());
                        if (null != cellControl)
                        {
                            Map<String, Object> cellControlRow = new HashMap<>();

                            setDilutionDataFields(run, cellControl.getName(), plate.getPlateNumber(), cellControlRow);
                            setGroupStats(cellControl, cellControlRow);
                            insertDilutionData(user, cellControlRow, cellControl, wellDataToDilutionDataMap, commitData, dilutionRows);
                        }

                        WellGroup virusControl = assay.getVirusControlWellGroup(plate, virusEntry.getKey());
                        if (null != virusControl)
                        {
                            Map<String, Object> virusControlRow = new HashMap<>();
                            setDilutionDataFields(run, virusControl.getName(), plate.getPlateNumber(), virusControlRow);
                            setGroupStats(virusControl, virusControlRow);
                            insertDilutionData(user, virusControlRow, virusControl, wellDataToDilutionDataMap, commitData, dilutionRows);
                        }
                    }
                }
            }

            // insert well data rows
            int plateNum = 0;
            for (Plate plate : plates)
            {
                plateNum += 1;      // 1-based
                Map<Well, Map<String, Object>> wellDataRows = new HashMap<>();
                String plateVirusName = (String) plate.getProperty(PlateBasedAssayProvider.VIRUS_NAME_PROPERTY_NAME);
                Map<WellData, Integer> wellDataToDilutionDataMap = virusToWellDataToDilutionDataMap.get(plateVirusName);

                // To calculate replicate number
                Map<String, Integer> replicateWellgroupToReplicateNumber = new HashMap<>();
                for (WellGroup wellGroup : plate.getWellGroups())
                {
                    for (Position position : wellGroup.getPositions())
                    {
                        Well well = plate.getWell(position.getRow(), position.getColumn());
                        if (!wellDataRows.containsKey(well))
                        {
                            Map<String, Object> wellDataRow = new HashMap<>();
                            wellDataRow.put("runId", run.getRowId());
                            wellDataRow.put("dilutionDataId", wellDataToDilutionDataMap.get(well));
                            wellDataRow.put("protocolId", protocol.getRowId());
                            wellDataRow.put("row", position.getRow());
                            wellDataRow.put("column", position.getColumn());
                            wellDataRow.put("value", well.getValue());
                            wellDataRow.put("container", run.getContainer());
                            wellDataRow.put("plateNumber", plateNum);
                            wellDataRow.put("plateVirusName", plateVirusName);
                            wellDataRows.put(well, wellDataRow);
                        }

                        Map<String, Object> wellDataRow = wellDataRows.get(well);
                        if (WellGroup.Type.CONTROL.equals(wellGroup.getType()))
                            wellDataRow.put("controlWellgroup", wellGroup.getName());
                        else if (WellGroup.Type.REPLICATE.equals(wellGroup.getType()))
                        {
                            wellDataRow.put("replicateWellgroup", wellGroup.getName());
                            Integer replicateNumber = replicateWellgroupToReplicateNumber.get(wellGroup.getName());
                            if (null == replicateNumber)
                                replicateNumber = 0;
                            replicateNumber += 1;
                            wellDataRow.put("replicateNumber", replicateNumber);
                            replicateWellgroupToReplicateNumber.put(wellGroup.getName(), replicateNumber);
                        }
                    }
                }

                for (WellGroup specimenWellgroup : plate.getWellGroups(WellGroup.Type.SPECIMEN))
                {
                    Set<WellGroup> virusWellgroups = specimenWellgroup.getOverlappingGroups(WellGroup.Type.VIRUS);
                    if (!virusWellgroups.isEmpty())
                    {
                        for (WellGroup virusWellgroup : virusWellgroups)
                        {
                            String specimenVirusLookupName = getSpecimenVirusWellgroupName(specimenWellgroup, virusWellgroup);
                            for (Position position : virusWellgroup.getPositions())
                            {
                                if (specimenWellgroup.contains(position))
                                {
                                    Well well = plate.getWell(position.getRow(), position.getColumn());
                                    Map<String, Object> wellDataRow = wellDataRows.get(well);
                                    setSpecimenFields(wellDataRow, specimenWellgroup.getName(), specimenVirusLookupName,
                                            wellgroupNameToNabSpecimen);
                                    wellDataRow.put("virusWellgroup", virusWellgroup.getName());
                                }
                            }
                        }
                    }
                    else
                    {
                        for (Position position : specimenWellgroup.getPositions())
                        {
                            Well well = plate.getWell(position.getRow(), position.getColumn());
                            setSpecimenFields(wellDataRows.get(well), specimenWellgroup.getName(), specimenWellgroup.getName(),
                                    wellgroupNameToNabSpecimen);
                        }
                    }
                }

                for (Map<String, Object> wellDataRow : wellDataRows.values())
                {
                    if (commitData)
                    {
                        validateWellDataRow(wellDataRow);
                        DilutionManager.insertWellDataRow(user, wellDataRow);
                    }
                    else
                        wellRows.add(wellDataRow);
                }
            }
            transaction.commit();
        }
    }

    /**
     * Helper to insert dilution data rows and track the inserted rowId
     *
     * @param dilutionRow map of row data
     * @param group sample or control well group the data is a member of
     * @param wellDataToDilutionDataMap map to track well data to inserted dilution row id
     * @param commitData flag to determine whether the insert is performed or if the dilution row is returned
     * @param dilutionDataRows list to store dilution data rows if commitData is set to false
     */
    private void insertDilutionData(User user, Map<String, Object> dilutionRow, WellGroup group, Map<WellData, Integer> wellDataToDilutionDataMap,
                                    boolean commitData, List<Map<String, Object>> dilutionDataRows) throws SQLException
    {
        if (commitData)
        {
            int rowId = DilutionManager.insertDilutionDataRow(user, dilutionRow);
            for (WellData wellData : group.getWellData(false))
                wellDataToDilutionDataMap.put(wellData, rowId);
        }
        else
            dilutionDataRows.add(new CaseInsensitiveHashMap<>(dilutionRow));

    }

    private void setSpecimenFields(Map<String, Object> wellDataRow, String wellGroupName, String wellGroupLookupName,
                                   Map<String, Pair<Integer, String>> wellgroupNameToNabSpecimen)
    {
        Pair<Integer, String> pair = wellgroupNameToNabSpecimen.get(wellGroupLookupName);
        wellDataRow.put("specimenWellgroup", wellGroupName);
        wellDataRow.put("runDataId", pair.first);
        wellDataRow.put("specimenLsid", pair.second);
    }

    protected Map<String, DomainProperty> getRunProperties(DilutionAssayProvider provider, ExpProtocol protocol)
    {
        Map<String, DomainProperty> runProperties = new HashMap<>();
        for (DomainProperty column : provider.getRunDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);
        for (DomainProperty column : provider.getBatchDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);
        return runProperties;
    }

    protected void setGroupStats(WellGroup group, Map<String, Object> properties)
    {
        properties.put("min", group.getMin());
        properties.put("max", group.getMax());
        properties.put("mean", group.getMean());
        properties.put("stddev", group.getStdDev());
    }

    protected void setDilutionDataFields(ExpRun run, String wellgroupName, int plateNumber, Map<String, Object> dilutionRow)
    {
        dilutionRow.put("wellgroupName", wellgroupName);
        dilutionRow.put("container", run.getContainer());
        dilutionRow.put("runId", run.getRowId());
        dilutionRow.put("protocolId", run.getProtocol().getRowId());
        dilutionRow.put("plateNumber", plateNumber);
    }

    private void validateWellDataRow(Map<String, Object> wellDataRow)
    {
        if (null == wellDataRow.get("runId") ||
            null == wellDataRow.get("protocolId") ||
            null == wellDataRow.get("container"))
        {
            throw new IllegalStateException("Well data row is missing necessary field.");
        }
    }

    public static String getSpecimenVirusWellgroupName(@NotNull WellGroup specimenGroup, @Nullable WellGroup virusGroup)
    {
        return specimenGroup.getName() + (null != virusGroup ? ":" + virusGroup.getName() : "");
    }

    public static String getWellgroupNameVirusNameCombo(String wellgroupName, String virusName)
    {
        return wellgroupName + ":" + virusName;
    }

    public static boolean isWellDataPopulated(ExpRun run)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(run.getContainer());
        filter.addCondition(FieldKey.fromString("runId"), run.getRowId());
        return (new TableSelector(DilutionManager.getTableInfoWellData(), filter, null).getRowCount() > 0);
    }
}
