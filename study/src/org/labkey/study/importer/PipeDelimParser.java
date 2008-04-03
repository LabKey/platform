package org.labkey.study.importer;

/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:11:11 PM
 */
class PipeDelimParser
{
    private int _left = 0;
    private int _right = 0;
    private String _data;

    public PipeDelimParser(String data)
    {
        _data = data;
        _right = _data.indexOf('|');
    }

    public boolean hasNext()
    {
        return _left <= _data.length();
    }

    public String next()
    {
        if (!hasNext())
            throw new IllegalStateException("Expected field not found.  Invalid visit map format?");
        String value;
        if (_right >= 0)
        {
            value = _data.substring(_left, _right);
            _left = _right + 1;
            _right = _data.indexOf('|', _left);
        }
        else
        {
            if (_left == _data.length())
                value = "";
            else
                value = _data.substring(_left);
            _left = _data.length() + 1;
        }
        return value.trim();
    }
}
