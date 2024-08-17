package io.homeassistant.binding.danfoss.internal.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import  jakarta.xml.bind.DatatypeConverter;

public class Dominion {

    public static class Packet {
        /*
         * Packet header consists of 4 bytes:
         * - 1 byte : MsgClass
         * - 2 bytes: MsgCode
         * - 1 byte: Payload length, not including the header itself
         */
        public static final int HeaderSize = 4;

        private ByteBuffer m_Buffer;
        private int m_Start;

        // This constructor is used in order to parse incoming packets
        public Packet(byte[] data, int startOffset) {
            m_Buffer = ByteBuffer.wrap(data);
            m_Start = startOffset;
            m_Buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        // Constructors below are used for outgoing packets
        public Packet(byte msgClass, int msgCode, int payloadLength) {
            // Outgoing packet is always prefixed with one byte.
            // 0 means write data, 1 means request data.
            m_Buffer = ByteBuffer.allocate(1 + HeaderSize + payloadLength);
            m_Start = 1;
            m_Buffer.order(ByteOrder.LITTLE_ENDIAN);
            m_Buffer.put((byte) (payloadLength == 0 ? 1 : 0));
            m_Buffer.put(msgClass);
            m_Buffer.putShort((short) msgCode);
            m_Buffer.put((byte) payloadLength);
        }

        public Packet(int msgClass, int msgCode) {
            this((byte) msgClass, msgCode, 0);
        }

        public Packet(int msgClass, int msgCode, byte data) {
            this((byte) msgClass, msgCode, 1);
            m_Buffer.put(data);
        }

        public Packet(int msgClass, int msgCode, short data) {
            this((byte) msgClass, msgCode, 2);
            m_Buffer.putShort(data);
        }

        public Packet(int msgClass, int msgCode, int data) {
            this((byte) msgClass, msgCode, 4);
            m_Buffer.putInt(data);
        }

        public Packet(int msgClass, int msgCode, double data) {
            this(msgClass, msgCode, (short) (data * 100));
        }

        public Packet(int msgClass, int msgCode, boolean data) {
            this(msgClass, msgCode, (byte) (data ? 1 : 0));
        }

        public Packet(int msgClass, int msgCode, byte[] data) {
            this((byte) msgClass, msgCode, data.length + 1);
            putByte(data.length);
            m_Buffer.put(data);
        }

        public Packet(int msgClass, int msgCode, String data) {
            this(msgClass, msgCode, data.getBytes());
        }

        public void position(int offset) {
            m_Buffer.position(m_Start + HeaderSize + offset);
        }

        // Argument is int for convenience because all Java expressions return int
        public void putByte(int data) {
            m_Buffer.put((byte) data);
        }

        public void putDate(Date date) {
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            c.setTimeZone(TimeZone.getTimeZone("UTC"));

            int day = c.get(Calendar.DAY_OF_MONTH);
            int dow = c.get(Calendar.DAY_OF_WEEK) - 1;

            // Move SUNDAY to the end
            if (dow == 0) {
                dow = 7;
            }

            putByte(c.get(Calendar.SECOND));
            putByte(c.get(Calendar.MINUTE));
            putByte(c.get(Calendar.HOUR_OF_DAY));
            putByte(day | (dow << 5));
            putByte(c.get(Calendar.MONTH) + 1);
            putByte(c.get(Calendar.YEAR) - 2000);
        }

        public byte[] getBuffer() {
            return m_Buffer.array();
        }

        public int getMsgClass() {
            return Byte.toUnsignedInt(m_Buffer.get(m_Start));
        }

        public int getMsgCode() {
            return Short.toUnsignedInt(m_Buffer.getShort(m_Start + 1));
        }

        public int getPayloadLength() {
            return Byte.toUnsignedInt(m_Buffer.get(m_Start + 3));
        }

        public int getLength() {
            return getPayloadLength() + HeaderSize;
        }

        // Following methods return payload, interpreted as respective format
        public byte getByte() {
            return m_Buffer.get(m_Start + HeaderSize);
        }

        public short getShort() {
            return m_Buffer.getShort(m_Start + HeaderSize);
        }

        public int getInt() {
            return m_Buffer.getInt(m_Start + HeaderSize);
        }

        public boolean getBoolean() {
            return getByte() != 0;
        }

        public double getDecimal() {
            short fixed = getShort();

            // Decimal values are 16-bit fixed-point with two decimal places.
            // On Icon missing sensors report 0x8000
            return (fixed == 0x8000) ? Double.NaN : fixed / 100.0;
        }

        public byte[] getArray() {
            // Arrays and strings are prefixed with length byte
            int length = Byte.toUnsignedInt(getByte());
            byte[] data = new byte[length];

            position(1);
            m_Buffer.get(data, 0, length);
            return data;
        }

        public String getString() {
            byte[] data = getArray();

            // For some reason empty strings, which have never been set, have
            // full length and all zero contents. At least on Icon.
            return data[0] == 0 ? "" : new String(getArray());
        }

        public Date getDate(int offset) {
            position(offset);

            // Date is encoded as 6 bytes: year month day hour minute second in UTC
            // Months count starts from 1; year count begins from 2000
            byte sec = m_Buffer.get();
            byte min = m_Buffer.get();
            int hr = m_Buffer.get() & 63; // No idea why 63 here; DeviSmart app does this
            int d = m_Buffer.get() & 31; // Strip day of week
            int m = m_Buffer.get() - 1;
            int y = Byte.toUnsignedInt(m_Buffer.get()) + 2000; // Greetings year 2256! :)

            Calendar c = Calendar.getInstance();
            c.clear();
            c.setTimeZone(TimeZone.getTimeZone("UTC"));
            c.set(y, m, d, hr, min, sec);
            return c.getTime();
        }

        public Dominion.Version getVersion() {
            return new Dominion.Version(getShort());
        }

        public DeviSmart.WizardInfo getWizardInfo() {
            DeviSmart.WizardInfo ret = new DeviSmart.WizardInfo();

            // WizardInfo is written as an array, so its first byte is array length
            // and always equals to 6. Skip it.
            position(1);
            // Then go values.
            ret.sensorType = m_Buffer.get();
            ret.regulationType = m_Buffer.get();
            ret.flooringType = m_Buffer.get();
            ret.roomType = m_Buffer.get();
            ret.outputPower = m_Buffer.get() * DeviSmart.WizardInfo.POWER_SCALE;
            // 6'th value is always set to 1, we don't know what it is.

            return ret;
        }

        @Override
        public String toString() {
            int length = getPayloadLength();
            byte[] data = new byte[length];

            position(0);
            m_Buffer.get(data, 0, length);
            return String.format("%3d %5d %3d %s", getMsgClass(), getMsgCode(), length,
                    DatatypeConverter.printHexBinary(data));
        }
    }

    public static class Version {
        public int Major;
        public int Minor;

        // Version number is short, composed of (Major, Minor)
        Version(short num) {
            Minor = num & 0xFF;
            Major = Short.toUnsignedInt(num) >> 8;
        }

        @Override
        public String toString() {
            return String.valueOf(Major) + "." + String.valueOf(Minor);
        }
    }

    public static final String ProtocolName = "dominion-1.0";
}
