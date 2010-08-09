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
    return "Save data to a local file."

def _show_directory_dialog():
    filechooser = JFileChooser("")
    filechooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
    selected = filechooser.showSaveDialog(None)
    if selected == JFileChooser.APPROVE_OPTION:
      file = filechooser.getSelectedFile()
      return file.getAbsolutePath()
      
def call(API, data):
    """
    Main function of the extension.
    Geven a few arguments and returns a message that 
    displays on statusbar of CQPad.
    
    "API" is the ExtensionAPI class object.
    It's provides follow functions
        void    setState(State state)
        State   getState()
        
    * State is the manager class of Map<String, List<String, String>>.
        It's provides follow functions
            void    add(String key, String value)
            void    add(String key, long value)
            List<String>    getAll(String key)
            Map<String, List<String, String>>   getAll()
            String  getFirstOr(String key, String def)
            long    getFirstOr(String key, long def)
            Set<String> getKeys()
            void    put(String key, List<String> list)
            void    remove(String key)
            void    set(String key, String value)
            void    set(String key, long value)
            String  toXML()
            
    "data" is a map object, contains some values.
    data.get("title")       #title
    data.get("textFile")        #text file
    data.get("queryString") #query string
    """
    title = data.get("title")
    textFile  = data.get("textFile")
    
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
        f = open( File(path, title + ".txt").getPath(), "w" )
        for line in open(textFile.getPath(), "r"):
            f.write( line )
        f.close()
        return "Saved"
    else:
        return "Save Failed"
        