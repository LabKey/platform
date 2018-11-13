/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.admin;

import java.util.Map;

/**
 * Used for registering a HealthCheck with {@link org.labkey.api.admin.HealthCheckRegistry}.
 */
public interface HealthCheck
{
    /**
     * This method will report the overall health for a certain part of the system.  The check should be
     * extremely light-weight (e.g., it should generally not do database queries) as it may be called quite
     * frequently.
     * @return Result of health check
     */
    Result checkHealth();

    class Result
    {
        private boolean _healthy = true;
        private Map<String, Object> _details;

        public Result()
        {
        }

        public Result(boolean healthy, Map<String, Object> details)
        {
            _healthy = healthy;
            _details = details;

        }
        public boolean isHealthy()
        {
            return _healthy;
        }

        public void setHealthy(boolean healthy)
        {
            this._healthy = healthy;
        }

        public Map<String, Object> getDetails()
        {
            return _details;
        }

        public void setDetails(Map<String, Object> details)
        {
            this._details = details;
        }
    }
}
