import java.io.*;
import java.nio.charset.StandardCharsets;


public final class Frame {
    public static final int MAGIC = 0x43484154; 
    public static final byte VERSION = 1;
    public static final int HEADER_SIZE = 16;
    public static final int MAX_PAYLOAD_SIZE = 1_000_000; 

    public final byte type;
    public final short flags;
    public final int seq;
    public final byte[] payload;

    public Frame(byte type, short flags, int seq, byte[] payload) {
        this.type = type;
        this.flags = flags;
        this.seq = seq;
        this.payload = (payload == null) ? new byte[0] : payload;
    }

    public String payloadText() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    public static Frame ofText(byte type, int seq, String text) {
        byte[] p = (text == null) ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        return new Frame(type, (short) 0, seq, p);
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(type);
        out.writeShort(flags);
        out.writeInt(seq);
        out.writeInt(payload.length);
        if (payload.length > 0) out.write(payload);
        out.flush();
    }

    public static Frame readFrom(DataInputStream in) throws IOException {
        int magic = in.readInt();               
        if (magic != MAGIC) throw new IOException("Bad MAGIC: " + Integer.toHexString(magic));

        byte ver = in.readByte();
        if (ver != VERSION) throw new IOException("Bad VERSION: " + ver);

        byte type = in.readByte();
        short flags = in.readShort();
        int seq = in.readInt();
        int len = in.readInt();

        if (len < 0 || len > MAX_PAYLOAD_SIZE) {
            throw new IOException(String.format("Bad LEN: %d (max: %d)", len, MAX_PAYLOAD_SIZE));
        }

        byte[] payload = new byte[len];
        if (len > 0) in.readFully(payload);     
        return new Frame(type, flags, seq, payload);
    }
}
