#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad render-extension.
"""

from com.blogspot.rubyug.crawlquerypad import DomUtils
from com.devx.io import TempFileManager

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

      
def call(API, data):
    """
    Main function of the extension.
    This function must be returns (String title, File text).
    """
    queryString = data.get("queryString")
    resultArr   = data.get("resultArr")
    worker      = data.get("worker")
    file = TempFileManager.createTempFile("email_extract", ".txt")
    
    f = open(file.getPath(), "w")
    title = None
    for i, loader in enumerate(resultArr):
        if worker.isCancelled():
            return "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i, len(resultArr)))
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
            f.write("%d/%d " % (i + 1, len(resultArr)))
            f.write(" %s" % loader.getTitle().encode("utf-8", "replace"))
            f.write("]\n")
            f.write(text.encode("utf-8", "replace"))
            f.write("\n")
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i + 1, len(resultArr), loader.getUrl())
    f.close()
    return (title, file)
    