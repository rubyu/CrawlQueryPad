#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad save-extension.
"""

from javax.swing import JFileChooser
from java.io import File

import os

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
    filename = data.get("filename")
    file     = data.get("file")
    
    state = API.getState()
    path = state.getFirstOr("path", "")
    if not path or not os.path.exists(path):
        _path = _show_directory_dialog()
        if _path:
            path = _path
            state.set("path", path)
            API.setState(state)
    if path and os.path.exists(path):
        output_file = os.path.join( path, filename )
        f = open(output_file, "w")
        for line in open(file.getPath(), "r"):
            f.write( line )
        f.close()
        return "\"%s\" saved" % output_file
    else:
        return "Save Failed"
        