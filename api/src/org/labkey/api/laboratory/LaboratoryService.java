/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.laboratory.assay.AssayImportMethod;
import org.labkey.api.laboratory.assay.AssayParser;
import org.labkey.api.module.Module;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 6:26 AM
 */
abstract public class LaboratoryService
{
    static LaboratoryService instance;

    static private final String URI = "http://cpas.labkey.com/laboratory#";
    static public final String ASSAYRESULT_CONCEPT_URI = URI + "assayResult";

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

    abstract public Set<AssayDataProvider> getRegisteredAssays();

    abstract public AssayDataProvider getAssayDescriptorForPrototol(int protocolId);

    abstract public void registerAssay(AssayDataProvider assay);

    abstract public void registerAssayImportMethods(String providerName, AssayImportMethod... methodList);

    abstract public List<AssayImportMethod> getImportMethods(AssayProvider ap);

    abstract public AssayImportMethod getImportMethodByName(String assayName, String methodName);

    abstract public Pair<ExpExperiment, ExpRun> saveAssayBatch(AssayParser parser, JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    abstract public AssayImportMethod getManualEntryImportMethod(AssayProvider ap);

    abstract public TableCustomizer getDefaultTableCustomizer();

    abstract public void registerNavItems(NavItem... items);

    abstract public List<NavItem>  getNavItems(@Nullable String category);

    abstract public Set<SettingsItem> getSettingsItems(Container c, User u);

    abstract public void registerSettingItems(SettingsItem... items);
}
