#!-*- coding:utf-8 -*-

"""
This is a sample script of CQPad render-extension.
"""

def ext_name():
    """
    Returns this extension's name.
    """
    return "text_extract_for_fbreader"

def ext_description():
    """
    Returns this extension's summary.
    """
    return u"HTMLからテキストを抽出してFBReaderで見やすいようにする"

def call(API):
    data = API.getData()
    results = data.get("results") #result of query
    worker  = data.get("worker")  #swing worker thread
    query   = data.get("query")   #query string
    
    size = len(results)
    file = API.createTemporaryFile("txt")
    filename = ""
    f = open(file.getPath(), "w")
    for i, loader in enumerate(results):
        if worker.isCancelled():
            return "CANCELLED"
        worker.publish("Rendering... %d/%d" % (i+1, size))
        try:
            title = loader.getTitle().strip()
            if title.endswith(u" - カクヨム"):
                title = title[:-7]
            if title.endswith(u" - テキストファイルダウンロード"):
                title = title[:-17]
            if title.startswith(u"暁 〜小説投稿サイト〜:"):
                title = title[12:]
            url = loader.getUrl().strip()
            if not filename:
                filename = "%s.txt" % title
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
            f.write("-" * 40 + "\n")
            f.write("%s\n"    % url.encode("utf-8", "replace"))
            f.write("[%d/%d]" % (i+1, size))
            f.write(" %s\n"   % title.encode("utf-8", "replace"))
            f.write(text.encode("utf-8", "replace"))
            f.write("\n")
        except:
            print "error occurred inside a render plugin! (%d/%d) url: %s" % (i+1, size, loader.getUrl())
    f.close()
    return (filename, file)
    
