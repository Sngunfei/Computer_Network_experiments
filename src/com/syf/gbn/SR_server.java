//package com.syf.gbn;
//
//import java.io.*;
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Date;
//
//public class SR_server {
//    final static int PORT = 10086;
//    final static int BUFFER_LENGTH = 1026; // seq + 1024bytes + EOF0
//
//    final static int SEND_WINDOW_SIZE = 10;  // 发送窗口大小
//    final static int SEQ_SIZE = 32;     // 序列号个数
//    final static int TIMEOUT = 10;
//
//    int[] ack = new int[SEQ_SIZE];
//    // ack数组里三种状态：
//    // 0，当前序列号可用
//    // 1，当前序列号已发送，未收到ack
//    // 2, 当前序列号已发送，已收到ack
//    // 相当于把ack=true的情况分为了两种
//    // 当收到send_base的ack时，要往前滑动窗口，只有在状态=2的情况下才滑动，为0时不滑动
//    int[] timer = new int[SEQ_SIZE];
//    int send_base = 0;
//
//    boolean reSendFlag = false;
//    int curSeq;
//    int preSeq;
//    int totalSeq;
//    int totalPacket;
//
//    // 查询当前序列号是否可用。
//    boolean seqIsAvailable(){
//        if(totalSeq >= totalPacket)
//            return false;
//        int step;
//        step = curSeq - send_base;
//        step = step >= 0 ? step: step + SEQ_SIZE;
//        if(step >= SEND_WINDOW_SIZE)
//            return false;
//        if(ack[curSeq] == 0)
//            return true;
//        return false;
//    }
//
//    // 超时重传，只传第一个超时的
//    void timeoutHandler(){
//        int index = 0;
//        for(int i = 0, len = SEQ_SIZE; i < len; i++){
//            index = (send_base + i) % SEQ_SIZE;
//            if(ack[index] == 1 && timer[index] >= TIMEOUT)
//                break;
//        }
//        ack[index] = 0;  // 设置为待发状态
//        timer[index] = 0;
//        preSeq = curSeq;  // 把正常情况下的下一个序列号存起来，先发超时的包
//        curSeq = index;   // 下一次发超时的包
//        reSendFlag = true;
//        totalSeq--;
//    }
//
//
//    void ackHandler(int seq){
//        ack[seq] = 2;
//        while(ack[send_base] == 2) {    // 滑动窗口
//            ack[send_base] = 0;
//            send_base = (send_base + 1) % SEQ_SIZE;
//        }
//    }
//
//    byte[] readIn() throws IOException{
//        byte[] content = new byte[65536];
//        File file = new File("C:\\Users\\86234\\workspace\\Network_experiment\\src\\com\\syf\\gbn\\file.txt");
//        DataInputStream br = new DataInputStream(new FileInputStream(file));
//        int size = br.read(content,0, 65535);
//        byte[] content2 = new byte[size];
//        System.arraycopy(content,0,content2,0, size);
//        return content2;
//    }
//
//    byte[] getCurTime(){
//        Date day=new Date();
//        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        return df.format(day).getBytes();
//    }
//
//    boolean isAllAcked(){
//        for(int i=0; i<SEND_WINDOW_SIZE; i++)
//            if(ack[i] != 0)
//                return false;
//        return true;
//    }
//
//
//    void start() throws IOException, InterruptedException {
//        DatagramSocket server = new DatagramSocket(PORT);
//        byte[] buffer = new byte[BUFFER_LENGTH];
//        DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
//        byte[] content = readIn();
//        totalPacket = (int)Math.ceil((double)content.length/1024); // 总共要发的包数
//
//        while(true){
//            System.out.println("start....");
//            server.setSoTimeout(0);
//            server.receive(packet);
//            String str = new String(packet.getData(),"UTF-8");
//            System.out.println(str);
//            if("-time".equals(str.substring(0,5))){
//                System.out.println("recv from client: -time");
//                byte[] time = getCurTime();
//                buffer = new byte[BUFFER_LENGTH];
//                System.arraycopy(time, 0, buffer, 0, time.length);
//                packet.setData(buffer);
//                server.send(packet);
//            }else if("-quit".equals(str.substring(0,5))){
//                buffer = new byte[BUFFER_LENGTH];
//                byte[] bye = "Good bye!".getBytes();
//                System.arraycopy(bye, 0, buffer, 0, bye.length);
//                packet.setData(buffer);
//                server.send(packet);
//                break;
//            }else if("-testsr".equals(str.substring(0,7))){
//                // 进入gbn测试阶段
//                // 首先server（0状态）向client发送205状态码, server进入1状态
//                // server等待client回复200状态码，如果收到了server进入2状态，开始传输文件，否则等待超时
//
//                boolean timeError = false;
//                System.out.println("Begain to test SR_client protocol,please don't abort the process.");
//                System.out.println("Shake hands stage");
//                int stage = 0;
//                boolean runFlag = true;
//                send_base = 0;
//                Arrays.fill(ack, 0);   // 初始化ack和timer数组
//                Arrays.fill(timer, 0);
//                while(runFlag){
//                    switch (stage){
//                        case 0: // 发送205状态码
//                            buffer[0] = (byte)205;
//                            packet.setData(buffer);
//                            server.send(packet);
//                            Thread.sleep(100);
//                            stage = 1;
//                            break;
//                        case 1: // 等待200状态码
//                            server.setSoTimeout(5000);
//                            try {
//                                server.receive(packet);
//                            } catch (InterruptedIOException e) {
//                                timeError = true;
//                            }
//                            if(timeError){
//                                System.out.println("shake hands time out error");
//                                runFlag = false;
//                                timeError = false;
//                                break;
//                            }
//                            if (((int)buffer[0]+256)%256 == 200) {
//                                System.out.println("Begin a file transfer");
//                                System.out.println("File size is " + content.length + "B, each packet is 1024B and packet "
//                                        + "total number is " + totalPacket);
//                                send_base = curSeq = totalSeq = 0;
//                                stage = 2;
//                            }
//                            break;
//                        case 2: // 传输阶段
//                            if(seqIsAvailable()){
//                                System.out.println("开始传输packet " + curSeq);
//                                System.out.println("send_base: " + send_base);
//                                buffer[0] = (byte)curSeq;
//                                ack[curSeq] = 1;        // false表示等待这个ack
//                                timer[curSeq] = 0;          // 开始计时
//                                int left_size = content.length - totalSeq*1024;
//                                if(left_size > 0 && left_size < 1024) {
//                                    System.arraycopy(content, totalSeq * 1024, buffer, 1, left_size);
//                                    for(int i=left_size+2; i<BUFFER_LENGTH; i++)
//                                        buffer[i] = ' ';
//                                } else if(left_size > 0) {
//                                    System.arraycopy(content, totalSeq * 1024, buffer, 1, 1024);
//                                    left_size = 1024;
//                                }
//                                //System.out.println(new String(buffer,1,left_size, "UTF-8"));
//                                packet.setData(buffer);
//                                server.send(packet);
//                               // System.out.println("send to：" + packet.getAddress() + ", size: " + packet.getLength()) ;
//
//                                // 根据reSendFlag判断本次发送是正常发送or超时发送，如果超时，那么就需要跳转到上一次要正常发送的位置
//                                if(reSendFlag) {
//                                    curSeq = preSeq;
//                                    reSendFlag = false;  // 发送完后 重发标志置位
//                                }
//                                else
//                                    curSeq = (curSeq + 1) % SEQ_SIZE;
//                                totalSeq ++;
//                                Thread.sleep(500);
//                            }
//                            server.setSoTimeout(250);
//                            boolean timeOutFlag = false;
//                            try {
//                                server.receive(packet);
//                            } catch (InterruptedIOException e) {
//                                for(int i=0; i<SEQ_SIZE; i++){
//                                    if(ack[i] == 1){
//                                        // 等待ack的位置
//                                        timer[i]++;
//                                        if(timer[i] >= TIMEOUT){
//                                            timeOutFlag = true;
//                                        }
//                                    }
//                                }
//                                if(!timeOutFlag)
//                                    break;
//                            }
//                            if(timeOutFlag){
//                                timeoutHandler();
//                                break;
//                            }
//                            buffer = packet.getData();
//                            int seq = ((int) buffer[0] + 256) % 256;
//                            System.out.println("收到第" + --seq + "个ack.");
//                            ackHandler(seq);
//                            timer[seq] = 0;  // 收到ack，该位置的计时器淸0
//                            if (totalSeq >= totalPacket && isAllAcked()) {
//                                System.out.println("传输完成");
//                                System.arraycopy("done".getBytes(), 0, buffer, 0, "done".getBytes().length);
//                                packet.setData(buffer);
//                                server.send(packet);
//                                runFlag = false;
//                                stage = 0;
//                            }
//                            Thread.sleep(500);
//                            break;
//                        default:
//                            break;
//                    }
//                }
//            }
//        }
//        server.close();
//    }
//
//    public static void main(String[] argv){
//        SR_server server = new SR_server();
//        try {
//            server.start();
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//}
