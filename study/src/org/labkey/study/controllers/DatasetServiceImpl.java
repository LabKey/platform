/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.ACL;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reader.NewTabLoader;
import org.labkey.study.dataset.client.DatasetService;
import org.labkey.study.dataset.client.model.GWTDataset;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jgarms
 */
@SuppressWarnings("unchecked")
class DatasetServiceImpl extends DomainEditorServiceBase implements DatasetService
{

    private final Study study;
    private final StudyManager studyManager;

    public DatasetServiceImpl(ViewContext context, Study study, StudyManager studyManager)
    {
        super(context);
        this.study = study;
        this.studyManager = studyManager;
    }


    public GWTDataset getDataset(int id)
    {
        try
        {
            DataSetDefinition dd = study.getDataSet(id);
            if (null == dd)
                return null;
            GWTDataset ds = new GWTDataset();
            PropertyUtils.copyProperties(ds, dd);
            ds.setDatasetId(dd.getDataSetId()); // upper/lowercase problem

            Cohort[] cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
            Map<String, String> cohortMap = new HashMap<String, String>();
            if (cohorts != null && cohorts.length > 0)
            {
                cohortMap.put("All", "");
                for (Cohort cohort : cohorts)
                    cohortMap.put(cohort.getLabel(), String.valueOf(cohort.getRowId()));
            }
            ds.setCohortMap(cohortMap);

            Map<String, String> visitDateMap = new HashMap<String, String>();
            TableInfo tinfo = dd.getTableInfo(getUser(), false, false);
            for (ColumnInfo col : tinfo.getColumns())
            {
                if (!Date.class.isAssignableFrom(col.getJavaClass()))
                    continue;
                if (col.getName().equalsIgnoreCase("visitdate"))
                    continue;
                if (col.getName().equalsIgnoreCase("modified"))
                    continue;
                if (visitDateMap.isEmpty())
                    visitDateMap.put("", "");
                visitDateMap.put(col.getName(), col.getName());
            }
            ds.setVisitDateMap(visitDateMap);


            Integer protocolRowId = dd.getProtocolId();
            if (protocolRowId != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolRowId.intValue());
                if (protocol != null)
                {
                    ds.setSourceAssayName(protocol.getName());

                    ActionURL assayURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(protocol.getContainer(), protocol);
                    ds.setSourceAssayURL(assayURL.getLocalURIString());

                    ActionURL unlinkAssayURL = new ActionURL(DatasetController.UnlinkAssayAction.class, getContainer());
                    unlinkAssayURL.addParameter("datasetId", dd.getDataSetId());
                    ds.setUnlinkAssayURL(unlinkAssayURL.getLocalURIString());
                }
            }


            return ds;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }


    public List updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain update)
    {
        try
        {
            assert orig.getDomainURI().equals(update.getDomainURI());
            List<String> errors = new ArrayList<String>();

            if (!getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                errors.add("Unauthorized");
                return errors;
            }

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
                if (DataSetDefinition.isDefaultFieldName(prop.getName(), study))
                    iter.remove();
            }
            update.setFields(updatedProps);

            errors = updateDomainDescriptor(orig, update);
            if (errors == null)
                errors = updateDataset(ds, orig.getDomainURI());

            return errors.isEmpty() ? null : errors;
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private List updateDataset(GWTDataset ds, String domainURI)
    {
        try
        {
            List<String> errors = new ArrayList<String>();

            // CONSIDER: optimistic concurrency validate against current
            // validate that this smells right
            DataSetDefinition def = study.getDataSet(ds.getDatasetId());
            if (null == def)
            {
                errors.add("Dataset not found");
                return errors;
            }

            if (ds.getDemographicData() && !def.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(def))
            {
                errors.add("This dataset currently contains more than one row of data per participant. Demographic data includes one row of data per participant.");
                return errors;
            }

            DataSetDefinition updated = def.createMutable();
            BeanUtils.copyProperties(updated, ds);

            String keyPropertyName = null;
            if (ds.getKeyPropertyName() != null)
            {
                Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);

                for (DomainProperty dp : domain.getProperties())
                {
                    if (dp.getName().equalsIgnoreCase(ds.getKeyPropertyName()))
                    {
                        keyPropertyName = dp.getName();
                        break;
                    }
                }
            }
            updated.setKeyPropertyName(keyPropertyName);

            if (!def.getLabel().equals(updated.getLabel()))
            {
                DataSetDefinition existing = studyManager.getDataSetDefinition(study, updated.getLabel());
                if (existing != null)
                {
                    errors.add("A Dataset already exists with the label \"" + updated.getLabel() +"\"");
                    return errors;
                }
            }

            if (!def.getName().equals(updated.getName()))
            {
                DataSetDefinition existing = studyManager.getDataSetDefinitionByName(study, updated.getName());
                if (existing != null)
                {
                    errors.add("A Dataset already exists with the name \"" + updated.getName() +"\"");
                    return errors;
                }
            }

            studyManager.updateDataSetDefinition(getUser(), updated);
            studyManager.uncache(def);

            return errors;
        }
        catch (Exception x)
        {
            throw UnexpectedException.wrap(x);
        }
    }

    public List updateDatasetDefinition(GWTDataset ds, GWTDomain domain, String tsv)
    {
        try
        {
            List<String> errors = new ArrayList<String>();

            if (!getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
            {
                errors.add("Unauthorized");
                return errors;
            }

            List<Map<String, Object>> maps = null;
            if (null != tsv && tsv.length() > 0)
            {
                NewTabLoader loader = new NewTabLoader(tsv, true);
                maps = loader.load();
            }

            PropertyDescriptor[] pds = OntologyManager.importOneType(domain.getDomainURI(), maps, errors, getContainer());
            if (pds == null || pds.length == 0)
                errors.add("No properties were successfully imported.");

            if (errors.isEmpty())
                errors = updateDataset(ds, domain.getDomainURI());

            return errors.isEmpty() ? null : errors;
        }
        catch (IOException e)
        {
            throw UnexpectedException.wrap(e);
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Override
    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId)
    {
        GWTDomain domain = super.getDomainDescriptor(typeURI, domainContainerId);
        domain.setDefaultValueOptions(new DefaultValueType[]
                { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
        return domain;
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
