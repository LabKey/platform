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
package org.labkey.api.pipeline;

import java.io.InputStream;
import java.util.Map;
import java.io.File;
import java.io.IOException;

/**
 * <code>ParamParser</code>
 */
public interface ParamParser
{
    void parse(InputStream inputStream);

    void setValidator(Validator validator);

    void addError(String paramName, String message);

    Error[] getErrors();

    String getXML();

    String getInputParameter(String name);

    void setInputParameter(String name, String value);

    void setInputParameter(String name, String value, String before);

    void addInputParameters(Map<String, Object> params);

    String removeInputParameter(String name);

    String[] getInputParameterNames();

    Map<String, String> getInputParameters();

    String getXMLFromMap(Map<String, String> params);

    void writeFromMap(Map<String, String> params, File fileDest) throws IOException;

    /**
     * <code>Error</code>
     */
    interface Error
    {
        String getMessage();

        int getLine();

        int getColumn();
    }

    /**
     * <code>Validator</code> validates all parameters set by the user in the
     * parameter set being parsed.
     */
    interface Validator
    {
        void validate(ParamParser parser);
    }

    /**
     * <code>ParamValidator</code> validates a single paramter set by the user
     * in the parameter set being parsed.
     */
    interface ParamValidator
    {
        void validate(String name, String value, ParamParser parser);
    }
}
