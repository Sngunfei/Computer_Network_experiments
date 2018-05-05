package com.syf.gbn;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class testServer {

    public void start() throws IOException {
        DatagramSocket server = new DatagramSocket(10086);
        byte[] bytes = new byte[16];
        DatagramPacket p = new DatagramPacket(bytes, bytes.length);
        server.receive(p);
        System.out.println(p.getLength());
        System.out.println(new String(p.getData(), "UTF-8"));
    }

    public static void main(String[] argv){
        testServer test = new testServer();
        try {
            test.start();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
