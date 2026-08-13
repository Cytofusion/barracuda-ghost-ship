package com.barracudaghost;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class Base32768
{
    private static final int[][] ranges = {
            {0x05D0, 27}, {0x0627, 25}, {0x0641, 10}, {0x0679, 71}, {0x06C3, 16},
            {0x0712, 30}, {0x074D, 89}, {0x07CA, 33}, {0x0800, 22}, {0x0840, 25},
            {0x0860, 11}, {0x0870, 24}, {0x08A0, 41}, {0x0904, 37}, {0x0972, 15},
            {0x0985, 8}, {0x0993, 22}, {0x0A13, 22}, {0x0A85, 9}, {0x0A93, 22},
            {0x0B05, 5}, {0x0B13, 22}, {0x0BAE, 12}, {0x0C12, 23}, {0x0C2A, 16},
            {0x0C92, 23}, {0x0CAA, 10}, {0x0D04, 9}, {0x0D12, 41}, {0x0D85, 18},
            {0x0D9A, 24}, {0x0DB3, 9}, {0x0E01, 48}, {0x0E8C, 24}, {0x0EA7, 10},
            {0x0F5D, 12}, {0x1000, 38}, {0x1075, 13}, {0x1100, 329}, {0x1260, 41},
            {0x1290, 33}, {0x12C8, 15}, {0x12D8, 57}, {0x1318, 67}, {0x1380, 16},
            {0x1401, 620}, {0x166F, 17}, {0x1681, 26}, {0x16A0, 75}, {0x1700, 18},
            {0x171F, 19}, {0x1740, 18}, {0x1760, 13}, {0x1780, 52}, {0x1820, 35},
            {0x1844, 53}, {0x1887, 34}, {0x18B0, 70}, {0x1900, 31}, {0x1950, 30},
            {0x1980, 44}, {0x19B0, 26}, {0x1A00, 23}, {0x1A20, 53}, {0x1B13, 33},
            {0x1B83, 30}, {0x1BBA, 44}, {0x1C00, 36}, {0x1C5A, 30}, {0x2D30, 56},
            {0x2D80, 23}, {0x3041, 11}, {0x307E, 22}, {0x30A1, 11}, {0x30DE, 22},
            {0x3105, 43}, {0x31A0, 32}, {0x31F0, 16}, {0x3400, 6592}, {0x4E00, 21013},
            {0xA016, 1143}, {0xA4D0, 40}, {0xA500, 268}, {0xA610, 16}, {0xA6A0, 70},
            {0xA80C, 23}, {0xA840, 52}, {0xA882, 50}, {0xA90A, 28}, {0xA930, 23},
            {0xA960, 29}, {0xA984, 47}, {0xA9E7, 9}, {0xAA00, 41}, {0xAA60, 16},
            {0xAA7E, 50}, {0xAAE0, 11}, {0xABC0, 35}, {0xD7B0, 23}, {0xD7CB, 49},
    };

    private static final int alphabetSize = 32768;

    private static final char[] indexToChar = new char[alphabetSize];
    private static final int[] charToIndex = new int[65536];

    static
    {
        Arrays.fill(charToIndex, -1);

        int index = 0;

        for (int[] range : ranges)
        {
            int start = range[0];
            int amount = range[1];

            for (int i = 0; i < amount; i++)
            {
                char currentChar = (char) (start + i);

                indexToChar[index] = currentChar;
                charToIndex[currentChar] = index;

                index++;
            }
        }

        if (index != alphabetSize)
        {
            throw new RuntimeException("Wrong number of Base32768 characters");
        }
    }

    public static boolean looksLikeBase32768(String text)
    {
        if (text.isEmpty())
        {
            return false;
        }

        return text.charAt(0) >= 128;
    }

    public static String encode(byte[] data)
    {
        StringBuilder result = new StringBuilder();

        int buffer = 0;
        int bits = 0;

        for (byte currentByte : data)
        {
            buffer = (buffer << 8) | (currentByte & 255);
            bits += 8;

            while (bits >= 15)
            {
                bits -= 15;

                int value = (buffer >> bits) & 32767;
                result.append(indexToChar[value]);
            }
        }

        if (bits > 0)
        {
            int value = (buffer << (15 - bits)) & 32767;
            result.append(indexToChar[value]);
        }

        return result.toString();
    }

    public static byte[] decode(String text)
    {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        int buffer = 0;
        int bits = 0;

        for (int i = 0; i < text.length(); i++)
        {
            char currentChar = text.charAt(i);

            int value = -1;

            if (currentChar < charToIndex.length)
            {
                value = charToIndex[currentChar];
            }

            if (value == -1)
            {
                throw new IllegalArgumentException(
                        "Invalid Base32768 character at position " + i
                );
            }

            buffer = (buffer << 15) | value;
            bits += 15;

            while (bits >= 8)
            {
                bits -= 8;

                int currentByte = (buffer >> bits) & 255;
                result.write(currentByte);
            }
        }

        return result.toByteArray();
    }
}