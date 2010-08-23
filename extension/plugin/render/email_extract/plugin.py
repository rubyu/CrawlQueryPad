#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad render-extension.
"""

from com.blogspot.rubyug.crawlquerypad import DomUtils

def ext_name():
    """
    Returns this extension's name.
    """
    return "email_extract"

def ext_description():
    """
    Returns this extension's summary.
    """
    return "Extracts links that has mail/tel/sms/mms schemes."

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
    file = API.createTemporaryFile("txt") #temporary file
    
    mailSet = set([])
    for i, loader in enumerate(results):
        if worker.isCancelled():
            return "CANCELLED" #return a message "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i + 1, len(results)))
        try:
            ins = loader.getContent()
            if ins:
                charset = loader.guessCharset()
                for mail in DomUtils.extractMails(loader.getUrl(), ins, charset):
                    if not mail in mailSet:
                        mailSet.add(mail)
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i + 1, len(results), loader.getUrl())
        finally:
            try:
                ins.close()
            except:
                pass
    f = open(file.getPath(), "w")
    for mail in mailSet:
        f.write(mail.encode("utf-8", "replace"))
        f.write("\n")
    f.close()
    return ("adresses.txt", file)
    