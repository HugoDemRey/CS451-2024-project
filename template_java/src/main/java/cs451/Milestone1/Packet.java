package cs451.Milestone1;

import static cs451.Constants.PACKET_TYPE_ACK;
import static cs451.Constants.PACKET_TYPE_CONTENT;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import cs451.Host;

public class Packet {
    private final int seqNum;
    private final Message[] messages;
    private final int nbMessages;
    private final Host receiver;

    public Packet(int seqNum, Message[] messages, int nbMessages, Host receiver) {
        this.seqNum = seqNum;
        this.messages = messages;
        this.nbMessages = nbMessages;
        this.receiver = receiver;
    }

    public int seqNum() {
        return seqNum;
    }

    public Message[] messages() {
        return messages;
    }

    public int nbMessages() {
        return nbMessages;
    }

    public Host receiver() {
        return receiver;
    }

    public static ByteBuffer serialize(Packet packet, int sentCount, int senderId){

        byte[][] contentBytes = new byte[packet.nbMessages()][];
        int[] contentSizeBytes = new int[packet.nbMessages()];
        int contentTotalSize = Character.BYTES + 4 * Integer.BYTES;
        for (int i = 0; i < packet.nbMessages(); i++) {
            contentBytes[i] = packet.messages()[i].getContent().getBytes(StandardCharsets.UTF_8);
            contentSizeBytes[i] = contentBytes[i].length;

            contentTotalSize += 2 * Integer.BYTES + contentSizeBytes[i];
        }

        /*
        * Physical Packet Format : 
        * [packetType (2 Byte)] 'M' 
        * [seqNum (4 Bytes)] 
        * [sentCount (4 Bytes)] 
        * [nbMessages (4 Bytes)] 
        * [contentSize1 (4 Bytes)] 
        * [content1 (contentSize1 Bytes)] 
        * [signature1 (4 Bytes)] 
        * [contentSize 2 (4 Bytes)]
        * [content2 (contentSize2 Bytes)]
        * [signature2 (4 Bytes)]
        * ...
        * [senderId (4 Bytes)] 
        */

        // Allocation of the byte buffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(contentTotalSize);

        // The sequence number of the packet (used for acks) is the sequence number of the first message
        byteBuffer.putChar(PACKET_TYPE_CONTENT);
        byteBuffer.putInt(packet.seqNum());
        byteBuffer.putInt(sentCount);
        byteBuffer.putInt(packet.nbMessages());
        for (int i = 0; i < packet.nbMessages(); i++) {
            byteBuffer.putInt(contentSizeBytes[i]);
            byteBuffer.put(contentBytes[i]);
            byteBuffer.putInt(packet.messages()[i].getSignature());
        }
        byteBuffer.putInt(senderId);
        return byteBuffer;

    } 

    @Override
    public String toString() {
        return "Packet{" +
                "messages=" + messages +
                ", nbMessages=" + nbMessages +
                ", receiver=" + receiver +
                '}';
    }
}
