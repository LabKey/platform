/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/15/12
 * Time: 6:26 AM
 */
abstract public class LaboratoryService
{
    static LaboratoryService instance;

    static private final String URI = "http://cpas.labkey.com/laboratory#";
    static public final String ASSAYRESULT_CONCEPT_URI = URI + "assayResult";
    static public final String ASSAYRAWRESULT_CONCEPT_URI = URI + "assayRawResult";
    static public final String SAMPLEDATE_CONCEPT_URI = URI + "sampleDate";
    static public final String BIRTHDATE_CONCEPT_URI = URI + "birthDate";
    static public final String DEATHDATE_CONCEPT_URI = URI + "deathDate";
    static public final String PARTICIPANT_CONCEPT_URI = "http://cpas.labkey.com/Study#ParticipantId";

    public static LaboratoryService get()
    {
        return instance;
    }

    static public void setInstance(LaboratoryService instance)
    {
        LaboratoryService.instance = instance;
    }

    abstract public void registerModule(Module module);

    abstract public Set<Module> getRegisteredModules();

    abstract public void registerDataProvider(DataProvider dp);

    abstract public Set<DataProvider> getDataProviders();

    abstract public Set<AssayDataProvider> getRegisteredAssayProviders();

    abstract public AssayDataProvider getDataProviderForAssay(int protocolId);

    abstract public AssayDataProvider getDataProviderForAssay(AssayProvider ap);

    abstract public Pair<ExpExperiment, ExpRun> saveAssayBatch(List<Map<String, Object>> results, JSONObject json, String basename, ViewContext ctx, AssayProvider provider, ExpProtocol protocol) throws ValidationException;

    abstract public Pair<ExpExperiment, ExpRun> saveAssayBatch(List<Map<String, Object>> results, JSONObject json, File file, ViewContext ctx, AssayProvider provider, ExpProtocol protocol) throws ValidationException;

    abstract public List<NavItem> getSettingsItems(Container c, User u);

    abstract public List<NavItem> getReportItems(Container c, User u);

    abstract public List<TabbedReportItem> getTabbedReportItems(Container c, User u);

    abstract public List<NavItem> getSampleItems(Container c, User u);

    abstract public List<NavItem> getMiscItems(Container c, User u);

    abstract public DataProvider getDataProvider(String name);

    abstract public List<NavItem> getDataItems(Container c, User u);

    abstract public void ensureAssayColumns(User u, String providerName) throws ChangePropertyDescriptorException;

    abstract public void sortNavItems(List<? extends NavItem> navItems);

    abstract public void registerClientDependency(ClientDependency cd, Module owner);

    abstract public Set<ClientDependency> getRegisteredClientDependencies(Container c);

    abstract public String getDefaultWorkbookFolderType(Container c);

    abstract public void registerAssayButton(ButtonConfigFactory btn, String providerName, String domain);

    abstract public List<ButtonConfigFactory> getAssayButtons(TableInfo ti, String providerName, String domain);

    abstract public TableCustomizer getLaboratoryTableCustomizer();

    abstract public void registerAssayResultsIndex(String providerName, List<String> columnsToIndex);

    abstract public void registerTableIndex(String schemaName, String queryName, List<String> columnsToIndex);

    abstract public void registerTableCustomizer(Module owner, Class<? extends TableCustomizer> customizerClass, String schemaName, String queryName);

    public static enum NavItemCategory
    {
        samples(),
        misc(),
        settings(),
        reports(),
        tabbedReports(),
        data();

        NavItemCategory()
        {

        }
    }
}
