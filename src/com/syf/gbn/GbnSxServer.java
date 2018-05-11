package com.syf.gbn;


import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/*
    双向传输到底是用一个socket按次序传还是加一条反向传输的命令？
    感觉有点问题
 */

public class GbnSxServer {
    final static int PORT = 10086;
    final static int BUFFER_LENGTH = 1026; // seq + 1024bytes + EOF0

    final static int SEND_WINDOW_SIZE = 10;  // 发送窗口大小
    final static int SEQ_SIZE = 20;     // 序列号个数
    boolean[] ack = new boolean[SEQ_SIZE];

    int curSeq;
    int curAck;
    int totalSeq;
    int totalPacket;

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

    // 超时重传
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

    /*  收到一个ack后的标记动作
        ack[] = [0,1,2,3,4,5,....,curAck,...,18,19]
        如果ack大于curAck，那么curAck ~ ack之间标为true
        ack超过最大值，回到了curAck的左边
        那么curAck ~ 19, 0 ~ ack都要标注为true
    */
    // 如果ack绕了一圈又到了curAck的右边，会有这种情况吗？应该不会吧，毕竟窗口大小限制了发送数量
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

    byte[] readIn() throws IOException{
        byte[] content = new byte[65536];
        File file = new File("C:\\Users\\86234\\workspace\\Network_experiment\\src\\com\\syf\\gbn\\file.txt");
        DataInputStream br = new DataInputStream(new FileInputStream(file));
        int size = br.read(content,0, 65535);
        byte[] content2 = new byte[size];
        System.arraycopy(content,0,content2,0, size);
        return content2;
    }

    byte[] getCurTime(){
        Date day=new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(day).getBytes();
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
        DatagramSocket server = new DatagramSocket(PORT);
        byte[] buffer = new byte[BUFFER_LENGTH];
        DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);

        byte[] content = readIn();
        totalPacket = (int)Math.ceil((double)content.length/1024);
        for(int i=0; i<SEQ_SIZE; i++)
            ack[i] = true;
        int recvSize = 0;

        double packetLossRatio = 0.2;
        double ackLossRatio = 0.2;

