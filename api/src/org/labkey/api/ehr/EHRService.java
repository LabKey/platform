/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.api.ehr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.dataentry.DataEntryForm;
import org.labkey.api.ehr.dataentry.DataEntryFormFactory;
import org.labkey.api.ehr.dataentry.SingleQueryFormProvider;
import org.labkey.api.ehr.demographics.DemographicsProvider;
import org.labkey.api.ehr.history.HistoryDataSource;
import org.labkey.api.ehr.history.LabworkType;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/14/12
 * Time: 4:44 PM
 */
abstract public class EHRService
{
    static EHRService instance;

    public static EHRService get()
    {
        return instance;
    }

    static public void setInstance(EHRService instance)
    {
        EHRService.instance = instance;
    }

    abstract public void registerModule(Module module);

    abstract public Set<Module> getRegisteredModules();

    abstract public void registerLabworkType(LabworkType type);

    abstract public void registerTriggerScript(Module owner, Resource script);

    abstract public List<Resource> getExtraTriggerScripts(Container c);

    abstract public void registerDemographicsProvider(DemographicsProvider provider);

    abstract public Collection<DemographicsProvider> getDemographicsProviders(Container c);

    abstract public void registerTableCustomizer(Module owner, Class<? extends TableCustomizer> customizer);

    abstract public void registerTableCustomizer(Module owner, Class<? extends TableCustomizer> customizer, String schema, String query);

    abstract public List<TableCustomizer> getCustomizers(Container c, String schema, String query);

    /**
     * Allow modules to provide JS and other dependencies that will be loaded whenever
     * ehr.context is requested, assuming that module is enabled in the current container
     * @param cd
     * @param owner
     */
    abstract public void registerClientDependency(ClientDependency cd, Module owner);

    abstract public Set<ClientDependency> getRegisteredClientDependencies(Container c);

    abstract public void setDateFormat(Container c, String format);

    abstract public String getDateFormat(Container c);

    abstract public User getEHRUser(Container c);

    abstract public void registerReportLink(REPORT_LINK_TYPE type, String label, Module owner, DetailsURL url, @Nullable String category);

    abstract public void registerReportLink(REPORT_LINK_TYPE type, String label, Module owner, URLHelper url, @Nullable String category);

    public enum REPORT_LINK_TYPE
    {
        housing(),
        project(),
        projectDetails(),
        protocol(),
        protocolDetails(),
        assignment(),
        moreReports(),
        datasets(),
        animalSearch();

        REPORT_LINK_TYPE()
        {

        }
    }

    abstract public void registerActionOverride(String actionName, Module owner, String resourcePath);

    abstract public void registerHistoryDataSource(HistoryDataSource source);

    /**
     * Returns the container holding the EHR study, as defined by the passed container's module properties
     * @param c
     * @return
     */
    abstract public Container getEHRStudyContainer(Container c);

    @NotNull
    abstract public Map<String, EHRQCState> getQCStates(Container c);

    abstract public void registerFormType(DataEntryFormFactory fact);

    abstract public DataEntryForm getDataEntryForm(String name, Container c, User u);

    abstract public void registerDefaultFieldKeys(String schemaName, String queryName, List<FieldKey> keys);

    public static enum FORM_TYPE
    {
        Task(),
        Encounter(),
        Run(),
        Request()
    }

    public static enum FORM_SECTION_LOCATION
    {
        Header(),
        Body(),
        Tabs()
    }

    public static enum QCSTATES
    {
        Abnormal("Abnormal"),
        DeleteRequested("Delete Requested"),
        RequestApproved("Request: Approved"),
        RequestSampleDelivered("Request: Sample Delivered"),
        RequestDenied("Request: Denied"),
        RequestCancelled("Request: Cancelled"),
        RequestPending("Request: Pending"),
        InProgress("In Progress"),
        ReviewRequired("Review Required"),
        Scheduled("Scheduled"),
        Completed("Completed");

        private String _label;

        QCSTATES(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        /** @throws java.lang.IllegalArgumentException if the QC state doesn't exist in the container */
        @NotNull
        public EHRQCState getQCState(@NotNull Container c)
        {
            EHRQCState result = EHRService.get().getQCStates(c).get(_label);
            if (result == null)
            {
                throw new IllegalArgumentException("Could not find QC state " + _label + " in container " + c.getPath());
            }
            return result;
        }
    }
    abstract public List<FieldKey> getDefaultFieldKeys(TableInfo ti);

    abstract public void registerTbarButton(ButtonConfigFactory btn, String schema, String query);

    abstract public void registerMoreActionsButton(ButtonConfigFactory btn, String schema, String query);

    @NotNull
    abstract public List<ButtonConfigFactory> getMoreActionsButtons(TableInfo ti);

    abstract public List<ButtonConfigFactory> getTbarButtons(TableInfo ti);

    abstract public boolean hasDataEntryPermission (String schemaName, String queryName, Container c, User u);

    abstract public boolean hasDataEntryPermission (TableInfo ti);

    abstract public boolean hasPermission (TableInfo ti, Class<? extends Permission> perm);

    abstract public boolean hasPermission (String schemaName, String queryName, Container c, User u, Class<? extends Permission> perm);

    abstract public boolean hasPermission (String schemaName, String queryName, Container c, User u, Class<? extends Permission> perm, EHRQCState qcState);

    abstract public void customizeDateColumn(AbstractTableInfo ti, String colName);

    abstract public TableCustomizer getEHRCustomizer();

    abstract public void registerSingleFormOverride(SingleQueryFormProvider p);

    abstract public void appendCalculatedIdCols(AbstractTableInfo ti, String dateFieldName);

    abstract public Collection<String> ensureFlagActive(User u, Container c, String flag, Date date, String remark, Collection<String> animalIds, boolean livingAnimalsOnly) throws BatchValidationException;

    abstract public Collection<String> ensureFlagActive(User u, Container c, String flag, Date date, Date enddate, String remark, Collection<String> animalIds, boolean livingAnimalsOnly) throws BatchValidationException;

    abstract public Collection<String> terminateFlagsIfExists(User u, Container c, String flag, Date enddate, Collection<String> animalIds);

    abstract public String getEHRDefaultClinicalProjectName(Container c);

    abstract public void addModuleRequiringLegagyExt3EditUI(Module m);
}
