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
