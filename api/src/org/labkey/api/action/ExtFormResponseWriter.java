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
package org.labkey.api.action;

import org.json.JSONObject;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/*
* User: Dave
* Date: Sep 3, 2008
* Time: 11:03:32 AM
*/

/**
 * This writer extends ApiJsonWriter by writing validation errors in the format
 * that Ext forms require.
 */
public class ExtFormResponseWriter extends ApiJsonWriter
{
    public ExtFormResponseWriter(HttpServletResponse response)
    {
        super(response);
    }

    public ExtFormResponseWriter(HttpServletResponse response, String contentTypeOverride)
    {
        super(response, contentTypeOverride);
    }

    public void write(Errors errors) throws IOException
    {
        /*
            Ext has a particular format they use for consuming validation errors expressed in JSON:
            {
                success: false,
                errors: {
                    clientCode: "Client not found",
                    portOfLoading: "This field must not be null"
                    }
            }

            This is a bit strange, since you can't provide more than one error
            for a given field, and there's really no place to put object-level
            errors.

            Also, the response code must be 200, even though there are errors.

            See http://extjs.com/deploy/dev/docs/?class=Ext.form.Action.Submit
        */

        JSONObject jsonErrors = new JSONObject();
        for(ObjectError error : (List<ObjectError>)errors.getAllErrors())
        {
            String msg = error.getDefaultMessage();
            String key = error.getObjectName();

            if(error instanceof FieldError)
            {
                FieldError ferror = (FieldError)error;
                key = ferror.getField();
                msg = ferror.getDefaultMessage();

                if(jsonErrors.has(ferror.getField()))
                    msg = jsonErrors.get(ferror.getField()) + " " + ferror.getDefaultMessage();
            }

            jsonErrors.put(key, msg);
        }

        JSONObject root = new JSONObject();
        root.put("success", false); //used by Ext forms
        root.put("errors", jsonErrors);
        writeJsonObj(root);
    }
}