/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Jan 15, 2009
 */
public abstract class AbstractAssayAPIAction<FORM extends SimpleApiJsonForm> extends ApiAction<FORM>
{

    public final ApiResponse execute(FORM form, BindException errors) throws Exception
    {
        if (form.getJsonObject() == null)
        {
            form.bindProperties(new JSONObject());
        }

        Pair<ExpProtocol, AssayProvider> pair = getProtocolProvider(form.getJsonObject(), getContainer());
        ExpProtocol protocol = pair.first;
        AssayProvider provider = pair.second;

        return executeAction(protocol, provider, form, errors);
    }

    public static Pair<ExpProtocol, AssayProvider> getProtocolProvider(JSONObject json, Container c)
    {
        int assayId = json.optInt(AssayJSONConverter.ASSAY_ID);
        if (assayId > 0)
            return getProtocolProvider(assayId, c);

        String assayName = json.optString(AssayJSONConverter.ASSAY_NAME);
        String providerName = json.optString(AssayJSONConverter.PROVIDER_NAME);
        if (assayName == null || providerName == null)
            throw new IllegalArgumentException("assayId or both protocolName and providerName required");

        return getProtocolProvider(assayName, providerName, c);
    }

    public static Pair<ExpProtocol, AssayProvider> getProtocolProvider(Integer assayId, Container c)
    {
        if (assayId == null)
            throw new IllegalArgumentException("assayId parameter required");

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
            throw new NotFoundException("Could not find assay id " + assayId);

        List<ExpProtocol> availableAssays = AssayService.get().getAssayProtocols(c);
        if (!availableAssays.contains(protocol))
            throw new NotFoundException("Assay id " + assayId + " is not visible for folder " + c);

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new NotFoundException("Could not find assay provider for assay id " + assayId);

        return Pair.of(protocol, provider);
    }

    public static Pair<ExpProtocol, AssayProvider> getProtocolProvider(String protocolName, String providerName, Container c)
    {
        AssayProvider provider = AssayService.get().getProvider(providerName);
        if (provider == null)
            throw new NotFoundException("Assay provider '" + providerName + "' not found");

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider);
        if (protocols.isEmpty())
            throw new NotFoundException("Assay protocol '" + protocolName + "' not found");

        protocols = protocols.stream().filter(p -> protocolName.equals(p.getName())).collect(Collectors.toList());
        if (protocols.isEmpty())
            throw new NotFoundException("Assay protocol '" + protocolName + "' not found");

        if (protocols.size() > 1)
            throw new NotFoundException("More than one assay protocol named '" + protocolName + "' was found.");

        return Pair.of(protocols.get(0), provider);
    }

    protected abstract ApiResponse executeAction(ExpProtocol assay, AssayProvider provider, FORM form, BindException errors) throws Exception;
}
