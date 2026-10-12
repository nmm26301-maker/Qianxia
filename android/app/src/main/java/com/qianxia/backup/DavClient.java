package com.qianxia.backup;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import okhttp3.*;
import okio.BufferedSink;

/** Minimal WebDAV adapter. Never follows redirects carrying stored credentials. */
public final class DavClient {
    private final OkHttpClient client;
    private final HttpUrl root;
    private final String auth;
    public static final class Item {public String name;public long size;public Item(String n,long s){name=n;size=s;}}
    public DavClient(String url,String user,String password,boolean allowHttp)throws IOException{
        root=HttpUrl.parse(url.endsWith("/")?url:url+"/");
        if(root==null||!root.username().isEmpty()||!root.password().isEmpty())throw new IOException("请输入有效的 WebDAV 地址，账号请填在账号栏");
        if(!root.isHttps()&&!allowHttp)throw new IOException("请使用 HTTPS；局域网 HTTP 需单独确认开启");
        auth=Credentials.basic(user,password,java.nio.charset.StandardCharsets.UTF_8);
        client=new OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    }
    private HttpUrl path(String name)throws IOException{
        if(name==null||name.isEmpty()||name.contains("/")||name.contains("\\")||name.equals(".")||name.equals(".."))throw new IOException("网盘文件名非法");
        return root.newBuilder().addPathSegment(name).build();
    }
    private Response request(String method,HttpUrl url,RequestBody body,String... headers)throws IOException{
        Request.Builder b=new Request.Builder().url(url).header("Authorization",auth).header("User-Agent","Qianxia/1.0").method(method,body);
        for(int i=0;i<headers.length;i+=2)b.header(headers[i],headers[i+1]);
        return client.newCall(b.build()).execute();
    }
    private static void require(Response r,int... codes)throws IOException{for(int c:codes)if(r.code()==c)return;throw new IOException("网盘返回 HTTP "+r.code()+"，请检查账号、目录权限与服务器地址");}
    public List<Item> list()throws Exception{
        String xml="<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        try(Response r=request("PROPFIND",root,RequestBody.create(xml,MediaType.get("application/xml; charset=utf-8")),"Depth","1")){
            require(r,207);if(r.body()==null||r.body().contentLength()>4*1024*1024)throw new IOException("网盘目录列表异常");
            ByteArrayOutputStream raw=new ByteArrayOutputStream();FileOps.copy(r.body().byteStream(),raw,4*1024*1024,null);String source=new String(raw.toByteArray(),"UTF-8");
            // Reject DTD/entities before parsing; also configure JVM parser protections when available.
            if(source.toUpperCase(Locale.US).contains("<!DOCTYPE")||source.toUpperCase(Locale.US).contains("<!ENTITY"))throw new IOException("网盘返回了不安全的目录内容");
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
            try{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}
            Document doc=f.newDocumentBuilder().parse(new ByteArrayInputStream(raw.toByteArray()));
            NodeList responses=doc.getElementsByTagNameNS("DAV:","response");List<Item> found=new ArrayList<>();
            for(int i=0;i<responses.getLength();i++){
                Element e=(Element)responses.item(i);NodeList href=e.getElementsByTagNameNS("DAV:","href");if(href.getLength()!=1)continue;
                HttpUrl u=root.resolve(href.item(0).getTextContent().trim());if(u==null||!u.host().equals(root.host())||u.port()!=root.port()||!u.scheme().equals(root.scheme()))continue;
                List<String> base=root.pathSegments(),parts=u.pathSegments();
                if(parts.size()!=base.size()||!parts.subList(0,parts.size()-1).equals(base.subList(0,base.size()-1)))continue;
                String name=parts.get(parts.size()-1);if(!name.endsWith(".qxb")||name.contains("/")||e.getElementsByTagNameNS("DAV:","collection").getLength()>0)continue;
                NodeList lengths=e.getElementsByTagNameNS("DAV:","getcontentlength");long size=-1;try{if(lengths.getLength()>0)size=Long.parseLong(lengths.item(0).getTextContent().trim());}catch(NumberFormatException ignored){}
                found.add(new Item(name,size));
            }
            Collections.sort(found,(a,b)->b.name.compareTo(a.name));return found;
        }
    }
    public void test()throws Exception{
        list();String name="qianxia-connection-test-"+UUID.randomUUID()+".tmp";byte[] check="qianxia write-read check".getBytes("UTF-8");boolean created=false;
        try{
            try(Response r=request("PUT",path(name),RequestBody.create(check,MediaType.get("application/octet-stream")))){require(r,200,201,204);created=true;}
            try(Response r=request("GET",path(name),null)){require(r,200);ByteArrayOutputStream out=new ByteArrayOutputStream();FileOps.copy(r.body().byteStream(),out,256,null);if(!Arrays.equals(check,out.toByteArray()))throw new IOException("网盘读写校验不一致");}
        }finally{if(created)try(Response r=request("DELETE",path(name),null)){require(r,200,204,404);}}
    }
    public void upload(File file,BackupCrypto.Progress progress)throws Exception{
        String pending=file.getName()+".uploading";HttpUrl temp=path(pending),dest=path(file.getName());
        RequestBody body=new RequestBody(){public MediaType contentType(){return MediaType.get("application/octet-stream");}public long contentLength(){return file.length();}public void writeTo(BufferedSink sink)throws IOException{try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;long done=0;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("上传已停止");sink.write(b,0,n);done+=n;if(progress!=null)progress.update(done,file.length());}}}};
        try(Response r=request("PUT",temp,body)){require(r,200,201,204);}
        // Verify the actual remote bytes before publishing a recoverable snapshot.
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(Response r=request("GET",temp,null)){require(r,200);try(InputStream in=r.body().byteStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){total+=n;if(total>file.length())throw new IOException("网盘文件长度不一致");digest.update(b,0,n);if(progress!=null)progress.update(total,file.length());}}}
        if(total!=file.length()||!FileOps.hex(digest.digest()).equals(FileOps.sha(file)))throw new IOException("云端校验失败，本地备份已保留");
        try(Response r=request("MOVE",temp,null,"Destination",dest.toString(),"Overwrite","F")){require(r,201,204);}
    }
    public void download(String name,File target,BackupCrypto.Progress progress)throws Exception{
        File partial=new File(target.getPath()+".part");boolean ok=false;
        try(Response r=request("GET",path(name),null)){
            require(r,200);long length=r.body().contentLength();if(length>FileOps.MAX_BYTES)throw new IOException("备份过大");
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(partial))){FileOps.copy(r.body().byteStream(),out,FileOps.MAX_BYTES,(done,max)->{if(progress!=null)progress.update(done,length);});}
            if(length>=0&&partial.length()!=length)throw new IOException("下载不完整");
            if(target.exists())throw new IOException("本地已存在同名备份，请从本地记录打开");
            if(!partial.renameTo(target))throw new IOException("无法保存下载的备份");ok=true;
        }finally{if(!ok)partial.delete();}
    }
}
