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
    return "email_extract"

def ext_description():
    """
    Returns this extension's summary.
    """
    return "Extract links that has mail/tel/sms/mms schemes."

      
def call(API, data):
    """
    Main function of the extension.
    This function must be returns (String title, File text).
    """
    queryString = data.get("queryString")
    resultArr   = data.get("resultArr")
    worker      = data.get("worker")
    file = TempFileManager.createTempFile("email_extract", ".txt")
    mailSet = set([])
    for i, loader in enumerate(resultArr):
        if worker.isCancelled():
            return "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i, len(resultArr)))
        try:
            ins    = loader.getContent()
            header = loader.getHeader()
            charset = DomUtils.guessCharset(header, ins)
            try:
                ins.close()
            except:
                pass
            ins = loader.getContent()
            
            for mail in DomUtils.extractMails(loader.getUrl(), ins, charset):
                if not mail in mailSet:
                    mailSet.add(mail)
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
    return ("email adresses", file)
    