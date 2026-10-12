package com.qianxia.backup;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.net.*;
import com.sun.net.httpserver.*;

public final class CoreTest {
    static int checks=0;
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;System.out.println("PASS "+s);}
    interface Op {void run()throws Exception;}
    static void fails(Op op,String label)throws Exception{try{op.run();throw new AssertionError("Expected failure: "+label);}catch(IOException e){check(true,label);}}
    public static void main(String[]args)throws Exception{
        File root=Files.createTempDirectory("qianxia-tests-").toFile();char[]password="test-pass-中国-123".toCharArray();Random random=new Random(19);
        try{
            for(int size:new int[]{0,1,1024,1048576,2097173}){
                File original=new File(root,"source"),enc=new File(root,"backup.qxb"),clear=new File(root,"restore");byte[]bytes=new byte[size];random.nextBytes(bytes);Files.write(original.toPath(),bytes);
                BackupCrypto.encrypt(original,enc,password,null);BackupCrypto.decrypt(enc,clear,password,null);check(Arrays.equals(bytes,Files.readAllBytes(clear.toPath())),"encrypted round trip "+size+" bytes");
                byte[]first=Files.readAllBytes(enc.toPath());BackupCrypto.encrypt(original,enc,password,null);check(!Arrays.equals(first,Files.readAllBytes(enc.toPath())),"fresh salt and nonce "+size);
            }
            File original=new File(root,"source"),enc=new File(root,"backup.qxb"),clear=new File(root,"restore");
            fails(()->BackupCrypto.decrypt(enc,clear,"wrong-password".toCharArray(),null),"wrong password rejected");check(!clear.exists(),"no plaintext output after password failure");
            byte[]good=Files.readAllBytes(enc.toPath());byte[]bad=good.clone();bad[BackupCrypto.HEADER+15]^=1;Files.write(enc.toPath(),bad);fails(()->BackupCrypto.decrypt(enc,clear,password,null),"modified ciphertext rejected");
            bad=Arrays.copyOf(good,good.length-20);Files.write(enc.toPath(),bad);fails(()->BackupCrypto.decrypt(enc,clear,password,null),"truncated terminal record rejected");
            bad=Arrays.copyOf(good,good.length+1);Files.write(enc.toPath(),bad);fails(()->BackupCrypto.decrypt(enc,clear,password,null),"trailing data rejected");
            bad=good.clone();bad[32]^=1;Files.write(enc.toPath(),bad);fails(()->BackupCrypto.decrypt(enc,clear,password,null),"header tamper rejected");
            Files.write(enc.toPath(),good);fails(()->BackupCrypto.encrypt(original,enc,password,(n,t)->{throw new InterruptedIOException("cancel");}),"cancellation aborts encryption");check(!enc.exists(),"cancelled encrypted output removed");
            File zip=new File(root,"evil.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){out.putNextEntry(new ZipEntry("../outside"));out.write(1);out.closeEntry();}
            File extracted=new File(root,"extracted");fails(()->FileOps.unpack(zip,extracted),"Zip Slip blocked");check(!new File(root,"outside").exists()&&!extracted.exists(),"unsafe archive cleaned");
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){out.putNextEntry(new ZipEntry("exports/0.data"));out.write("中文导出文件".getBytes("UTF-8"));out.closeEntry();}FileOps.unpack(zip,extracted);check(new File(extracted,"exports/0.data").length()>0,"normal archive extracted");
            dav(root);
            System.out.println("ALL "+checks+" CHECKS PASSED");
        }finally{FileOps.delete(root);}
    }
    static void dav(File root)throws Exception{
        Map<String,byte[]>store=Collections.synchronizedMap(new HashMap<>());HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/dav/",e->{try{
            if(!e.getRequestHeaders().getFirst("Authorization").equals("Basic dXNlcjpwYXNz")){e.sendResponseHeaders(401,-1);return;}
            String name=e.getRequestURI().getPath().substring(5),method=e.getRequestMethod();byte[]body;
            switch(method){
                case "PROPFIND":StringBuilder xml=new StringBuilder("<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>");for(Map.Entry<String,byte[]>item:store.entrySet())xml.append("<d:response><d:href>/dav/").append(item.getKey()).append("</d:href><d:propstat><d:prop><d:getcontentlength>").append(item.getValue().length).append("</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");xml.append("<d:response><d:href>http://evil.invalid/dav/evil.qxb</d:href></d:response></d:multistatus>");body=xml.toString().getBytes("UTF-8");e.sendResponseHeaders(207,body.length);e.getResponseBody().write(body);break;
                case "PUT":ByteArrayOutputStream b=new ByteArrayOutputStream();FileOps.copy(e.getRequestBody(),b,FileOps.MAX_BYTES,null);store.put(name,b.toByteArray());e.sendResponseHeaders(201,-1);break;
                case "GET":body=store.get(name);if(body==null)e.sendResponseHeaders(404,-1);else{e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);}break;
                case "DELETE":store.remove(name);e.sendResponseHeaders(204,-1);break;
                case "MOVE":String dest=new URI(e.getRequestHeaders().getFirst("Destination")).getPath().substring(5);if(store.containsKey(dest)){e.sendResponseHeaders(412,-1);break;}store.put(dest,store.remove(name));e.sendResponseHeaders(201,-1);break;
                default:e.sendResponseHeaders(405,-1);
            }
        }catch(Exception x){e.sendResponseHeaders(500,-1);}finally{e.close();}});
        server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/dav/";
            fails(()->new DavClient(base,"user","pass",false),"HTTP requires explicit opt-in");DavClient dav=new DavClient(base,"user","pass",true);dav.test();check(store.isEmpty(),"connection test cleans its own test file");
            File file=new File(root,"迁匣-测试.qxb");Files.write(file.toPath(),"encrypted-content-placeholder".getBytes("UTF-8"));dav.upload(file,null);check(store.containsKey(file.getName())&&!store.containsKey(file.getName()+".uploading"),"upload verified and published using MOVE");
            List<DavClient.Item>items=dav.list();check(items.size()==1&&items.get(0).name.equals(file.getName()),"WebDAV list retains Unicode and rejects foreign origin");
            File downloaded=new File(root,"download.qxb");dav.download(items.get(0).name,downloaded,null);check(FileOps.sha(file).equals(FileOps.sha(downloaded)),"download matches uploaded SHA-256");
            fails(()->dav.download(items.get(0).name,downloaded,null),"download refuses local overwrite");
            fails(()->dav.download("../evil.qxb",new File(root,"bad"),null),"remote filename traversal blocked");
            fails(()->new DavClient(base,"user","wrong",true).list(),"unauthorized account rejected");
        }finally{server.stop(0);}
    }
}
