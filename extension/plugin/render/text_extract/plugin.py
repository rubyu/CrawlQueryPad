#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad render-extension.
"""

def ext_name():
    """
    Returns this extension's name.
    """
    return "text_extract"

def ext_description():
    """
    Returns this extension's summary.
    """
    return "Extracts text from html contents."

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
    
    This function must be return a tuple (String title, File text).
    
    If render plugin ignored the change of "worker.isCancelled()", user may get 
    slower response speed and lower application performance.
    On "for loader in results:" loop, interrupt when user cancel is True, and 
    return a message "CANCELLED". 
    """
    data = API.getData()
    results = data.get("results") #result of query
    worker  = data.get("worker")  #swing worker thread
    query   = data.get("query")   #query string
    
    result_title = ""
    result_file  = API.createTemporaryFile("txt") #temporary file
    
    f = open(result_file.getPath(), "w")
    for i, loader in enumerate(results):
        if worker.isCancelled():
            return "CANCELLED" #return a message "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i, len(results)))
        try:
            title = loader.getTitle().strip()
            if not result_title:
                result_title = title
            f.write( "----------------------------------------\n" )
            f.write( "[" )
            f.write( "%d/%d " % (i + 1, len(results)) )
            f.write( " %s" % title.encode("utf-8", "replace") )
            f.write( "]\n" )
            f.write( loader.getText().encode("utf-8", "replace") )
            f.write( "\n" )
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i + 1, len(results), loader.getUrl())
    f.close()
    return (result_title, result_file)
    