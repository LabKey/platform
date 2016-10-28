/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
package org.labkey.api.exp;

/**
 * Indicates something went wrong when operating over data managed by the Experiment module.
 * User: jeckels
 * Date: Sep 21, 2005
 */
public class ExperimentException extends Exception
{

    public ExperimentException()
    {
        super();
    }

    public ExperimentException(String message)
    {
        super(message);
    }

    public ExperimentException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ExperimentException(Throwable cause)
    {
        super(cause);
    }

    @Override
    public String getMessage()
    {
        String result = super.getMessage();
        if (result == null)
        {
            result = getClass().getName();
            if (getCause() != null && getCause() != this)
            {
                if (getCause().getMessage() != null)
                {
                    return getCause().getMessage();
                }
                result += " " + getCause();
            }
        }
        return result;
    }
}
