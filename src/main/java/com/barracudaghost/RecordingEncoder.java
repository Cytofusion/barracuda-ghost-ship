package com.barracudaghost;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RecordingEncoder
{
    private static final int Version = 5;
    private static final String CharSet = "UTF-8";

    public static class DecodedGhost
    {
        public final List<FrameDif> frames;
        public final List<Events> events;
        public final TrialData trialdata;

        public DecodedGhost(List<FrameDif> frames, List<Events> events, TrialData metadata)
        {
            this.frames = frames;
            this.events = events;
            this.trialdata = metadata;
        }
    }

    public static String encode(List<FrameDif> frames, List<Events> events, TrialData metadata) throws IOException
    {
        return Base32768.encode(compress(buildRawBytes(frames, events, metadata)));
    }

    public static BufferedImage encodeToImage(List<FrameDif> frames, List<Events> events, TrialData metadata) throws IOException
    {
        return packToImage(compress(buildRawBytes(frames, events, metadata)));
    }

    public static DecodedGhost decode(String data) throws IOException
    {
        byte[] compressed;

        if (Base32768.looksLikeBase32768(data))
        {
            compressed = Base32768.decode(data);
        }
        else
        {
            compressed = Base64.getUrlDecoder().decode(data);
        }

        return parseRaw(decompress(compressed));
    }

    public static DecodedGhost decodeFromImage(BufferedImage image) throws IOException
    {
        return parseRaw(decompress(unpackFromImage(image)));
    }

    private static byte[] buildRawBytes(List<FrameDif> frames, List<Events> events, TrialData metadata) throws IOException
    {
        if (frames.isEmpty())
        {
            throw new IllegalArgumentException("No frames to encode");
        }

        ByteArrayOutputStream raw = new ByteArrayOutputStream();

        raw.write(Version);

        writeVarInt(raw, metadata.trialType.ordinal());
        writeVarInt(raw, metadata.rank.ordinal());
        writeVarInt(raw, metadata.finalTimeSeconds);
        writeString(raw, metadata.username);

        writeVarInt(raw, frames.size());

        FrameDif first = frames.get(0);
        writeVarInt(raw, first.X);
        writeVarInt(raw, first.Y);
        writeVarInt(raw, first.O);

        int prevX = first.X;
        int prevY = first.Y;
        int prevOrientation = first.O;

        for (int i = 1; i < frames.size(); i++)
        {
            FrameDif frame = frames.get(i);
            int dx = frame.X - prevX;
            int dy = frame.Y - prevY;
            int dOrientation = frame.O - prevOrientation;

            writeZigZag(raw, dx);
            writeZigZag(raw, dy);
            writeZigZag(raw, dOrientation);
            writeVarInt(raw, frame.Gap);

            prevX = frame.X;
            prevY = frame.Y;
            prevOrientation = frame.O;
        }

        writeVarInt(raw, events.size());
        int prevEventTick = 0;
        for (Events event : events)
        {
            writeVarInt(raw, event.tick - prevEventTick);
            writeVarInt(raw, event.type.ordinal());
            prevEventTick = event.tick;
        }

        return raw.toByteArray();
    }

    private static byte[] compress(byte[] raw) throws IOException
    {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed))
        {
            gzip.write(raw);
        }
        return compressed.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws IOException
    {
        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
        {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1)
            {
                decompressed.write(buffer, 0, len);
            }
        }
        return decompressed.toByteArray();
    }

    private static DecodedGhost parseRaw(byte[] raw) throws IOException
    {
        VarIntReader reader = new VarIntReader(raw);

        int version = reader.readByte();

        TrialData.TrialType[] trialTypes = TrialData.TrialType.values();
        TrialData.Rank[] ranks = TrialData.Rank.values();

        int trialTypeOrdinal = reader.readVarInt();
        int rankOrdinal = reader.readVarInt();
        int finalTimeSeconds = reader.readVarInt();
        String username = reader.readString();

        TrialData.TrialType trialType = (trialTypeOrdinal >= 0 && trialTypeOrdinal < trialTypes.length)
                ? trialTypes[trialTypeOrdinal] : TrialData.TrialType.UNKNOWN;
        TrialData.Rank rank = (rankOrdinal >= 0 && rankOrdinal < ranks.length)
                ? ranks[rankOrdinal] : TrialData.Rank.UNRANKED;

        TrialData metadata = new TrialData(trialType, rank, finalTimeSeconds, username);

        int frameCount = reader.readVarInt();

        int x = reader.readVarInt();
        int y = reader.readVarInt();
        int orientation = reader.readVarInt();

        List<FrameDif> frames = new ArrayList<>(frameCount);
        frames.add(new FrameDif(x, y, orientation, 0));

        for (int i = 1; i < frameCount; i++)
        {
            x += reader.readZigZag();
            y += reader.readZigZag();
            orientation += reader.readZigZag();
            int tickGap = reader.readVarInt();
            frames.add(new FrameDif(x, y, orientation, tickGap));
        }

        List<Events> events = new ArrayList<>();
        if (!reader.isAtEnd())
        {
            int eventCount = reader.readVarInt();
            int tick = 0;
            Events.Type[] types = Events.Type.values();
            for (int i = 0; i < eventCount; i++)
            {
                tick += reader.readVarInt();
                int typeOrdinal = reader.readVarInt();
                if (typeOrdinal >= 0 && typeOrdinal < types.length)
                {
                    events.add(new Events(tick, types[typeOrdinal]));
                }
            }
        }

        return new DecodedGhost(frames, events, metadata);
    }

    private static BufferedImage packToImage(byte[] data)
    {
        int numPixels = (data.length + 2) / 3;
        int width = (int) Math.ceil(Math.sqrt(numPixels));
        int height = (int) Math.ceil((double) numPixels / width);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int i = 0;
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int r = i < data.length ? (data[i] & 0xFF) : 0;
                int g = (i + 1) < data.length ? (data[i + 1] & 0xFF) : 0;
                int b = (i + 2) < data.length ? (data[i + 2] & 0xFF) : 0;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
                i += 3;
            }
        }
        return image;
    }

    private static byte[] unpackFromImage(BufferedImage image)
    {
        int width = image.getWidth();
        int height = image.getHeight();
        ByteArrayOutputStream out = new ByteArrayOutputStream(width * height * 3);
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int rgb = image.getRGB(x, y);
                out.write((rgb >> 16) & 0xFF);
                out.write((rgb >> 8) & 0xFF);
                out.write(rgb & 0xFF);
            }
        }
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value)
    {
        while ((value & ~0x7F) != 0)
        {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeZigZag(ByteArrayOutputStream out, int value)
    {
        writeVarInt(out, (value << 1) ^ (value >> 31));
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException
    {
        byte[] bytes;
        try
        {
            bytes = (value == null ? "" : value).getBytes(CharSet);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IOException(e);
        }
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static class VarIntReader
    {
        private final byte[] data;
        private int pos = 0;

        VarIntReader(byte[] data)
        {
            this.data = data;
        }

        boolean isAtEnd()
        {
            return pos >= data.length;
        }

        int readByte()
        {
            return data[pos++] & 0xFF;
        }

        int readVarInt()
        {
            int result = 0;
            int shift = 0;
            while (true)
            {
                int b = data[pos++] & 0xFF;
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0)
                {
                    break;
                }
                shift += 7;
            }
            return result;
        }

        int readZigZag()
        {
            int value = readVarInt();
            return (value >>> 1) ^ -(value & 1);
        }

        String readString() throws IOException
        {
            int length = readVarInt();
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++)
            {
                bytes[i] = (byte) readByte();
            }
            try
            {
                return new String(bytes, CharSet);
            }
            catch (UnsupportedEncodingException e)
            {
                throw new IOException(e);
            }
        }
    }
}