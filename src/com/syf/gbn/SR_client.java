package com.syf.gbn;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.Scanner;

/*
    SR-Client
        维护一个接收窗口 win = boolean[], 表示是否接收
        最左的位置称为 rcv_base, 当rcv_base收到后窗口移动，不用操心ack在回去的路上是否丢失
        服务器收到ack后也移动，超时则重传最左边的超时
 */

public class SR_client {

    final int PORT = 10086;
    final String SERVER_IP = "127.0.0.1";
    final int BUFFER_LENGTH = 1026;
    final int SEQ_SIZE = 20;
    final int WINDOW_SIZE = 5;

    boolean[] ack = new boolean[WINDOW_SIZE];
    byte[] content = new byte[65535];  // 文件内容
    int totalSize = 0;

    void printTips() {
        System.out.println("*****************************************");
        System.out.println("| -time to get current time |");
        System.out.println("| -quit to exit client |");
        System.out.println("| -testsr [X] [Y] to test the gbn |");
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

    void sendToUpper(int start) throws UnsupportedEncodingException {
        int cnt = 0;
        for(int i=0; i<WINDOW_SIZE; i++){
            int index = (i+start)%WINDOW_SIZE;
            if(ack[index]) {
                cnt += 1024;
                ack[index] = false;
            } else
                break;
        }
        System.out.println("Send to upper lawyer.");
        System.out.println(new String(content, totalSize, totalSize + cnt, "UTF-8"));
        totalSize += cnt;
    }

    void start() throws IOException, InterruptedException {
        DatagramSocket socket = new DatagramSocket(10010);
        byte[] bytes = new byte[BUFFER_LENGTH];
        DatagramPacket packet = new DatagramPacket(new byte[BUFFER_LENGTH], BUFFER_LENGTH , InetAddress.getByName(SERVER_IP), PORT);
        printTips();

        double packetLossRatio = 0.2;
        double ackLossRatio = 0.2;
        Scanner scan = new Scanner(System.in);
        while(true){
            String cmd = scan.nextLine();
            if("-testsr".equals(cmd)){
                System.out.println("Begin to test SR protocol, please don't abort the process");
                System.out.println("The loss ratio of packet is " + packetLossRatio +
                        ", the loss ratio of ack is " + ackLossRatio);
                int stage = 0;
                int code = 0;
                int seq = 0;
                int rcv_base = 0;
                totalSize = 0;
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
                                buffer[1] = ' ';
                                packet.setData(buffer);
                                socket.send(packet);
                                stage = 1;
                                rcv_base = 0;
                            }
                            break;
                        case 1: // 等待接收数据阶段
                            seq = ((int)buffer[0] + 256) % 256;
                            if(lossInLossRatio(packetLossRatio)){
                                // 模拟packet丢失
                                System.out.println("The packet with a seq of " + seq + " loss.");
                                continue;
                            }
                            System.out.println("recv a packet with a seq of " + seq);
                            if(seq == rcv_base) {
                                ack[rcv_base] = true; // 返回ack
                                sendToUpper(rcv_base);   // rcv_base -> rcv_rightest, 这些数据打包发给应用层
                                rcv_base++;    // 窗口移动
                                rcv_base %= SEQ_SIZE;
                            }else if(seq > rcv_base && !ack[seq]){
                                // 接收数据包，但是窗口不移动
                                ack[seq] = true;
                            }else if(seq < rcv_base){
                                // 之前某个ack丢掉，重新发过来后直接返回ack即可，
                                // do nothing
                            }
                            if(lossInLossRatio(ackLossRatio)){
                                // 模拟ack丢失
                                System.out.println("The ack of " + ++seq + " loss.");
                                continue;
                            }
                            buffer[0] = (byte)(seq);
                            buffer[1] = ' ';
                            packet.setData(buffer);
                            socket.send(packet);
                            break;
                        default:
                            break;
                    }
                    Thread.sleep(500);
                }
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
        SR_client client = new SR_client();
        try{
            client.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
