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
    return "Extract texts and escapes reserved characters of aozora bunko."

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
    
    This function must be returns a tuple (String title, File text).
    
    If render plugin ignored the change of "worker.isCancelled()", user may get 
    slower response speed and lower application performance.
    On "for loader in results:" loop, interrupt when user cancel is True, and 
    return a message "CANCELLED". 
    """
    data = API.getData()
    results = data.get("results") #result of query
    worker  = data.get("worker")  #swing worker thread
    query   = data.get("query")   #query string
    file = API.createTemporaryFile("text_extract", "txt") #temporary file
    
    f = open(file.getPath(), "w")
    title = None
    for i, loader in enumerate(results):
        if worker.isCancelled():
            return "CANCELLED" #return a message "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i, len(results)))
        try:
            if not title:
                title = loader.getTitle()
            text = loader.getText()
            if not text:
                text = ""
            text = text.replace(u"＃", "#")
            text = text.replace(u"※", "*")
            text = text.replace(u"《", "<<")
            text = text.replace(u"》", ">>")
            text = text.replace(u"［", "[")
            text = text.replace(u"］", "]")
            text = text.replace(u"〔", "[")
            text = text.replace(u"〕", "]")
            text = text.replace(u"｜", "|")
            f.write("----------------------------------------\n")
            f.write("[")
            f.write("%d/%d " % (i + 1, len(results)))
            f.write(" %s" % loader.getTitle().encode("utf-8", "replace"))
            f.write("]\n")
            f.write(text.encode("utf-8", "replace"))
            f.write("\n")
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i + 1, len(results), loader.getUrl())
    f.close()
    return (title, file)
    