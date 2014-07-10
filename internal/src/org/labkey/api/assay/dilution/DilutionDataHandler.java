/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.OORDisplayColumnFactory;
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
import org.labkey.api.security.User;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 5/8/13
 */
public abstract class DilutionDataHandler extends AbstractExperimentDataHandler
{
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
            try
            {
                ExpRun run = _data.getRun();
                DilutionAssayRun assayResults = getAssayResults(run, _info.getUser(), _dataFile, null);
                List<Map<String, Object>> results = new ArrayList<>();

                for (int summaryIndex = 0; summaryIndex < assayResults.getSummaries().length; summaryIndex++)
                {
                    DilutionSummary dilution = assayResults.getSummaries()[summaryIndex];
                    WellGroup group = dilution.getFirstWellGroup();
                    ExpMaterial sampleInput = assayResults.getMaterial(group);

                    Map<String, Object> props = new HashMap<>();
                    results.add(props);

                    // generate curve ICs and AUCs for each curve fit type
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
            results.put(name + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX, outOfRange);
        }
    }

    protected ExperimentException createParseError(File dataFile, String msg) throws ExperimentException
    {
        return createParseError(dataFile, msg, null);
    }

    protected ExperimentException createParseError(File dataFile, String msg, @Nullable Exception cause) throws ExperimentException
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
        File dataFile = getDataFile(run);
        if (dataFile == null)
            throw new MissingDataFileException(getResourceName(run) +  " data file could not be found for run " + run.getName() + ".  Deleted from file system?");
        return getAssayResults(run, user, dataFile, fit);
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

    protected String getResourceName(ExpRun run)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        AssayProvider provider = AssayService.get().getProvider(protocol);

        return provider != null ? provider.getResourceName() : "Assay";
    }

    protected abstract List<Plate> createPlates(File dataFile, PlateTemplate template) throws ExperimentException;

    protected DilutionAssayRun getAssayResults(ExpRun run, User user, File dataFile, @Nullable StatsService.CurveFitType fit) throws ExperimentException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(run.getProtocol().getLSID());
        Container container = run.getContainer();
        DilutionAssayProvider provider = (DilutionAssayProvider) AssayService.get().getProvider(protocol);
        PlateTemplate nabTemplate = provider.getPlateTemplate(container, protocol);

        Map<String, DomainProperty> runProperties = new HashMap<>();
        for (DomainProperty column : provider.getRunDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);
        for (DomainProperty column : provider.getBatchDomain(protocol).getProperties())
            runProperties.put(column.getName(), column);

        Map<Integer, String> cutoffs = getCutoffFormats(protocol, run);
        List<Integer> sortedCutoffs = new ArrayList<>(cutoffs.keySet());
        Collections.sort(sortedCutoffs);

        List<Plate> plates = createPlates(dataFile, nabTemplate);

        // Copy all properties from the input materials on the appropriate sample wellgroups; the NAb data processing
        // code uses well-group properties internally.
        Collection<ExpMaterial> sampleInputs = run.getMaterialInputs().keySet();
        Map<ExpMaterial, List<WellGroup>> inputs = getMaterialWellGroupMapping(provider, plates, sampleInputs);

        List<? extends DomainProperty> sampleProperties = provider.getSampleWellGroupDomain(protocol).getProperties();
        Map<String, DomainProperty> samplePropertyMap = new HashMap<>();
        for (DomainProperty sampleProperty : sampleProperties)
            samplePropertyMap.put(sampleProperty.getName(), sampleProperty);
        for (Map.Entry<ExpMaterial, List<WellGroup>> entry : inputs.entrySet())
            prepareWellGroups(entry.getValue(), entry.getKey(), samplePropertyMap);

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
        assay.setDataFile(dataFile);
        assay.setLockAxes(lockAxes);
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

        SampleInfo.Method method = SampleInfo.Method.valueOf(methodString);
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
            if (!first)
            {
                if (method == SampleInfo.Method.Dilution)
                    dilution *= factor;
                else if (method == SampleInfo.Method.Concentration)
                    dilution /= factor;
            }
            else
                first = false;
            well.setDilution(dilution);
        }
    }

    protected abstract void prepareWellGroups(List<WellGroup> wellgroups, ExpMaterial material, Map<String, DomainProperty> samplePropertyMap) throws ExperimentException;

    protected Map<ExpMaterial, List<WellGroup>> getMaterialWellGroupMapping(DilutionAssayProvider provider, List<Plate> plates, Collection<ExpMaterial> sampleInputs)throws ExperimentException
    {
        Plate plate = plates.get(0);
        List<? extends WellGroup> wellgroups = plate.getWellGroups(WellGroup.Type.SPECIMEN);
        Map<String, ExpMaterial> nameToMaterial = new HashMap<>();
        for (ExpMaterial material : sampleInputs)
            nameToMaterial.put(material.getName(), material);

        Map<ExpMaterial, List<WellGroup>> mapping = new HashMap<>();
        for (WellGroup wellgroup : wellgroups)
        {
            ExpMaterial material = nameToMaterial.get(wellgroup.getName());
            if (material == null)
                throw new MissingMaterialException("Wellgroup " + wellgroup.getName() + " does not have a matching input material. Was plate template edited?");
            mapping.put(material, Collections.singletonList(wellgroup));
        }
        return mapping;
    }

    protected abstract DilutionAssayRun createDilutionAssayRun(DilutionAssayProvider provider, ExpRun run, List<Plate> plates, User user, List<Integer> sortedCutoffs, StatsService.CurveFitType fit);

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

        importRows(data, run, protocol, parser.getResults());
    }

    public static final String POLY_SUFFIX = "_poly";
    public static final String OOR_SUFFIX = "OORIndicator";
    public static final String PL4_SUFFIX = "_4pl";
    public static final String PL5_SUFFIX = "_5pl";

    protected abstract void importRows(ExpData data, ExpRun run, ExpProtocol protocol, List<Map<String, Object>> rawData) throws ExperimentException;

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
            PropertyDescriptor pd = new PropertyDescriptor(propertyURI.toString(), typeAndFormat.getKey().getTypeUri(), propertyName, propertyName, container);
            pd.setFormat(typeAndFormat.getValue());
            return pd;
        }
        return null;
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
        Lsid dataRowLsid = new Lsid(data.getLSID());
        dataRowLsid.setNamespacePrefix(_dataRowLsidPrefix);
        dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + wellGroupName);
        return dataRowLsid;
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

    public void runMoved(ExpData newData, Container container, Container targetContainer, String oldRunLSID, String newRunLSID, User user, int oldDataRowID) throws ExperimentException
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }

    public ActionURL getContentURL(Container container, ExpData data)
    {
        ExpRun run = data.getRun();
        if (run != null)
        {
            ExpProtocol protocol = run.getProtocol();
            ExpProtocol p = ExperimentService.get().getExpProtocol(protocol.getRowId());
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(container, p, run.getRowId());
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
        if (propertyName.startsWith(CURVE_IC_PREFIX) && !propertyName.endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX))
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
        else if (propertyName.startsWith(POINT_IC_PREFIX) && !propertyName.endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX))
        {
            return Integer.valueOf(propertyName.substring(POINT_IC_PREFIX.length()));
        }
        return null;
    }

}
