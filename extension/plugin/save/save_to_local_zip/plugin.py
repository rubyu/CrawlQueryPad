#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad save-extension.
"""

from javax.swing import JFileChooser
from java.io import File, BufferedInputStream, FileInputStream, FileOutputStream
from java.util.zip import ZipEntry, ZipOutputStream
import jarray
import os
import zipfile

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
    filename = escape_filename(data.get("filename"))
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
        #java
        output_file = File( path, "%s.zip" % filename )
        input_stream      = BufferedInputStream( FileInputStream(file) )
        zip_output_stream = ZipOutputStream( FileOutputStream(output_file) )
        zip_entry         = ZipEntry( "%s%s" % ("content", os.path.splitext(filename)[1]) ) #overwrite filename
        zip_output_stream.putNextEntry(zip_entry)
        buf = jarray.zeros(1024, "b")
        while True:
            len = input_stream.read(buf)
            if len < 0:
                break
            zip_output_stream.write(buf, 0, len)
        input_stream.close()
        zip_output_stream.close()
        return "\"%s\" saved" % output_file.getPath()
        """
        #jython
        output_file = os.path.join( path, "%s.zip" % filename )
        zip = zipfile.ZipFile(output_file, 'w', zipfile.ZIP_DEFLATED)
        zip.write(
            file.getPath().encode("cp932", "replace"), #LookupError: unknown encoding ...why?
            filename.encode("cp932", "replace")        #
            )
        zip.close()
        return "\"%s\" saved" % output_file
        """
    else:
        return "Save Failed"
        