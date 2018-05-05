//package com.syf.gbn;
//
//import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
//import sun.misc.Cleaner;
//
//import java.io.*;
//import java.net.*;
//import java.nio.ByteBuffer;
//import java.nio.channels.DatagramChannel;
//import java.text.SimpleDateFormat;
//import java.util.Calendar;
//import java.util.Date;
//
//public class Test_nio {
//    final static int PORT = 10086;
//    final static int BUFFER_LENGTH = 1026; // seq + 1024bytes + EOF0
//
//    final static int SEND_WINDOW_SIZE = 10;  // 发送窗口大小
//    final static int SEQ_SIZE = 20;     // 序列号个数
//    boolean[] ack = new boolean[SEQ_SIZE];
//
//    int curSeq;
//    int curAck;
//    int totalSeq;
//    int totalPacket;
//
//    // 查询当前序列号是否可用。
//    boolean seqIsAvailable(){
//        int step;
//        step = curSeq - curAck;
//        step = step >= 0 ? step: step + SEQ_SIZE;
//        if(step >= SEND_WINDOW_SIZE)
//            return false;
//        if(ack[curSeq])
//            return true;
//        return false;
//    }
//
//    // 超时重传
//    void timeoutHandler(){
//        System.out.println("Timeout error!");
//        int index;
//        for(int i=0; i<SEND_WINDOW_SIZE; i++){
//            index = (i + curAck) % SEQ_SIZE;
//            ack[index] = true;
//        }
//        totalSeq -= SEND_WINDOW_SIZE;
//        curSeq = curAck;
//    }
//
//    /*  收到一个ack后的标记动作
//        ack[] = [0,1,2,3,4,5,....,curAck,...,18,19]
//        如果ack大于curAck，那么curAck ~ ack之间标为true
//        ack超过最大值，回到了curAck的左边
//        那么curAck ~ 19, 0 ~ ack都要标注为true
//    */
//    // 如果ack绕了一圈又到了curAck的右边，会有这种情况吗？应该不会吧，毕竟窗口大小限制了发送数量
//    void ackHandler(int seq){
//        seq--;
//        System.out.println("Recv a ack of " + seq);
//        if(curAck <= seq){
//            for(int i=curAck; i<=seq; i++){
//                ack[i] = true;
//            }
//            curAck = (seq + 1) % SEQ_SIZE;
//        }else{
//            for(int i=curAck; i<SEQ_SIZE; i++){
//                ack[i] = true;
//            }
//            for(int i=0; i<=seq; i++){
//                ack[i] = true;
//            }
//            curAck = seq + 1;
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
//    void start() throws IOException, InterruptedException {
//        DatagramChannel channel = DatagramChannel.open();
//        channel.socket().bind(new InetSocketAddress(PORT));
//        channel.connect(new InetSocketAddress("127.0.0.1",10010));
//        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_LENGTH);
//
//        byte[] content = readIn();
//        totalPacket = content.length / 1024;
//        for(int i=0; i<SEQ_SIZE; i++){
//            ack[i] = true;
//        }
//        DatagramPacket packet = new DatagramPacket(buffer.array(), BUFFER_LENGTH);
//        int recvSize = 0;
//        while(true){
//            System.out.println("start....");
//            buffer.clear();
//            recvSize = channel.read(buffer);
//            if(recvSize<=0){
//                Thread.sleep(200);
//                continue;
//            }
//            buffer.flip();
////            byte[] tt = new byte[buffer.limit()];
////            buffer.get(tt, 0, buffer.limit());
//            String str = new String(buffer.array(),"UTF-8");
//            if("-time".equals(str.substring(0,5))){
//                System.out.println("recv from client: -time");
//                buffer.clear();
//                buffer.put(getCurTime(), 0, getCurTime().length);
//                buffer.flip();
//                channel.write(buffer);
//            }else if("-quit".equals(str.substring(0,5))){
//                buffer.clear();
//                buffer.put("Good bye!".getBytes());
//                buffer.flip();
//                channel.write(buffer);
//                break;
//            }else if("-testgbn".equals(str.substring(0,8))){
//                // 进入gbn测试阶段
//                // 首先server（0状态）向client发送205状态码, server进入1状态
//                // server等待client回复200状态码，如果收到了server进入2状态，开始传输文件，否则等待超时
//                int waitCount = 0;
//                System.out.println("Begain to test GBN_client protocol,please don't abort the process.");
//                System.out.println("Shake hands stage");
//                int stage = 0;
//                boolean runFlag = true;
//                while(runFlag){
//                    switch (stage){
//                        case 0: // 发送205状态码
//                            buffer.clear();
//                            buffer.put((byte)205);
//                            buffer.flip();
//                            channel.write(buffer);
//                            Thread.sleep(100);
//                            stage = 1;
//                            break;
//                        case 1: // 等待200状态码
//                            buffer.clear();
//                            recvSize = channel.read(buffer);
//                            buffer.flip();
//                            if(recvSize <= 0){
//                                System.out.println(waitCount);
//                                waitCount++;
//                                if(waitCount > 20){
//                                    runFlag = false;
//                                    System.out.println("Timeout error");
//                                    break;
//                                }
//                                Thread.sleep(500);
//                                continue;
//                            }else {
//                                if (((int)buffer.get(0)+256)%256 == 200) {
//                                    System.out.println("Begin a file transfer");
//                                    System.out.println("File size is " + content.length + "B, each packet is 1024B and packet "
//                                            + "total number is " + totalPacket);
//                                    curAck = curSeq = totalSeq = waitCount = 0;
//                                    stage = 2;
//                                }
//                            }
//                            break;
//                        case 2: // 传输阶段
//                            if(seqIsAvailable()){
//                                if(totalSeq >= totalPacket)
//                                    break;
//                                System.out.println("开始传输第" + curSeq + "个");
//                                byte[] bytes = new byte[BUFFER_LENGTH];
//                                bytes[0] = (byte)(curSeq + 1);
//                                ack[curSeq] = false;
//                                int left_size = content.length - totalSeq*1024;
//                                System.out.println(content.length);
//                                System.out.println(left_size);
//                                if(left_size < 1024)
//                                    System.arraycopy(content, totalSeq*1024, bytes, 1, left_size);
//                                else
//                                    System.arraycopy(content, totalSeq*1024, bytes, 1, 1024);
//                                buffer.clear();
//                                buffer.put(bytes);
//                                buffer.flip();
//                                channel.write(buffer);
//                                System.out.println("flag");
//                                curSeq = (curSeq + 1) % SEQ_SIZE;
//                                totalSeq ++;
//                                Thread.sleep(500);
//                            }
//                            buffer.clear();
//                            recvSize = channel.read(buffer);
//                            buffer.flip();
//                            if(recvSize <= 0){
//                                waitCount++;
//                                if(waitCount > 20){
//                                    timeoutHandler();
//                                    waitCount = 0;
//                                }
//                            }else{
//                                System.out.println("收到ack");
//                                ackHandler((int)buffer.get(0));
//                                waitCount = 0;
//                            }
//                            Thread.sleep(500);
//                            break;
//
//                        default:
//                            break;
//                    }
//                }
//            }
//        }
//        channel.close();
//    }
//
//    public static void main(String[] argv){
//        GBN_server server = new GBN_server();
//        try {
//            server.start();
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//}