        while(true){
            System.out.println("start....");
            server.setSoTimeout(0);
            server.receive(packet);
            String str = new String(packet.getData(),"UTF-8");
            System.out.println(str);
            if("-time".equals(str.substring(0,5))){
                System.out.println("recv from client: -time");
                byte[] time = getCurTime();
                buffer = new byte[BUFFER_LENGTH];
                System.arraycopy(time, 0, buffer, 0, time.length);
                packet.setData(buffer);
                server.send(packet);
            }else if("-quit".equals(str.substring(0,5))){
                buffer = new byte[BUFFER_LENGTH];
                byte[] bye = "Good bye!".getBytes();
                System.arraycopy(bye, 0, buffer, 0, bye.length);
                packet.setData(buffer);
                server.send(packet);
                break;
            }else if("-testgbn".equals(str.substring(0,8))){
                // 进入gbn测试阶段
                // 首先server（0状态）向client发送205状态码, server进入1状态
                // server等待client回复200状态码，如果收到了server进入2状态，开始传输文件，否则等待超时
                int waitCount = 0;
                System.out.println("Begain to test GBN_client protocol,please don't abort the process.");
                System.out.println("Shake hands stage");
                int stage = 0;
                boolean runFlag = true;
                Loop:    while(runFlag){
                    switch (stage){
                        case 0: // 发送205状态码
                            buffer[0] = (byte)205;
                            packet.setData(buffer);
                            server.send(packet);
                            Thread.sleep(100);
                            stage = 1;
                            break;
                        case 1: // 等待200状态码
                            server.setSoTimeout(250);
                            while(true) {
                                try {
                                    server.receive(packet);
                                    break;
                                } catch (InterruptedIOException e) {
                                    waitCount++;
                                }finally {
                                    if(waitCount>=20)
                                        break;
                                }
                            }
                            if(waitCount >= 20){
                                System.out.println("shake hands time out error");
                                runFlag = false;
                                waitCount = 0;
                                break;
                            }
                            if (((int)buffer[0]+256)%256 == 200) {
                                System.out.println("Begin a file transfer");
                                System.out.println("File size is " + content.length + "B, each packet is 1024B and packet "
                                        + "total number is " + totalPacket);
                                curAck = curSeq = totalSeq = waitCount = 0;
                                stage = 2;
                            }
                            break;
                        case 2: // 传输阶段
                            if(seqIsAvailable()){
                                System.out.println("开始传输第" + (curSeq+1) + "个");
                                buffer[0] = (byte)(curSeq);
                                ack[curSeq] = false;
                                int left_size = content.length - totalSeq*1024;
                                if(left_size > 0 && left_size < 1024) {
                                    System.arraycopy(content, totalSeq * 1024, buffer, 1, left_size);
                                    for(int i=left_size+2; i<BUFFER_LENGTH; i++)
                                        buffer[i] = ' ';
                                } else if(left_size > 0) {
                                    System.arraycopy(content, totalSeq * 1024, buffer, 1, 1024);
                                    left_size = 1024;
                                }
                                //System.out.println(new String(buffer,1,left_size, "UTF-8"));
                                packet.setData(buffer);
                                server.send(packet);
                                System.out.println("send to：" + packet.getAddress() + ", size: " + packet.getLength()) ;
                                curSeq = (curSeq + 1) % SEQ_SIZE;
                                totalSeq ++;
                                Thread.sleep(500);
                            }
                            server.setSoTimeout(250);
                            try {
                                server.receive(packet);
                            } catch (InterruptedIOException e) {
                                waitCount++;
                                if(waitCount>=20){
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
                            if (totalSeq >= totalPacket) {
                                System.out.println("传输完成");
                                System.out.println("开始接收");
                                System.arraycopy("done".getBytes(), 0, buffer, 0, "done".getBytes().length);
                                packet.setData(buffer);
                                server.send(packet);
                                runFlag = true;
                                stage = 3;
                                continue Loop;
                            }
                            Thread.sleep(500);
                            break;
                        default:
                            break;
                    }
                }
            }else if("-sendData".equals(str.substring(0,9))){
                int waitCount = 0;
                int stage = 0;
                boolean runFlag = true;
                buffer[0] = (byte)66;   // 表示准备接收数据
                packet.setData(buffer);
                server.send(packet);
                int code;
                int seq;
                int waitSeq = 0;
                while(true){
                    server.receive(packet);
                    buffer = packet.getData();
                    str = new String(buffer,"UTF-8");
                    str = str.trim();
                    if("done".equals(str.substring(0,4))) {
                        System.out.println("传输结束！");
                        break;
                    }
                    switch (stage){
                        case 0: // 确认阶段
                            code = ((int)buffer[0] + 256) % 256;
                            if(code == 88){
                                System.out.println("Ready for file transmission.");
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
                                waitSeq++;
                                waitSeq %= SEQ_SIZE;
                                //System.out.println(new String(buffer, 1, packet.getLength() - 2, "UTF-8"));
                                //System.out.println(buffer.length);
                                //System.arraycopy(buffer,1, content, seq*1024, packet.getLength()-2);
                                //System.out.println(new String(buffer, 1, packet.getLength()-2, "UTF-8"));
                                //totalsize += packet.getLength()-2;
                                buffer[0] = (byte) (waitSeq);
                                buffer[1] = ' ';

                                if (lossInLossRatio(ackLossRatio)) {
                                    System.out.println("The ack of " + waitSeq + " loss.");
                                    continue;
                                }
                                packet.setData(buffer);
                                server.send(packet);
                                System.out.println("send a ack of " + waitSeq);
                            }
                            break;
                        default:
                            break;
                    }
                    Thread.sleep(500);
                }
            }
        }
        server.close();
    }

    public static void main(String[] argv){
        GbnSxServer client = new GbnSxServer();
        try {
            client.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
