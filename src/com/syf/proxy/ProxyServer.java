package com.syf.proxy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class HttpHeader{
    String method;
    URL url;
    InetSocketAddress host;
    String cookie;
    Date last_modified_since;
    public HttpHeader(){
    }
}

class ProxyParam{
    Socket client;
    Socket server;
}

class Cache{
    public static HashMap<URL, File> cache = new HashMap<>();
    public static HashMap<URL, String> time = new HashMap<>();
}

class ProxyThread implements Runnable{

    ProxyParam proxyParam;
    final int MAX_SIZE = 65507;
    public ProxyThread(ProxyParam proxyParam){
        this.proxyParam = proxyParam;
    }

    /*
       server： 代理服务器充当服务器角色，向用户读取请求和发送内容
       client:  代理服务器充当客户端角色，向服务器发送请求和读取内容
       以字节流传输
     */

    @Override
    public void run(){
        try {
            System.out.println("开始访问......");

            // 缓冲区
            byte[] buffer = new byte[MAX_SIZE];

            DataOutputStream sDos = new DataOutputStream(proxyParam.server.getOutputStream());
            DataInputStream sDis = new DataInputStream(proxyParam.server.getInputStream());

            // 从客户端接收访问请求
            int size = sDis.read(buffer, 0, MAX_SIZE);
            if (size <= 0) {
                System.out.println("error");
                return;
            }

            HttpHeader httpHeader = new HttpHeader();
            char[] cacheBuffer = new String(buffer).toCharArray();

            // 解析请求头
            if(ProxyServer.parseHttpHead(cacheBuffer, httpHeader)){
                // 代理服务器访问host
                proxyParam.client = new Socket(httpHeader.host.getAddress(), 80);
                System.out.println("访问 " + httpHeader.url + "成功");
                DataOutputStream cDos = new DataOutputStream(proxyParam.client.getOutputStream());
                DataInputStream cDis = new DataInputStream(proxyParam.client.getInputStream());

                String modifiedStr = "If-Modified-Since: "+ "Sat, 28 Apr 2018 14:26:22 GMT";

                byte[] buffer2 = new byte[size + modifiedStr.length()];
                System.arraycopy(buffer,0, buffer2, 0, size);
                byte[] buffer3 =  modifiedStr.getBytes();
                System.arraycopy(buffer3,0, buffer2, size, buffer3.length);

                System.out.println("内容");
                System.out.println(buffer2.length);
                System.out.println(new String(buffer2));
                // 发送修改后的http头

                cDos.write(buffer2, 0, size);
                cDos.flush();

                // 代理服务器接收host返回信息， 写入缓冲区
                size = cDis.read(buffer, 0, MAX_SIZE);
                if (size <= 0)
                    System.out.println("error");

//                String content = new String(buffer,"utf-8");
//                System.out.println("返回内容");
//                System.out.println(content);
//                int index = content.indexOf("\r\n");
//                String firstLine = content.substring(0, index);
//
//                if(!Cache.cache.containsKey(httpHeader.url)){
//                    writeCache(httpHeader.url, buffer, size);
//                }else if(firstLine.contains("304")){
//                    // 不需要修改, 直接从缓存中读取
//                    buffer = readCache(httpHeader.url);
//                }else if(firstLine.contains("200")){
//                    // 需要修改，重新写入缓存，更新最近时间
//                    updateCache(httpHeader.url, buffer, size);
//                }
//                System.out.println();
                // 客户端从缓冲区读取信息
                sDos.write(buffer, 0, buffer.length);
                sDos.flush();
                cDos.close();
                cDis.close();
            }
            sDos.close();
            sDis.close();
        }catch (IOException e){
            //e.printStackTrace();
        }
    }

    public byte[] readCache(URL url) throws IOException{
        byte[] content = new byte[MAX_SIZE];
        File file = Cache.cache.get(url);
        DataInputStream dis = new DataInputStream(new FileInputStream(file));
        dis.read(content,0, MAX_SIZE);

        dis.close();
        return content;
    }

    public void updateCache(URL url, byte[] buffer, int size) throws IOException{
        File file = Cache.cache.get(url);
        DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
        dos.write(buffer,0, size);


    }
}

public class ProxyServer{
    public final int MAX_SIZE = 65507;
    public final int HTTP_PORT = 80;

    // 代理服务器接收客户端请求
    ServerSocket server;
    InetSocketAddress serverAddr;
    final int port = 10086;

    final int MaxThreadNum = 20;
    ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, MaxThreadNum,
                                            5, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));

    public static final HashSet<String> sensitiveWords = new HashSet<>();
    public static final HashSet<String> fishWords = new HashSet<>();

    static{
        sensitiveWords.add("PORN");
        sensitiveWords.add("JAPAN");
        sensitiveWords.add("FUCK");
        sensitiveWords.add("SEX");
        sensitiveWords.add("FACEBOOK");
        sensitiveWords.add("ms");

        fishWords.add("jwes");
    }

    public boolean initSocket() throws IOException{
        server = new ServerSocket(port);
        if(server == null){
            System.out.println("error");
            return false;
        }
        return true;
    }

    public static boolean filter(String str){
        for(String word : sensitiveWords){
            if(str.contains(word)){
                return true;
            }
        }
        return false;
    }

    public static boolean ban(String str){
        if(str.contains("127.0.0.1"))
            return true;
        return false;
    }

    public static boolean parseHttpHead(char[] buffer, HttpHeader httpHeader) throws MalformedURLException {
        System.out.println("解析http头......");
        String str = new String(buffer);

        String[] lines = str.split("\r\n");
        //System.out.println("共有" + lines.length + "行");
        System.out.println(lines[0]);

        String[] heads = lines[0].split(" ");
        httpHeader.method = heads[0];

        if(filter(str)) {
            System.out.println("拒绝访问 " + heads[1]);
            return false;
        }

        boolean flag = false;
        if(heads[1].contains("jwes")){
            System.out.println("钓鱼：" + heads[1]);
            heads[1] = "http://today.hit.edu.cn/";
            System.out.println("引导至：" + heads[1]);
            flag = true;
        }
        httpHeader.url = new URL(heads[1]);
        httpHeader.host = new InetSocketAddress(httpHeader.url.getHost(), 80);

        for(int i=1; i<lines.length; i++){
            String cur = lines[i];
            if(cur==null || cur.length()==0) continue;
            System.out.println(cur);
            switch (cur.charAt(0)){
                // host
                case 'H':
                    String hostStr = cur.substring(6, cur.length());
                    if(flag)
                        hostStr = "today.hit.edu.cn";
                    httpHeader.host = new InetSocketAddress(hostStr, 10086);
                    break;
                // cookie
                case 'C':
                    if(cur.length() > 8){
                        String header = cur.substring(0,6);
                        if("Cookie".equals(header)){
                            httpHeader.cookie = cur.substring(8, cur.length());
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    public void start() throws IOException{
        System.out.println("开始运行");
        this.initSocket();
        while(true){
            Socket acceptSocket = this.server.accept();
            InetAddress address = acceptSocket.getInetAddress();
//            if(ban(address.getHostAddress())){
//                System.out.println("该IP地址禁止访问！");
//                continue;
//            }
            ProxyParam proxyParam = new ProxyParam();
            proxyParam.server = acceptSocket;
            this.threadPool.execute(new ProxyThread(proxyParam));
        }
    }

    public static void main(String[] argv){
        ProxyServer proxyServer = new ProxyServer();
        try {
            proxyServer.start();
        }catch (IOException e){
            //e.printStackTrace();
        }
    }
}
