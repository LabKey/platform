/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.study.model;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.labkey.study.model.SequenceNumImportHelper.parseDateTime;

public class DatasetLsidImportHelper
{
    private final DatasetDefinition _datasetDefinition;
    private final Map<String, String> _map = new HashMap<>();
    private final Converter _convertDate = ConvertUtils.lookup(Date.class);

    public DatasetLsidImportHelper(DatasetDefinition datasetDefinition)
    {
        _datasetDefinition = datasetDefinition;
    }

    public Callable<Object> getCallable(
            @NotNull final DataIterator it,
            @Nullable final Integer indexPtidOutput,
            @Nullable final Integer indexSequenceNum,
            @Nullable final Integer dateIndex,
            @Nullable final Integer keyIndex,
            @Nullable final Integer indexContainer
    )
    {
        return () -> {
            String participantId = indexPtidOutput == null ? "" : DatasetDataIteratorBuilder.getOutputString(it, indexPtidOutput);
            Double sequenceNum = DatasetDataIteratorBuilder.getOutputDouble(it, indexSequenceNum);
            Date visitDate = getOutputDate(it, dateIndex);
            Object key = keyIndex == null ? null : it.get(keyIndex);
            String container = DatasetDataIteratorBuilder.getOutputString(it, indexContainer);

            return translateLsid(participantId, sequenceNum, visitDate, key, container);
        };
    }

    private Date getOutputDate(DataIterator it, Integer i)
    {
        if (i == null)
            return null;

        Object o = it.get(i);
        return _convertDate.convert(Date.class, o);
    }

    private String getURNPrefix(String container)
    {
        Container c = null;
        String entityId;
        if (_datasetDefinition.isShared() && _datasetDefinition.getDataSharingEnum() == DatasetDefinition.DataSharing.PTID)
        {
            c = _datasetDefinition.getDefinitionContainer();
            entityId = c.getId();
        }
        else if (null == container)
        {
            c = _datasetDefinition.getContainer();
            entityId = c.getId();
        }
        else
        {
            entityId = container;
        }
        String urn = _map.get(entityId);
        if (null != urn)
            return urn;
        if (null == c)
            c = ContainerManager.getForId(entityId);

        String id = null == c ? entityId : String.valueOf(c.getRowId());
        urn = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + id + ":" + _datasetDefinition.getDatasetId() + ".";
        _map.put(entityId, urn);

        return urn;
    }

    // MUST match what is produced by DatasetDefinition.generateLSIDSQL
    public String translateLsid(
            String participantId,
            Double sequenceNum,
            Date date,
            @Nullable Object keyValue,      // the managed or additional key value for the dataset
            @Nullable String container      // alternate container to use, otherwise the dataset definition based container is used
    )
    {
        StringBuilder sb = new StringBuilder(getURNPrefix(container));

        sb.append(participantId.trim());
        if (!_datasetDefinition.isDemographicData())
        {
            sb.append(".");
            if (sequenceNum != null)
                sb.append(DatasetDataIteratorBuilder.SEQUENCE_NUM_FORMAT.format(sequenceNum));

            if (!_datasetDefinition.getStudy().getTimepointType().isVisitBased() && _datasetDefinition.getUseTimeKeyField())
            {
                if (date == null)
                    throw new IllegalArgumentException(String.format("Visit date is required to generate the LSID for this dataset (%s)", _datasetDefinition.getName()));
                sb.append(".").append(String.format("%tH%tM%tS", date, date, date));
            }
            else if (keyValue != null)
            {
                sb.append(".").append(keyValue);
            }
        }
        return sb.toString();
    }

    public final static class TestCase extends Assert
    {
        private StudyImpl _visitStudy;
        private StudyImpl _dateStudy;

        @Before
        public void initialize()
        {
            // create studies
            _visitStudy = createStudy(TimepointType.VISIT, "Visit Study");
            _dateStudy = createStudy(TimepointType.DATE, "Date Study");
        }

        private StudyImpl createStudy(TimepointType timepointType, String name)
        {
            TestContext context = TestContext.get();
            Container c = ContainerManager.createContainer(JunitUtil.getTestContainer(), GUID.makeHash(), context.getUser());
            StudyImpl study = new StudyImpl(c, name);
            study.setTimepointType(timepointType);
            study.setStartDate(new Date(DateUtil.parseDateTime("2025-04-01")));

            return StudyManager.getInstance().createTestStudy(context.getUser(), study);
        }

        @After
        public void tearDown()
        {
            JunitUtil.deleteTestContainer();
        }

        @Test
        public void testVistBased() throws ValidationException
        {
            CaseInsensitiveHashMap<Double> map = new CaseInsensitiveHashMap<>();
            map.put("Enrollment", 1.0000);
            map.put("SR", 100.0);
            map.put("SR-1", 9999.0);
            SequenceNumImportHelper seq = new SequenceNumImportHelper(
                    TimepointType.VISIT,
                    parseDateTime("1 Jan 2025 1:00pm"),
                    null,
                    map,
                    null
            );

            // demographics dataset
            DatasetLsidImportHelper lsid = new DatasetLsidImportHelper(createDatasetDef(_visitStudy, true, false));
            String demographics = lsid.translateLsid("111", seq.translateSequenceNum(1.0, null), new Date(), null, null);
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(1.00000, null), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum("Enrollment", "2025-1-1"), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(1, null), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(2.0, null), null, null, null));
            assertEquals(demographics, lsid.translateLsid(" 111 ", seq.translateSequenceNum(2.0, null), null, null, null));
            assertNotEquals(demographics, lsid.translateLsid("222", seq.translateSequenceNum("Enrollment",  "2025-1-1"), null, null, null));
            assertNotEquals(demographics, lsid.translateLsid("222", seq.translateSequenceNum(1.00000, null), new Date(), null, null));

