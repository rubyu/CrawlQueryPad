#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad save-extension.
"""

from javax.swing import JFileChooser
from java.io import File, BufferedInputStream, FileInputStream, FileOutputStream
from java.util.zip import ZipEntry, ZipOutputStream
import jarray
import os

def ext_name():
    """
    Returns this extension's name.
    """
    return "save_to_local_zip"

def ext_description():
    """
    Returns this extension's summary.
    """
    return "Saves data to a local zip file."

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
        save_file = File( path, "%s.zip" % filename )
        input_stream      = BufferedInputStream( FileInputStream(file) )
        zip_output_stream = ZipOutputStream( FileOutputStream(save_file) )
        zip_entry         = ZipEntry(filename)
        zip_output_stream.putNextEntry(zip_entry)
        buf = jarray.zeros(1024, "b")
        while True:
            len = input_stream.read(buf)
            if len < 0:
                break
            zip_output_stream.write(buf, 0, len)
        input_stream.close()
        zip_output_stream.close()
        return "\"%s\" saved" % save_file.getPath()
    else:
        return "Save Failed"
        