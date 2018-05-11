package com.syf.gbn;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class GbnSxClient {

    final int PORT = 10086;
    final String SERVER_IP = "127.0.0.1";
    final int BUFFER_LENGTH = 1026;
    final int SEQ_SIZE = 20;
    final int SEND_WINDOW_SIZE = 10;

    int curAck;
    int curSeq;
    int totalSeq;
    int waitCount;
    boolean ack[] = new boolean[SEQ_SIZE];

    // 查询当前序列号是否可用。
    boolean seqIsAvailable(){
        int step;
        step = curSeq - curAck;
        step = step >= 0 ? step: step + SEQ_SIZE;
        if(step >= SEND_WINDOW_SIZE)
            return false;
        if(ack[curSeq])
            return true;
        return false;
    }

    void timeoutHandler(){
        System.out.println("Timeout error!");
        int index;
        for(int i=0; i<SEND_WINDOW_SIZE; i++){
            index = (i + curAck) % SEQ_SIZE;
            ack[index] = true;
        }
        //totalSeq -= SEND_WINDOW_SIZE;
        int step = curSeq-curAck;
        step = step >=0 ? step: step + SEQ_SIZE;
        totalSeq -= step;
        curSeq = curAck;
    }

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

    void ackHandler(int seq){
        seq--;
        System.out.println("Recv a ack of " + seq);
        if(curAck <= seq){
            for(int i=curAck; i<=seq; i++){
                ack[i] = true;
            }
            curAck = (seq + 1) % SEQ_SIZE;
        }else{
            for(int i=curAck; i<SEQ_SIZE; i++){
                ack[i] = true;
            }
            for(int i=0; i<=seq; i++){
                ack[i] = true;
            }
            curAck = seq + 1;
        }
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
                                waitSeq = (waitSeq + 1) % SEQ_SIZE;
                                System.out.println(new String(buffer, 1, packet.getLength() - 2, "UTF-8"));
                                buffer[0] = (byte) (waitSeq);
                                buffer[1] = ' ';
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
            }else if("-sendData".equals(cmd)){
                // 发送数据
                System.out.println("客户端向服务器发数据");
                System.arraycopy(cmd.getBytes(),0, bytes, 0, cmd.getBytes().length);
                packet.setData(bytes);
                socket.send(packet);
                byte buffer[] = new byte[BUFFER_LENGTH];
                int stage = 0;
                boolean runFlag = true;
                while(runFlag){
                    switch (stage){
                        case 0:
                            socket.receive(packet);
                            buffer = packet.getData();
                            if(((int)buffer[0] == 66)){
                                buffer[0] = (byte)88;
                                buffer[1] = ' ';
                                packet.setData(buffer);
                                socket.send(packet);
                                stage = 1;
                                curAck = curSeq = totalSeq = waitCount = 0;
                                Arrays.fill(ack, true);
                            }
                            break;
                        case 1:
                            if(seqIsAvailable()){
                                System.out.println("开始传输第" + (curSeq+1) + "个");
                                buffer[0] = (byte)(curSeq);
                                ack[curSeq] = false;
                                packet.setData(buffer);
                                socket.send(packet);
                                curSeq = (curSeq + 1) % SEQ_SIZE;
                                System.out.println("curSeq: " + curSeq);
                                System.out.println("curAck: " + curAck);
                                totalSeq ++;
                                Thread.sleep(500);
                            }
                            socket.setSoTimeout(250);
                            try {
                                socket.receive(packet);
                            } catch (InterruptedIOException e) {  // who cares?
                                waitCount ++;
                                socket.setSoTimeout(0);
                                if(waitCount >= 20){
                                    // ...
                                    timeoutHandler();
                                    waitCount = 0;
                                }
                                break;
                            }
                            buffer = packet.getData();
                            int seq = ((int) buffer[0] + 256) % 256;
                            System.out.println("收到第" + (seq-1) + "个ack.");
                            ackHandler(seq);
                            waitCount = 0;
                            if (totalSeq >= 20) {
                                System.out.println("传输完成");
                                System.out.println("开始接收");
                                System.arraycopy("done".getBytes(), 0, buffer, 0, "done".getBytes().length);
                                packet.setData(buffer);
                                socket.send(packet);
                                runFlag = false;
                                stage = 0;
                            }
                            Thread.sleep(500);
                    }
                    //
                }
            }
            else {
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
        GbnSxClient client = new GbnSxClient();
        try{
            client.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
