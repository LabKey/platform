/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.mothership.statuscake;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public record History(Server s, LocalDateTime start, LocalDateTime end) {
    public long getDuration() {
        if (end == null) {
            return -1;
        }
        ZoneId zoneId = ZoneId.systemDefault(); // You can use any ZoneId
        Instant instant1 = start.atZone(zoneId).toInstant();
        Instant instant2 = end.atZone(zoneId).toInstant();

        // Calculate the duration between the two Instants
        Duration duration = Duration.between(instant1, instant2);

        // Get the difference in seconds
        return duration.toSeconds();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("serverId", s.getId());
        result.put("start", start);
        result.put("duration", getDuration());
        return result;
    }
}
