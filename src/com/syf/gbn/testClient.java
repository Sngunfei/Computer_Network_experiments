package com.syf.gbn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class testClient {

    public void start() throws IOException{
        DatagramSocket client = new DatagramSocket();
        byte[] bytes = "窝草阿萨是巨大受打击按实际第六届奥斯卡来得及看".getBytes();
        DatagramPacket p = new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), 10086);
        client.send(p);
        client.close();
    }

    public static void main(String[] argv){
        testClient test = new testClient();
        try {
            test.start();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
