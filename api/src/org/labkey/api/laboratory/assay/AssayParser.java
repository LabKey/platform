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
package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 10:40 AM
 */
public interface AssayParser
{
    /**
     * Parses the provided file and json object, returning a list of row maps.
     * @param json
     * @param file
     * @param fileName
     * @param ctx
     * @return
     * @throws ValidationException
     * @throws ExperimentException
     */
    public JSONObject getPreview(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    /**
     * Parses the provided file and json object using getPreview(), then saves this to the database
     * @param json
     * @param file
     * @param fileName
     * @param ctx
     * @throws ValidationException
     * @throws ExperimentException
     */
    public void saveBatch(JSONObject json, File file, String fileName, ViewContext ctx) throws ValidationException, ExperimentException;

    public List<Map<String, Object>> parseResults(JSONObject json, File file) throws ValidationException, ExperimentException;

    public ExpProtocol getProtocol();

    public AssayProvider getProvider();
}
