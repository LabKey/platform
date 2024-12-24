package org.labkey.mothership.statuscake;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Server {
    private final String _id;
    private final String _name;
    private final List<String> _tags;
    private LocalDateTime _firstUse;

    public Server(String id, String name, List<String> tags) {
        _id = id;
        _name = name;
        _tags = tags;
    }

    public void checkFirstUse(LocalDateTime time) {
        if (_firstUse == null || time.isBefore(_firstUse)) {
            _firstUse = time;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("id", _id);
        result.put("name", _name);
        result.put("tags", String.join(", ", _tags));
        result.put("firstUse", _firstUse);
        return result;
    }

    @Override
    public String toString() {
        return "Server: " + _name;
    }

    public String getId() {
        return _id;
    }
}
