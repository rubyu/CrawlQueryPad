#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad save-extension.
"""

from javax.swing import JFileChooser
from java.io import File

def ext_name():
    """
    Returns this extension's name.
    """
    return "save_to_local"

def ext_description():
    """
    Returns this extension's summary.
    """
    return "Saves data to a local file."

def _show_directory_dialog():
    filechooser = JFileChooser("")
    filechooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
    selected = filechooser.showSaveDialog(None)
    if selected == JFileChooser.APPROVE_OPTION:
      file = filechooser.getSelectedFile()
      return file.getAbsolutePath()

def escape_filename(str):
    #remove last period or blank 
    buf = []
    stop = False
    for s in reversed(str):
        if not stop:
            if s == "." or \
               s == " " or \
               s == "\t":
                continue
            else:
                stop = True
        buf.insert(0, s)
    str = "".join(buf)
    #replace character of ascii 0-31
    buf = []
    for s in str:
        if 31 < ord(s):
            buf.append(s)
        else:
            buf.append("_")
    str = "".join(buf)
    #replace reserved character
    str = str.replace("\\", u"￥")
    str = str.replace("/", u"／")
    str = str.replace(":", u"：")
    str = str.replace("*", u"＊")
    str = str.replace("?", u"？")
    str = str.replace("\"", u"”")
    str = str.replace("<", u"＜")
    str = str.replace(">", u"＞")
    str = str.replace("|", u"｜")
    #delete if reserved name
    if str == "AUX" or \
       str == "CLOCK$" or \
       str == "COM1" or str == "COM2" or \
       str == "COM3" or str == "COM4" or \
       str == "COM5" or str == "COM6" or \
       str == "COM7" or str == "COM8" or \
       str == "COM9" or \
       str == "CON" or \
       str == "CONFIG$" or \
       str == "LPT1" or str == "LPT2" or \
       str == "LPT3" or str == "LPT4" or \
       str == "LPT5" or str == "LPT6" or \
       str == "LPT7" or str == "LPT8" or \
       str == "LPT9" or \
       str == "NUL" or \
       str == "PRN":
        str == ""
    #delete if .. or .
    if str == "." or str == "..":
        str = ""
    #if empty
    if str == "":
        str = "(empty)"
    return str
    
def call(API):
    """
    Main function of the save extension.
    
    API is a instance of ExtensionAPI class, that provides access to "Data" and "State".
    * Access to "Data"
        data = API.getData() #get data map
        data.get("query")
        >>> '"http://www.google.com/" > 1'
    * Access to "State"
        state = API.getState()      #get plugin state
        state.add("key", "value")   #add key and value
        state.getFirstOr("key", "") #get value
        >>> 'value'
        API.setState(state) #save plugin state
    
    This function must be return a string, and that will be displayed on statusbar of CQPad.
    """
    data = API.getData()
    title     = data.get("title")
    text_file = data.get("text")
    
    state = API.getState()    
    path = state.getFirstOr( "path", "" )
    if not File(path).exists():
        path = ""
    if "" == path:
        _path = _show_directory_dialog()
        if None != _path:
            path = _path
            state.set( "path", path )
            API.setState(state)
    if "" != path:
        save_file = File(path, escape_filename(title + ".txt"))
        f = open( save_file.getPath(), "w" )
        for line in open(text_file.getPath(), "r"):
            f.write( line )
        f.close()
        return "\"%s\" saved" % save_file.getPath()
    else:
        return "Save Failed"
        