            // non-demographics dataset
            lsid = new DatasetLsidImportHelper(createDatasetDef(_visitStudy, false, false));
            String nonDemographics = lsid.translateLsid("222", seq.translateSequenceNum(1.0, null), null, null, null);
            assertEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(1.0000000, null), null, null, null));
            assertEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("1.0", null), null, null, null));
            assertEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("Enrollment", "2025-4-1"), null, null, null));
            assertEquals(nonDemographics, lsid.translateLsid(" 222\t", seq.translateSequenceNum("Enrollment", parseDateTime("2025/4/1")), new Date(), null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("Enrollment", null), new Date(), 123, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(100.0, null), null, null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("SR", null), null, null, null));

            // additional key field
            String additionalKey = lsid.translateLsid("333", seq.translateSequenceNum(9999.0, null), null, "abc", null);
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(9999.0, null), null, "abc", null));
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(9999, null), new Date(), "abc", null));
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum("9999.00000", null), new Date(), "abc", null));
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum("SR-1", null), null, "abc", null));
            additionalKey = lsid.translateLsid("333", seq.translateSequenceNum(9999.0, null), null, 333.00, null);
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(9999.0, null), null, 333.0000, null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(9999, null), new Date(), "abc", null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum("Enrollment", null), null, 333.00, null));
        }

        @Test
        public void testDateBased() throws ValidationException
        {
            SequenceNumImportHelper seq = new SequenceNumImportHelper(
                    TimepointType.DATE,
                    parseDateTime("2025-4-1 12:00"),
                    null,
                    Collections.emptyMap(),
                    null
            );

            // demographics dataset
            DatasetLsidImportHelper lsid = new DatasetLsidImportHelper(createDatasetDef(_dateStudy, true, false));
            String demographics = lsid.translateLsid("111", seq.translateSequenceNum(1.0, null), new Date(), null, null);
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(1.00000, null), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(null, "2025-1-1"), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(null, null), new Date(), null, null));
            assertEquals(demographics, lsid.translateLsid("111", seq.translateSequenceNum(null, null), null, null, null));
            assertEquals(demographics, lsid.translateLsid(" 111 ", seq.translateSequenceNum(2.0, null), null, null, null));
            assertNotEquals(demographics, lsid.translateLsid("222", seq.translateSequenceNum("Enrollment",  "2025-1-1"), null, null, null));
            assertNotEquals(demographics, lsid.translateLsid("222", seq.translateSequenceNum(1.00000, null), new Date(), null, null));

            // non-demographics dataset
            lsid = new DatasetLsidImportHelper(createDatasetDef(_dateStudy, false, false));
            String nonDemographics = lsid.translateLsid("222", seq.translateSequenceNum(null, parseDateTime("2025-4-15")), null, null, null);
            assertEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(null, "2025-4-15"), null, null, null));
            assertEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(null, parseDateTime("15 April 2025 1:00pm")), null, null, null));
            assertEquals(nonDemographics, lsid.translateLsid("222\t", seq.translateSequenceNum(null, "2025/4/15"), new Date(), null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(null, "2025-4-16"), null, null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum(null, "15 April 2025 1:00pm"), null, null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("1.0", "15 April 2025"), null, null, null));
            assertNotEquals(nonDemographics, lsid.translateLsid("222", seq.translateSequenceNum("Enrollment", parseDateTime("2025/4/1")), new Date(), null, null));

            // alternate key field
            lsid = new DatasetLsidImportHelper(createDatasetDef(_dateStudy, false, false));
            String additionalKey = lsid.translateLsid("333", seq.translateSequenceNum(null, parseDateTime("2025-4-15 1:00pm")), null, "abc", null);
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "15 April 2025"), null, "abc", null));
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "2025-4-15"), null, "abc", null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, parseDateTime("2025-4-16")), null, "abc", null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "2025-4-15"), null, "bca", null));
            additionalKey = lsid.translateLsid("333", seq.translateSequenceNum(null, parseDateTime("2025-4-15 1:00pm")), null, 12.0, null);
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "15 April 2025"), null, "12.0", null));
            assertEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "2025-4-15"), null, 12.0000, null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, null), null, 12.0, null));
            assertNotEquals(additionalKey, lsid.translateLsid("333", seq.translateSequenceNum(null, "2025-4-15"), null, "12", null));

            // with time portion of date field
            lsid = new DatasetLsidImportHelper(createDatasetDef(_dateStudy, false, true));
            additionalKey = lsid.translateLsid("444", seq.translateSequenceNum(null, "2025-1-1"), parseDateTime("2025-4-15 11:15pm"), "abc", null);
            assertEquals(additionalKey, lsid.translateLsid("444", seq.translateSequenceNum(null, "1 Jan 2025"), new Date(DateUtil.parseDateTime("14 April 2025 11:15pm")), "abc", null));
            assertEquals(additionalKey, lsid.translateLsid("444", seq.translateSequenceNum(null, "1 Jan 2025"), new Date(DateUtil.parseDateTime("14 April 2025 11:15pm")), null, null));
            assertEquals(additionalKey, lsid.translateLsid("444", seq.translateSequenceNum(null, "2025-1-1"), new Date(DateUtil.parseDateTime("14 April 2025 11:15pm")), 12.00, null));
            assertNotEquals(additionalKey, lsid.translateLsid("444", seq.translateSequenceNum(null, "2025-4-15"), new Date(), "abc", null));
        }

        private DatasetDefinition createDatasetDef(StudyImpl study, boolean demographics, boolean useTimeKeyField)
        {
            DatasetDefinition def = new DatasetDefinition(study, 1);
            def.setDemographicData(demographics);
            def.setUseTimeKeyField(useTimeKeyField);

            return def;
        }
    }
}
