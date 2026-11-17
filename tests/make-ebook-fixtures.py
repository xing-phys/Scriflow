#!/usr/bin/env python3
"""Original tiny books plus checksum-pinned upstream binary format test samples."""
from pathlib import Path
from io import BytesIO
from urllib.request import urlopen
from zipfile import ZipFile as BaseZipFile, ZipInfo, ZIP_DEFLATED, ZIP_STORED
import hashlib
import json
import struct
import zlib

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'build/ebook-fixtures';OUT.mkdir(parents=True,exist_ok=True)

class ZipFile(BaseZipFile):
    def writestr(self,name,data,**kwargs):
        if isinstance(name,str):name=ZipInfo(name,date_time=(2020,1,1,0,0,0))
        return super().writestr(name,data,**kwargs)


def png(width,height):
    def chunk(name,data): return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    data=b''.join(b'\0'+b''.join(bytes((0,0,0)) if (x//20+y//20)%2 else bytes((255,255,255)) for x in range(width)) for y in range(height))
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b'')


picture=png(320,480)
with ZipFile(OUT/'quantum.epub','w') as archive:
    archive.writestr('mimetype','application/epub+zip',compress_type=ZIP_STORED)
    archive.writestr('META-INF/container.xml','''<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>''')
    archive.writestr('OPS/book.opf','''<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:scriflow:ebook-test-1</dc:identifier><dc:title>Quantum Reading Fixture</dc:title><dc:creator>Scriflow tests</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/><item id="image" href="image.png" media-type="image/png"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>''')
    archive.writestr('OPS/nav.xhtml','''<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="a.xhtml">Spin chains</a></li><li><a href="b.xhtml">Quantum information</a></li></ol></nav></body></html>''')
    for name,title in [('a','Spin chains'),('b','Quantum information')]:
        paragraphs=''.join('<p id="p'+str(i)+'">Paragraph '+str(i)+'. The uncertainty principle connects quantum fluctuations and measurement. We study non-equilibrium quantum dynamics, entanglement and transport in spin chains. 中文阅读测试。</p>' for i in range(80))
        content='<html xmlns="http://www.w3.org/1999/xhtml"><head><title>'+title+'</title><style>p,span{font-family:monospace;font-size:13px}</style></head><body><h1>'+title+'</h1><p id="anchor">The uncertainty principle remains linked to this passage after changing fonts.</p><p><a href="b.xhtml#p20">Linked passage</a></p><math xmlns="http://www.w3.org/1998/Math/MathML"><mi>E</mi><mo>=</mo><mi>m</mi><msup><mi>c</mi><mn>2</mn></msup></math><img src="image.png"/>'+paragraphs+'</body></html>'
        archive.writestr('OPS/'+name+'.xhtml',content,compress_type=ZIP_DEFLATED)
    archive.writestr('OPS/image.png',picture)
with ZipFile(OUT/'scripted.epub','w') as archive,ZipFile(OUT/'quantum.epub') as original:
    for entry in original.infolist():
        data=original.read(entry.filename)
        if entry.filename=='OPS/a.xhtml':
            data=data.replace(b'<h1>',b'<script>parent.__bookScriptExecuted=true;fetch("https://example.org/ebook-leak")</script><img src="https://example.org/ebook-leak.png" onerror="parent.__bookScriptExecuted=true"/><h1>',1)
        archive.writestr(entry,data)
with ZipFile(OUT/'comic.cbz','w') as archive:
    archive.writestr('page1.png',picture);archive.writestr('page2.png',png(360,480))
(OUT/'quantum.fb2').write_text('''<?xml version="1.0" encoding="utf-8"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info><genre>science</genre><author><first-name>Scriflow</first-name><last-name>tests</last-name></author><book-title>Quantum FB2 Fixture</book-title><lang>en</lang></title-info></description><body><section id="chapter1"><title><p>Spin dynamics</p></title><p>The uncertainty principle links measurement and fluctuations. 中文阅读测试。</p></section><section id="chapter2"><title><p>Entanglement</p></title><p>The reduced density matrix encodes local information.</p></section></body></FictionBook>''')
(OUT/'quantum.txt').write_text(('The uncertainty principle connects measurement and quantum fluctuations. 中文阅读测试。\n\n')*500)
(OUT/'quantum.html').write_text('<!doctype html><html><head><title>Quantum HTML Fixture</title></head><body><h1>Quantum dynamics</h1><p>The uncertainty principle is a central result.</p><script>parent.__bookScriptExecuted=true</script></body></html>')
for sample in json.loads((ROOT/'tests/ebook-samples.json').read_text())['samples']:
    destination=OUT/sample['name']
    if not destination.is_file() or hashlib.sha256(destination.read_bytes()).hexdigest()!=sample['sha256']:
        data=urlopen(sample['url'],timeout=90).read()
        if hashlib.sha256(data).hexdigest()!=sample['sha256']:raise SystemExit('Upstream fixture changed: '+sample['name'])
        destination.write_bytes(data)
    if sample.get('mobiVersion')==8 and not sample.get('encryption'):
        (OUT/'sample-kf8.azw3').write_bytes(destination.read_bytes())
print('Prepared checksum-pinned ebook fixtures:',OUT)

with ZipFile(OUT/'quantum.fb2.zip','w') as archive:
    archive.writestr('quantum.fb2',(OUT/'quantum.fb2').read_bytes(),compress_type=ZIP_DEFLATED)
with ZipFile(OUT/'encrypted.epub','w') as archive,ZipFile(OUT/'quantum.epub') as original:
    for entry in original.infolist():archive.writestr(entry,original.read(entry.filename))
    archive.writestr('META-INF/encryption.xml',"<encryption xmlns='urn:oasis:names:tc:opendocument:xmlns:container' xmlns:e='http://www.w3.org/2001/04/xmlenc#'><e:EncryptedData><e:EncryptionMethod Algorithm='http://www.w3.org/2001/04/xmlenc#aes128-cbc'/><e:CipherData><e:CipherReference URI='OPS/a.xhtml'/></e:CipherData></e:EncryptedData></encryption>")

# Trailing padding leaves the bundled IFF document intact while exercising the
# large-file random-access branch; this is not a large-page-count benchmark.
with (OUT/'large-bundled.djvu').open('wb') as output:
    output.write((OUT/'DjVu3Spec_bundled.djvu').read_bytes())
    output.truncate(34*1024*1024)
