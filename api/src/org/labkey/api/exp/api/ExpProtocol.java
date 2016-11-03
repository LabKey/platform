/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.data.Container;

import java.util.Map;
import java.util.List;
import java.util.Collection;
import java.util.Set;

/**
 * Defines a repeatable process (perhaps a sample derivation, aliquotting, or data analysis),
 * of which there can be multiple instances represented by {@link ExpRun}.
 */
public interface ExpProtocol extends ExpObject
{
    String ASSAY_DOMAIN_PREFIX = "AssayDomain-";
    String ASSAY_DOMAIN_RUN = AssayDomainTypes.Run.getPrefix();
    String ASSAY_DOMAIN_BATCH = AssayDomainTypes.Batch.getPrefix();
    String ASSAY_DOMAIN_DATA = AssayDomainTypes.Result.getPrefix();

    /**
     * List of well-known domain types.  AssayProviders may
     * contain other domain types not listed in this enumeration.
     */
    enum AssayDomainTypes implements IAssayDomainType
    {
        Batch("Batch"), Run("Run"), Result("Data");

        private String prefix;

        AssayDomainTypes(String prefixName)
        {
            this.prefix = ASSAY_DOMAIN_PREFIX + prefixName;
        }

        public String getName()
        {
            return name();
        }

        public String getPrefix()
        {
            return this.prefix;
        }

        public String getLsidTemplate()
        {
            return AbstractAssayProvider.getPresubstitutionLsid(getPrefix());
        }
    }

    void setObjectProperties(Map<String, ObjectProperty> props);

    /**
    * @return map from OntologyEntryURI to parameter
     */
    Map<String, ProtocolParameter> getProtocolParameters();
    void setProtocolParameters(Collection<ProtocolParameter> params);

    String getInstrument();

    String getSoftware();

    String getContact();

    List<? extends ExpProtocol> getChildProtocols();
    List<? extends ExpExperiment> getBatches();

    enum ApplicationType
    {
        ExperimentRun,
        ProtocolApplication,
        ExperimentRunOutput,
    }

    List<? extends ExpProtocolAction> getSteps();
    ApplicationType getApplicationType();
    ProtocolImplementation getImplementation();
    String getDescription();
    Integer getMaxInputDataPerInstance();
    Integer getMaxInputMaterialPerInstance();
    String getProtocolDescription();
    void setProtocolDescription(String description);
    void setMaxInputMaterialPerInstance(Integer maxMaterials);
    void setMaxInputDataPerInstance(Integer i);
    Integer getOutputMaterialPerInstance();
    Integer getOutputDataPerInstance();
    String getOutputMaterialType();
    String getOutputDataType();

    /**
     * Adds a step and persists it to the database
     */
    ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence);

    List<? extends ExpProtocol> getParentProtocols();
    
    List<? extends ExpRun> getExpRuns();

    Set<Container> getExpRunContainers();

    void setApplicationType(ApplicationType type);
    void setDescription(String description);
}
