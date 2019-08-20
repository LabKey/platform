/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.study.controllers;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import gwt.client.org.labkey.study.dataset.client.DatasetService;
import gwt.client.org.labkey.study.dataset.client.model.GWTDataset;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.util.*;

/**
 * User: jgarms
 */
@SuppressWarnings("unchecked")
public class DatasetServiceImpl extends DomainEditorServiceBase implements DatasetService
{

    private final StudyImpl study;
    private final StudyManager studyManager;

    public DatasetServiceImpl(ViewContext context, StudyImpl study, StudyManager studyManager)
    {
        super(context);
        this.study = study;
        this.studyManager = studyManager;
    }


    public GWTDataset getDataset(int id)
    {
        try
        {
            DatasetDefinition dd = study.getDataset(id);
            if (null == dd)
                return null;
            GWTDataset ds = new GWTDataset();
            PropertyUtils.copyProperties(ds, dd);
            ds.setDatasetId(dd.getDatasetId()); // upper/lowercase problem
            ds.setKeyPropertyManaged(dd.getKeyManagementType() != Dataset.KeyManagementType.None);

            // Use Time Key Field
            if (dd.getUseTimeKeyField())
            {
                ds.setKeyPropertyName(GWTDataset.TIME_KEY_FIELD_KEY);
            }

            if (study.getContainer().isProject() && study.isDataspaceStudy())
            {
                ds.setDefinitionShared(true);
                if (study.getShareVisitDefinitions())
                    ds.setVisitMapShared(true);
            }

            List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
            Map<String, String> cohortMap = new HashMap<>();
            if (cohorts != null && cohorts.size() > 0)
            {
                cohortMap.put("All", "");
                for (CohortImpl cohort : cohorts)
                    cohortMap.put(cohort.getLabel(), String.valueOf(cohort.getRowId()));
            }
            ds.setCohortMap(cohortMap);

            Map<String, String> visitDateMap = new HashMap<>();
            TableInfo tinfo = dd.getTableInfo(getUser(), false);
            for (ColumnInfo col : tinfo.getColumns())
            {
                if (!Date.class.isAssignableFrom(col.getJavaClass()))
                    continue;
                if (col.getName().equalsIgnoreCase("visitdate"))
                    continue;
                if (col.getName().equalsIgnoreCase("modified"))
                    continue;
                if (col.getName().equalsIgnoreCase("created"))
                    continue;
                if (visitDateMap.isEmpty())
                    visitDateMap.put("", "");
                visitDateMap.put(col.getName(), col.getName());
            }
            ds.setVisitDateMap(visitDateMap);


            ExpProtocol protocol = dd.getAssayProtocol();
            if (protocol != null)
            {
                ds.setSourceAssayName(protocol.getName());

                ActionURL assayURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(protocol.getContainer(), protocol);
                ds.setSourceAssayURL(assayURL.getLocalURIString());
            }


            return ds;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }


    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain update)
    {
        assert orig.getDomainURI().equals(update.getDomainURI());
        List<String> errors = new ArrayList<>();

        if (!checkCanUpdate(ds, errors))
            return errors;

        Domain d = PropertyService.get().getDomain(getContainer(), update.getDomainURI());
        if (null == d)
        {
            errors.add("Domain not found: " + update.getDomainURI());
            return errors;
        }

        if (!ds.getTypeURI().equals(orig.getDomainURI()) ||
            !ds.getTypeURI().equals(update.getDomainURI()))
        {
            errors.add("Illegal Argument");
            return errors;
        }

        // Remove any fields that are duplicates of the default dataset fields.
        // e.g. participantid, etc.

        List<GWTPropertyDescriptor> updatedProps = update.getFields();
        for (Iterator<GWTPropertyDescriptor> iter = updatedProps.iterator(); iter.hasNext();)
        {
            GWTPropertyDescriptor prop = iter.next();
            if (DatasetDefinition.isDefaultFieldName(prop.getName(), study))
                iter.remove();
            else if (DatasetDomainKind.DATE.equalsIgnoreCase(prop.getName()))
                prop.setRangeURI(PropertyType.DATE_TIME.getTypeUri());
        }
        update.setFields(updatedProps);

        try (DbScope.Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
        {
            errors = updateDomainDescriptor(orig, update);
            if (errors.isEmpty())
            {
                errors = updateDataset(ds, orig.getDomainURI());
                if (errors.isEmpty())
                {
                    transaction.commit();
                }
            }
        }


        return errors;
    }

    private List updateDataset(GWTDataset ds, String domainURI)
    {
        List<String> errors = new ArrayList<>();
        try
        {
            // CONSIDER: optimistic concurrency validate against current
            // validate that this smells right
            DatasetDefinition def = study.getDataset(ds.getDatasetId());
            if (null == def)
            {
                errors.add("Dataset not found");
                return errors;
            }

            if (ds.getDemographicData() && !def.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(def))
            {
                errors.add("This dataset currently contains more than one row of data per " +  StudyService.get().getSubjectNounSingular(getContainer()) +
                        ". Demographic data includes one row of data per " + StudyService.get().getSubjectNounSingular(getContainer()) + ".");
                return errors;
            }

            if (ds.getDemographicData())
            {
                ds.setKeyPropertyName(null);
                ds.setKeyPropertyManaged(false);
            }

            /* IGNORE illegal shareDataset values */
            if (!study.getShareVisitDefinitions())
                ds.setDataSharing("NONE");

            // Default is no key management
            Dataset.KeyManagementType keyType = Dataset.KeyManagementType.None;
            String keyPropertyName = null;
            boolean useTimeKeyField = false;

            DatasetDefinition updated = def.createMutable();
            // Clear the category ID so that it gets regenerated based on the new string - see issue 19649
            updated.setCategoryId(null);

            // Use Time as Key Field
            if (GWTDataset.TIME_KEY_FIELD_KEY.equalsIgnoreCase(ds.getKeyPropertyName()))
            {
                ds.setKeyPropertyName(null);
                useTimeKeyField = true;
            }
            BeanUtils.copyProperties(updated, ds);

            if (ds.getKeyPropertyName() != null)
            {
                Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);

                for (DomainProperty dp : domain.getProperties())
                {
                    if (dp.getName().equalsIgnoreCase(ds.getKeyPropertyName()))
                    {
                        keyPropertyName = dp.getName();
                        // Be sure that the user really wants a managed key, not just that disabled select box still had a value
                        if (ds.getKeyPropertyManaged())
                        {
                            if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.INTEGER || dp.getPropertyDescriptor().getPropertyType() == PropertyType.DOUBLE)
                            {
                                // Number fields must be RowIds
                                keyType = Dataset.KeyManagementType.RowId;
                            }
                            else if (dp.getPropertyDescriptor().getPropertyType() == PropertyType.STRING)
                            {
                                // Strings can be managed as GUIDs
                                keyType = Dataset.KeyManagementType.GUID;
                            }
                            else
                            {
                                throw new IllegalStateException("Unsupported column type for managed keys: " + dp.getPropertyDescriptor().getPropertyType());
                            }
                        }
                        break;
                    }
                }
            }
            updated.setKeyPropertyName(keyPropertyName);
            updated.setKeyManagementType(keyType);
            updated.setUseTimeKeyField(useTimeKeyField);

            if (!def.getLabel().equals(updated.getLabel()))
            {
                Dataset existing = studyManager.getDatasetDefinitionByLabel(study, updated.getLabel());
                if (existing != null && existing.getDatasetId() != ds.getDatasetId())
                {
                    errors.add("A Dataset already exists with the label \"" + updated.getLabel() +"\"");
                    return errors;
                }
            }

            if (!def.getName().equals(updated.getName()))
            {
                // issue 17766: check if dataset or query exist with this name
                Dataset existing = studyManager.getDatasetDefinitionByName(study, updated.getName());
                if ((null != existing && existing.getDatasetId() != ds.getDatasetId())
                    || null != QueryService.get().getQueryDef(getUser(), getContainer(), "study", updated.getName()))
                {
                    errors.add("A Dataset or Query already exists with the name \"" + updated.getName() +"\"");
                    return errors;
                }
            }

            studyManager.updateDatasetDefinition(getUser(), updated, errors);

            return errors;
        }
        catch (RuntimeSQLException e)
        {
            errors.add("Additional key column must have unique values.");
            return errors;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }

    private boolean checkCanUpdate(GWTDataset ds, List<String> errors)
    {
        if (!getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            errors.add("Unauthorized");
            return false;
        }
        Study study = StudyService.get().getStudy(getContainer());
        if (null == study)
        {
            errors.add("Study not found in current container");
            return false;
        }
        DatasetDefinition def = (DatasetDefinition)study.getDataset(ds.getDatasetId());
        if (null == def)
        {
            errors.add("Dataset not found");
            return false;
        }
        if (!def.canUpdateDefinition(getUser()))
        {
            errors.add("Shared dataset can not be edited in this folder.");
            return false;
        }
        return true;
    }

    @Override
    public GWTDomain getDomainDescriptor(String typeURI)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI);
        domain.setDefaultValueOptions(new DefaultValueType[]
                { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
        return domain;
    }
}
