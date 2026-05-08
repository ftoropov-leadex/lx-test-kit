package com.apiframework.splunk.model;

import java.time.Instant;
import java.util.Map;

// A single log event returned from a Splunk search.
// Well-known fields (_raw, _time, source, etc.) are typed; everything else is in the fields map.
public record SplunkSearchResult(
    String raw,          // _raw: the full original log line text
    Instant time,        // _time: event timestamp, null if Splunk returned an unparseable value
    String source,       // source: log origin path or identifier
    String sourceType,   // sourcetype: Splunk input type
    String host,         // host: the machine that generated the event
    String index,        // index: the Splunk index the event is stored in
    Map<String, String> fields  // all fields returned by the search, including the well-known ones above
) {

    // Looks up a field by name from the fields map. Returns null if the field was not returned.
    public String field(String fieldName) {
        return fields.get(fieldName);
    }
}
