var EditArea_save = {

    /**
     * Is called each time the user touch a keyboard key.
     *
     * @param (event) e: the keydown event
     * @return true - pass to next handler in chain, false - stop chain execution
     * @type boolean
     */
    onkeydown : function(e) {                
        var letter = String.fromCharCode(e.keyCode);
        var low_letter = letter.toLowerCase();
        if (parent == null || parent.saveEvent == null)
            return true;
        if (13 == e.keyCode)
            low_letter = "enter";
        if (CtrlPressed(e) && !AltPressed(e) && !ShiftPressed(e)) {
            switch(low_letter){
                case "s":
                    parent.saveEvent(e);
                    e.preventDefault();
                    break;
                case "e":
                    parent.editEvent(e);
                    e.preventDefault();
                    return false;
                case "enter":
                    parent.executeEvent(e);
                    e.preventDefault();
                    return false;
                default:
                    break;
            }
        }
        return true;
    }
};

// Adds the plugin class to the list of available EditArea plugins
editArea.add_plugin("save", EditArea_save);