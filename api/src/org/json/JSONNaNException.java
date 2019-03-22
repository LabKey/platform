package org.json;

public class JSONNaNException extends JSONException {
    /**
     * Constructs a JSONNaNException, which is just a JSONException with
     * the message "JSON does not allow NaN values.".
     */
    public JSONNaNException(){
        super("JSON does not allow NaN values.");
    }
}
