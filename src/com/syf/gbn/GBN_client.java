package com.syf.gbn;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Random;
import java.util.Scanner;

public class GBN_client {

    final int PORT = 10086;
    final String SERVER_IP = "127.0.0.1";
    final int BUFFER_LENGTH = 1026;
    final int SEQ_SIZE = 20;

    void printTips() {
        System.out.println("*****************************************");
        System.out.println("| -time to get current time |");
        System.out.println("| -quit to exit client |");
        System.out.println("| -testgbn [X] [Y] to test the gbn |");
        System.out.println("*****************************************");
    }

    boolean lossInLossRatio(double lossRatio){
        int lossBound = (int)(lossRatio * 100);
        Random random = new Random(System.currentTimeMillis() % 86237);
        int r = Math.abs(random.nextInt()) % 101;
        if(r <= lossBound)
            return true;
        return false;
    }

    void start() throws IOException, InterruptedException {
        DatagramSocket socket = new DatagramSocket(10010);
        byte[] bytes = new byte[BUFFER_LENGTH];
        DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LENGTH], BUFFER_LENGTH , InetAddress.getByName(SERVER_IP), PORT);
        printTips();

        int ret;
        int interval = 1;
        double packetLossRatio = 0.2;
        double ackLossRatio = 0.2;

        Scanner scan = new Scanner(System.in);
        while(true){
            String cmd = scan.nextLine();
            if("-testgbn".equals(cmd)){
                System.out.println("Begin to test GBN protocol, please don't abort the process");
                System.out.println("The loss ratio of packet is " + packetLossRatio +
                        ", the loss ratio of ack is " + ackLossRatio);
                int waitCount = 0;
                int stage = 0;
                int code = 0;
                int seq = 0;
                int recvSeq = 0;
                int waitSeq = 0;
                int totalsize = 0;
                byte[] content = new byte[65535];
                System.arraycopy(cmd.getBytes(),0, bytes, 0, cmd.getBytes().length);
                packet.setData(bytes);
                socket.send(packet);
                while(true){
                    socket.receive(packet);
                    byte[] buffer = packet.getData();
                    String str = new String(buffer,"UTF-8");
                    str = str.trim();
                    if("done".equals(str.substring(0,4))) {
                        System.out.println("传输结束！");
                        break;
                    }
                    switch (stage){
                        case 0: // 等待握手阶段
                            code = ((int)buffer[0] + 256) % 256;
                            if(code == 205){
                                System.out.println("Ready for file transmission.");
                                buffer[0] = (byte)200;
                                buffer[1] = '\0';
                                packet.setData(buffer);
                                socket.send(packet);
                                stage = 1;
                                recvSeq = 0;
                                waitSeq = 0;
                            }
                            break;
                        case 1: // 等待接收数据阶段
                            seq = ((int)buffer[0] + 256) % 256;
                            if(lossInLossRatio(packetLossRatio)){
                                System.out.println("The packet with a seq of " + seq + " loss.");
                                continue;
                            }
                            System.out.println("recv a packet with a seq of " + seq);
                            if(seq == waitSeq) {
                                waitSeq++;
                                if (waitSeq == 20)
                                    waitSeq = 0;
                                System.out.println(new String(buffer, 1, packet.getLength() - 2, "UTF-8"));
                                //System.out.println(buffer.length);
                                //System.arraycopy(buffer,1, content, seq*1024, packet.getLength()-2);
                                //System.out.println(new String(buffer, 1, packet.getLength()-2, "UTF-8"));
                                //totalsize += packet.getLength()-2;
                                buffer[0] = (byte) (waitSeq);
                                buffer[1] = '\0';
                                recvSeq = seq;

                                if (lossInLossRatio(ackLossRatio)) {
                                    System.out.println("The ack of " + waitSeq + " loss.");
                                    continue;
                                }
                                packet.setData(buffer);
                                socket.send(packet);
                                System.out.println("send a ack of " + waitSeq);
                            }
                                break;

                        default:
                            break;
                    }
                    Thread.sleep(500);
                }
                System.out.println(new String(content,0, totalsize, "UTF-8"));
            }else {
                System.out.println(cmd);
                System.arraycopy(cmd.getBytes(), 0, bytes, 0, cmd.getBytes().length);
                packet.setData(bytes);
                socket.send(packet);
                socket.receive(packet);
                String str = new String(packet.getData(), "UTF-8");
                str = str.trim();
                System.out.println(str);
                if ("Good bye!".equals(str))
                    break;
                printTips();
            }
        }
        socket.close();
    }

    public static void main(String[] argv){
        GBN_client client = new GBN_client();
        try{
            client.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
