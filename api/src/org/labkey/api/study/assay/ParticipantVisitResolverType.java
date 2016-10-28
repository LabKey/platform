/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.assay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.InsertView;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Sep 20, 2007
 */
public interface ParticipantVisitResolverType
{
    public ParticipantVisitResolver createResolver(ExpRun run, Container targetStudyContainer, User user) throws IOException, ExperimentException;

    public ParticipantVisitResolver createResolver(Collection<ExpMaterial> inputMaterials,
                                                   Collection<ExpData> inputDatas,
                                                   Collection<ExpMaterial> outputMaterials,
                                                   Collection<ExpData> outputDatas,
                                                   Container runContainer,
                                                   Container targetStudyContainer, User user) throws IOException, ExperimentException;

    public String getName();

    public String getDescription();

    public void render(RenderContext ctx) throws Exception;

    public void addHiddenFormFields(AssayRunUploadContext<?> form, InsertView view);

    public void configureRun(AssayRunUploadContext<?> context, ExpRun run, Map<ExpData, String> inputDatas) throws ExperimentException;

    public boolean collectPropertyOnUpload(AssayRunUploadContext<?> uploadContext, String propertyName);

    public static class Serializer
    {
        public static final String STRING_VALUE_PROPERTY_NAME = "stringValue";

        /**
         * ParticipantVisitResolver default value may be stored as a simple string, or it may be JSON encoded.
         * If JSON encoded, it may have additional nested properties containing ThawList list settings.
         */
        public static void decode(String stringValue, Map<String, Object> formDefaults, String propName)
        {
            try
            {
                Map<String, String> decodedVals = new ObjectMapper().readValue(stringValue, Map.class);
                formDefaults.put(propName, decodedVals.remove(STRING_VALUE_PROPERTY_NAME));
                formDefaults.putAll(decodedVals);
            }
            catch (IOException e)
            {
                formDefaults.put(propName, stringValue);
            }

        }

        public static String decodeBaseStringValue(String stringValue)
        {
            Map<String, Object> decodedVals = new HashMap<>();
            decode(stringValue, decodedVals, STRING_VALUE_PROPERTY_NAME);
            return (String)decodedVals.get(STRING_VALUE_PROPERTY_NAME);
        }

        /**
         * We store additional ThawList settings as JSON property value pairs inside the default value for
         * ParticipantVisitResolver
         */
        public static String encode(String resolverType, HttpServletRequest request)
        {
            try
            {
                // Prevent double encoding; if the resolverType string is already a json map, return it.
                new ObjectMapper().readValue(resolverType, Map.class);
                return resolverType;
            }
            catch (IOException e)
            {
                // Wasn't a json map, just a string. Proceed with encoding below.
            }
            Map<String, String> jsonValues = new LinkedHashMap<>();
            jsonValues.put(ParticipantVisitResolverType.Serializer.STRING_VALUE_PROPERTY_NAME, resolverType);

            if (resolverType.equals(ThawListResolverType.NAME))
            {
                boolean isLookupTextType = ThawListResolverType.TEXT_NAMESPACE_SUFFIX.equals(request.getParameter(ThawListResolverType.THAW_LIST_TYPE_INPUT_NAME));

                Map<String, String> values = new HashMap<>();
                IteratorUtils.asIterator(request.getParameterNames()).forEachRemaining(name -> {
                    // Issue 21126 If lookup was pasted tsv, could still get a default list entry in parameter map. Don't add it to properties list.
                    if (!isLookupTextType || !StringUtils.startsWith(name, ThawListResolverType.NAMESPACE_PREFIX + ThawListResolverType.LIST_NAMESPACE_SUFFIX))
                        values.put(name, request.getParameter(name));
                });
                for (Map.Entry<String, String> entry : values.entrySet())
                {
                    String name = entry.getKey();
                    if (name.startsWith(ThawListResolverType.NAMESPACE_PREFIX) && !name.equalsIgnoreCase(ThawListResolverType.THAW_LIST_TEXT_AREA_INPUT_NAME))
                    {
                        String thawListValue = entry.getValue();
                        if (thawListValue != null && !thawListValue.isEmpty())
                            jsonValues.put(name, thawListValue);
                    }
                }
            }

            try
            {
                return new ObjectMapper().writeValueAsString(jsonValues);
            }
            catch (JsonProcessingException e)
            {
                throw new UnexpectedException(e);
            }
        }
    }
}
