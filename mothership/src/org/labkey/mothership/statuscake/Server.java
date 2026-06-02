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
