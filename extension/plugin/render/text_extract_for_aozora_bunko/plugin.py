#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad render-extension.
"""

def ext_name():
    """
    Returns this extension's name.
    """
    return "text_extract_for_aozora_bunko"

def ext_description():
    """
    Returns this extension's summary.
    """
    return u"HTMLからテキストを抽出して予約文字をエスケープする"

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
    Main function of the render extension.
    
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
    
    This function must be return a tuple (String filename, File file).
    
    If render plugin ignored the change of "worker.isCancelled()", user may get 
    slower response speed and lower application performance.
    On "for loader in results:" loop, interrupt when user cancel is True, and 
    return a message "CANCELLED". 
    """
    data = API.getData()
    results = data.get("results") #result of query
    worker  = data.get("worker")  #swing worker thread
    query   = data.get("query")   #query string
    
    filename = ""
    file     = API.createTemporaryFile("txt") #temporary file
    
    f = open(file.getPath(), "w")
    for i, loader in enumerate(results):
        if worker.isCancelled():
            return "CANCELLED" #return a message "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i, len(results)))
        try:
            title = loader.getTitle().strip()
            if not filename:
                filename = "%s.txt" % escape_filename(title)
            text = loader.getText()
            text = text.replace(u"＃", "#")
            text = text.replace(u"※", "*")
            text = text.replace(u"《", "<<")
            text = text.replace(u"》", ">>")
            text = text.replace(u"［", "[")
            text = text.replace(u"］", "]")
            text = text.replace(u"〔", "[")
            text = text.replace(u"〕", "]")
            text = text.replace(u"｜", "|")
            f.write( "----------------------------------------\n" )
            f.write( "[" )
            f.write( "%d/%d " % (i + 1, len(results)) )
            f.write( " %s" % title.encode("utf-8", "replace") )
            f.write( "]\n" )
            f.write( text.encode("utf-8", "replace") )
            f.write( "\n" )
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i + 1, len(results), loader.getUrl())
    f.close()
    return (filename, file)
    